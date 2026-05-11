package com.kether.pixellife.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kether.pixellife.backend.engine.SimulationEngine;
import com.kether.pixellife.backend.model.Entity;
import com.kether.pixellife.backend.model.Nutrient;
import com.kether.pixellife.backend.model.Organism;
import com.kether.pixellife.backend.model.Plant;
import com.kether.pixellife.backend.persistence.entity.SimulationRecord;
import com.kether.pixellife.backend.persistence.repository.SimulationRepository;
import com.kether.pixellife.common.dto.SimulationDtos.*;
import com.kether.pixellife.common.event.SimulationEvent;
import com.kether.pixellife.common.model.SimulationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service principal de gestion des simulations — v3.
 *
 * Migration :
 *  - startSimulation() écrit plantCount/nutrientCount/organismCount/mutationRate
 *    dans SimulationRecord (plus de ratios float)
 *  - toSummary() reconstruit SimulationConfig depuis les int
 *  - getCurrentSnapshot() expose depth + dna[] + z dans le GridSnapshot
 *  - trackPeakOrganisms() sauvegarde toutes les 500 steps (était 100)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationService {

    private final SimulationRepository simulationRepository;
    private final ObjectMapper objectMapper;

    @Value("${pixellife.simulation.tick-delay-ms:100}")
    private long defaultTickDelayMs;

    private final Map<Long, SimulationEngine> activeEngines     = new ConcurrentHashMap<>();
    private final Map<Long, Integer>          peakOrganismCounts = new ConcurrentHashMap<>();

    // ─── Démarrage ────────────────────────────────────────────────────────────

    @Transactional
    public SimulationStartResponse startSimulation(SimulationConfig config) {
        SimulationRecord record = SimulationRecord.builder()
                .gridWidth    (config.width())
                .gridHeight   (config.height())
                // ← int, pas float
                .plantCount   (config.plantCount())
                .nutrientCount(config.nutrientCount())
                .organismCount(config.organismCount())
                .mutationRate (config.mutationRate())
                .maxSteps     (config.maxSteps())
                .seed         (config.seed())
                .totalSteps   (0)
                .status       (SimulationRecord.SimulationStatus.RUNNING)
                .startedAt    (Instant.now())
                .build();

        record = simulationRepository.save(record);
        final long simId = record.getId();

        SimulationEngine engine = new SimulationEngine(simId, config);
        engine.initialize();

        saveInitialSnapshot(record, engine);
        engine.addEventListener(event -> handleSimulationEvent(simId, event));
        activeEngines.put(simId, engine);

        Thread.ofVirtual()
                .name("sim-" + simId)
                .start(() -> engine.run(defaultTickDelayMs));

        log.info("Simulation #{} lancée ({}×{} plants={} orgs={} infini={})",
                simId, config.width(), config.height(),
                config.plantCount(), config.organismCount(), config.isInfinite());

        return new SimulationStartResponse(simId, "Simulation #%d démarrée".formatted(simId));
    }

    // ─── Contrôle ─────────────────────────────────────────────────────────────

    public boolean pauseSimulation(long simId) {
        SimulationEngine engine = activeEngines.get(simId);
        if (engine == null) return false;
        engine.pause();
        updateStatus(simId, SimulationRecord.SimulationStatus.PAUSED);
        return true;
    }

    public boolean resumeSimulation(long simId) {
        SimulationEngine engine = activeEngines.get(simId);
        if (engine == null) return false;
        engine.resume();
        updateStatus(simId, SimulationRecord.SimulationStatus.RUNNING);
        return true;
    }

    public boolean stopSimulation(long simId) {
        SimulationEngine engine = activeEngines.get(simId);
        if (engine == null) return false;
        engine.stop();
        return true;
    }

    public boolean setTickDelay(long simId, long delayMs) {
        SimulationEngine engine = activeEngines.get(simId);
        if (engine == null) return false;
        engine.setTickDelayMs(delayMs);
        return true;
    }

    // ─── Lecture ──────────────────────────────────────────────────────────────

    public Optional<GridSnapshot> getCurrentSnapshot(long simId) {
        SimulationEngine engine = activeEngines.get(simId);
        if (engine == null) return Optional.empty();

        List<EntitySnapshot> snapshots = engine.getSnapshot().stream()
                .filter(e -> !e.isDead())
                .map(this::toEntitySnapshot)
                .toList();

        return Optional.of(new GridSnapshot(
                simId,
                engine.getCurrentStepValue(),
                engine.getConfig().width(),
                engine.getConfig().height(),
                engine.getDepth(),          // ← depth 3D
                snapshots
        ));
    }

    public List<SimulationSummary> listSimulations() {
        return simulationRepository.findAllOrderByStartedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    public Optional<SimulationSummary> getSimulation(long simId) {
        return simulationRepository.findById(simId).map(this::toSummary);
    }

    // ─── Événements moteur ────────────────────────────────────────────────────

    private void handleSimulationEvent(long simId, SimulationEvent event) {
        switch (event) {
            case SimulationEvent.SimulationEnded ended -> finalizeSimulation(simId, ended);
            case SimulationEvent.StepCompleted   step  -> trackPeakOrganisms(simId, step);
            default -> {}
        }
    }

    private void finalizeSimulation(long simId, SimulationEvent.SimulationEnded ended) {
        SimulationEngine engine = activeEngines.get(simId);

        simulationRepository.findById(simId).ifPresent(record -> {
            record.setStatus(SimulationRecord.SimulationStatus.FINISHED);
            record.setFinishedAt(ended.timestamp());
            record.setTotalSteps(ended.totalSteps());
            record.setEndReason(ended.reason());

            if (engine != null) {
                List<Entity> finals = engine.getSnapshot();
                record.setFinalOrganismCount((int) finals.stream()
                        .filter(e -> e instanceof Organism && !e.isDead()).count());
                record.setFinalPlantCount((int) finals.stream()
                        .filter(e -> e instanceof Plant    && !e.isDead()).count());
                record.setFinalNutrientCount((int) finals.stream()
                        .filter(e -> e instanceof Nutrient && !e.isDead()).count());

                try {
                    record.setFinalGridSnapshot(objectMapper.writeValueAsString(
                            finals.stream().filter(e -> !e.isDead())
                                    .map(this::toEntitySnapshot).toList()));
                } catch (Exception ex) {
                    log.warn("Impossible de sérialiser le snapshot final : {}", ex.getMessage());
                }
            }

            simulationRepository.save(record);
            activeEngines.remove(simId);
            peakOrganismCounts.remove(simId);
            log.info("Simulation #{} finalisée (raison: {})", simId, ended.reason());
        });
    }

    private void trackPeakOrganisms(long simId, SimulationEvent.StepCompleted step) {
        peakOrganismCounts.merge(simId, step.organismCount(), Math::max);

        // Sauvegarde toutes les 500 steps (réduit la pression sur SQLite)
        if (step.step() % 500 == 0) {
            simulationRepository.findById(simId).ifPresent(record -> {
                record.setPeakOrganismCount(peakOrganismCounts.getOrDefault(simId, 0));
                simulationRepository.save(record);
            });
        }
    }

    // ─── Helpers privés ───────────────────────────────────────────────────────

    private void saveInitialSnapshot(SimulationRecord record, SimulationEngine engine) {
        try {
            record.setInitialGridSnapshot(objectMapper.writeValueAsString(
                    engine.getSnapshot().stream().map(this::toEntitySnapshot).toList()));
            simulationRepository.save(record);
        } catch (Exception ex) {
            log.warn("Impossible de sauvegarder le snapshot initial : {}", ex.getMessage());
        }
    }

    @Transactional
    protected void updateStatus(long simId, SimulationRecord.SimulationStatus status) {
        simulationRepository.findById(simId).ifPresent(record -> {
            record.setStatus(status);
            simulationRepository.save(record);
        });
    }

    /**
     * Convertit une entité moteur en DTO de snapshot pour le renderer.
     * Inclut z (position 3D), dna[] (génome) et renderRadius.
     */
    private EntitySnapshot toEntitySnapshot(Entity entity) {
        String  gender       = null;
        float[] dna          = null;
        float   renderRadius = 0.32f;
        float   renderHeight = 0f;

        // Position continue par défaut : centre de la cellule
        float fx = entity.x();
        float fy = entity.y();
        float fz = entity.z();

        if (entity instanceof Organism o) {
            gender       = o.getGender().name();
            dna          = o.getDna().toArray();
            renderRadius = o.getDna().size();
            // Position continue pour animation fluide des organismes
            fx = o.getFloatX();
            fy = o.getFloatY();
            fz = entity.getPosition().z() + 0.5f;

        } else if (entity instanceof Plant p) {
            renderRadius = p.getRenderRadius();
            renderHeight = p.getRenderHeight();    // hauteur normalisée [0..1]

        } else if (entity instanceof Nutrient n) {
            renderRadius = 0.20f;
            // Position flottante continue pour animation fluide
            float[] fp = n.getFloatPosition();
            fx = fp[0] + 0.5f;
            fy = fp[1] + 0.5f;
            fz = fp[2];
        }

        return new EntitySnapshot(
                entity.getId(),
                entity.typeName(),
                entity.getPosition().gridX(),
                entity.getPosition().gridY(),
                entity.getPosition().gridZ(),
                fx, fy, fz,               // position continue
                entity.getEnergy(),
                gender,
                dna,
                renderRadius,
                renderHeight
        );
    }



    /**
     * Reconstruit un SimulationConfig depuis un SimulationRecord.
     * Utilise les int (plantCount, etc.) — plus de ratios float.
     */
    private SimulationSummary toSummary(SimulationRecord r) {
        SimulationConfig config = new SimulationConfig(
                r.getGridWidth(),
                r.getGridHeight(),
                r.getPlantCount(),       // ← int
                r.getNutrientCount(),    // ← int
                r.getOrganismCount(),    // ← int
                r.getMaxSteps(),
                r.getSeed(),
                r.getMutationRate()
        );
        SimulationStatus status = switch (r.getStatus()) {
            case RUNNING  -> SimulationStatus.RUNNING;
            case PAUSED   -> SimulationStatus.PAUSED;
            case FINISHED -> SimulationStatus.FINISHED;
            case ERROR    -> SimulationStatus.ERROR;
        };
        return new SimulationSummary(
                r.getId(), config, r.getStartedAt(), r.getFinishedAt(),
                r.getTotalSteps(), status);
    }
}