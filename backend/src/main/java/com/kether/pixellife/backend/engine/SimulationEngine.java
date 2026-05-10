package com.kether.pixellife.backend.engine;

import com.kether.pixellife.backend.model.*;
import com.kether.pixellife.common.event.SimulationEvent;
import com.kether.pixellife.common.model.Position;
import com.kether.pixellife.common.model.SimulationConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Moteur principal de simulation — v4.
 *
 * Nouveauté principale : intégration de {@link EcosystemRegulator}.
 * Le régulateur observe les populations toutes les 50 steps et intervient
 * si une espèce est en danger d'extinction. Les paramètres appris sont
 * persistés entre sessions dans ~/.pixellife/ecosystem_params.json.
 *
 * Le contexte de tick expose maintenant le régulateur pour que les
 * entités puissent récupérer les bonus adaptatifs (photosynthèse, etc.).
 */
@Slf4j
@Getter
public class SimulationEngine {

    private static final int DEFAULT_DEPTH = 16;

    private final long             simulationId;
    private final SimulationConfig config;
    private final Grid             grid;
    private final int              depth;
    private EcosystemRegulator     regulator; // initialisé après populateGrid()

    private volatile boolean running    = false;
    private volatile boolean paused     = false;
    private volatile long    tickDelayMs;

    private final AtomicLong currentStep = new AtomicLong(0);
    private final List<Consumer<SimulationEvent>> eventListeners = new CopyOnWriteArrayList<>();

    private static final RandomGenerator RNG = RandomGeneratorFactory.getDefault().create();

    public SimulationEngine(long simulationId, SimulationConfig config) {
        this.simulationId = simulationId;
        this.config       = config;
        this.grid         = new Grid(config.width(), config.height());
        this.depth        = DEFAULT_DEPTH;
    }

    // ─── Cycle de vie ─────────────────────────────────────────────────────────

    public void initialize() {
        log.info("Initialisation simulation #{} ({}x{} depth={})",
                simulationId, config.width(), config.height(), depth);
        populateGrid();

        // Créer le régulateur APRÈS populateGrid pour avoir les vrais comptages initiaux
        long plants    = grid.getAllEntities().stream().filter(e -> e instanceof Plant).count();
        long organisms = grid.getAllEntities().stream().filter(e -> e instanceof Organism).count();
        long nutrients = grid.getAllEntities().stream().filter(e -> e instanceof Nutrient).count();
        regulator = new EcosystemRegulator(grid, (int)plants, (int)organisms, (int)nutrients, depth);

        log.info("Grille initialisée : {} entités (P={} O={} N={})",
                grid.entityCount(), plants, organisms, nutrients);
    }

    public void run(long initialTickDelayMs) {
        this.tickDelayMs = initialTickDelayMs;
        running = true;
        log.info("Simulation #{} démarrée (delay={}ms, infinie={})",
                simulationId, tickDelayMs, config.isInfinite());

        while (running) {
            if (!config.isInfinite() && currentStep.get() >= config.maxSteps()) break;
            if (paused) { Thread.onSpinWait(); continue; }

            tick();

            // Sans régulateur, on arrêterait ici sur extinction.
            // Avec régulateur, on continue — l'extinction est gérée en amont.
            // On arrête seulement si les organismes ET les plantes sont à 0 depuis longtemps.
            boolean fullyExtinct = grid.getAllEntities().stream()
                    .noneMatch(e -> (e instanceof Organism || e instanceof Plant) && !e.isDead());
            if (fullyExtinct) {
                log.info("Simulation #{} : extinction totale irréversible à l'étape {}",
                        simulationId, currentStep.get());
                break;
            }

            long delay = tickDelayMs;
            if (delay > 0) {
                try { Thread.sleep(delay); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }

        running = false;
        // Sauvegarde finale des paramètres appris
        if (regulator != null) regulator.saveParams();

        String reason = !config.isInfinite() && currentStep.get() >= config.maxSteps()
                ? "max_steps_reached" : "extinction";
        publishEvent(new SimulationEvent.SimulationEnded(
                simulationId, currentStep.get(), reason, Instant.now()));
        log.info("Simulation #{} terminée après {} steps", simulationId, currentStep.get());
    }

    public void stop()   { running = false; }
    public void pause()  { paused  = true;  }
    public void resume() { paused  = false; }
    public void setTickDelayMs(long d) { this.tickDelayMs = Math.max(0, d); }

    // ─── Tick ─────────────────────────────────────────────────────────────────

    public void tick() {
        long step = currentStep.incrementAndGet();

        GridSimulationContext context = new GridSimulationContext(
                grid, step, this::publishEvent, config.mutationRate(), depth, regulator);

        List<Entity> snapshot = grid.snapshot();
        for (Entity entity : snapshot) {
            if (!entity.isDead()) entity.update(context);
        }

        cleanupDead(context);

        // ── Régulation homéostatique ─────────────────────────────────────────
        if (regulator != null) regulator.regulate(step, context);

        context.flush();

        long organisms = snapshot.stream().filter(e -> e instanceof Organism && !e.isDead()).count();
        long plants    = snapshot.stream().filter(e -> e instanceof Plant    && !e.isDead()).count();
        long nutrients = snapshot.stream().filter(e -> e instanceof Nutrient && !e.isDead()).count();

        publishEvent(new SimulationEvent.StepCompleted(
                step, (int)organisms, (int)plants, (int)nutrients, Instant.now()));

        if (step % 100 == 0) {
            log.debug("Step {} | O:{} P:{} N:{} | photoBonus:{:.3f}",
                    step, organisms, plants, nutrients,
                    regulator != null ? regulator.getPlantPhotosynthesisBonus() : 0f);
        }
    }

    private void cleanupDead(GridSimulationContext context) {
        grid.getAllEntities().stream()
                .filter(Entity::isDead)
                .forEach(dead -> {
                    context.scheduleRemoval(dead);
                    if (dead instanceof Organism organism) {
                        float richness = 8f + organism.getAge() * 0.04f;
                        context.spawnNutrientNear(dead.getPosition(), richness);
                    }
                    // Les plantes mortes produisent aussi un nutriment
                    else if (dead instanceof Plant plant) {
                        context.spawnNutrientNear(dead.getPosition(), 4f + plant.getEnergy() * 0.1f);
                    }
                });
    }

    // ─── Initialisation de la grille ──────────────────────────────────────────

    private void populateGrid() {
        Set<Position> used = new HashSet<>();

        spawnEntities(config.plantCount(), used, pos ->
                new Plant(pos, 300f, 0.3f + RNG.nextFloat() * 0.7f));


        // Nutriments spawned à des Z aléatoires pour remplir l'espace 3D
        for (int i = 0; i < config.nutrientCount(); i++) {
            Position pos = new Position(
                    RNG.nextInt(config.width()),
                    RNG.nextInt(config.height()),
                    RNG.nextInt(depth)          // z aléatoire dès le départ
            );
            float richness = 8f + RNG.nextFloat() * 15f;
            grid.addEntity(new Nutrient(pos, richness, richness));
        }

        spawnEntities(config.organismCount(), used, Organism::spawn);
    }

    private void spawnEntities(int count, Set<Position> used,
                               java.util.function.Function<Position, Entity> factory) {
        int attempts = 0, spawned = 0;
        int max = Math.max(count * 10, 1000);
        while (spawned < count && attempts < max) {
            attempts++;
            Position pos = new Position(RNG.nextInt(config.width()), RNG.nextInt(config.height()), 0);
            if (used.add(pos)) { grid.addEntity(factory.apply(pos)); spawned++; }
        }
        if (spawned < count)
            log.warn("Seulement {}/{} entités spawned", spawned, count);
    }

    // ─── API ─────────────────────────────────────────────────────────────────

    public List<Entity> getSnapshot()         { return grid.snapshot(); }
    public long         getCurrentStepValue() { return currentStep.get(); }
    public boolean      isRunning()           { return running; }
    public boolean      isPaused()            { return paused; }
    public int          getDepth()            { return depth; }

    public void addEventListener(Consumer<SimulationEvent> l) { eventListeners.add(l); }

    private void publishEvent(SimulationEvent event) {
        for (var l : eventListeners) {
            try { l.accept(event); }
            catch (Exception ex) { log.warn("Erreur listener : {}", ex.getMessage()); }
        }
    }
}