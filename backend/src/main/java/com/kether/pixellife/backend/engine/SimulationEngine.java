package com.kether.pixellife.backend.engine;

import com.kether.pixellife.backend.model.*;
import com.kether.pixellife.common.event.SimulationEvent;
import com.kether.pixellife.common.model.BiologicalConfig;
import com.kether.pixellife.common.model.Position;
import com.kether.pixellife.common.model.SimulationConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Moteur principal de simulation.
 *
 * <p>Orchestre le cycle tick-by-tick :</p>
 * <ol>
 *   <li>Mise à jour de chaque entité via {@link GridSimulationContext}</li>
 *   <li>Nettoyage des morts (spawn d'un nutriment par organisme/plante morte)</li>
 *   <li>Régulation homéostatique ({@link EcosystemRegulator}) toutes les 50 steps</li>
 *   <li>Flush du contexte (application des ajouts/suppressions en attente)</li>
 * </ol>
 *
 * <p>La {@link BiologicalConfig} est extraite de {@link SimulationConfig#effectiveBioConfig()}
 * une fois à l'initialisation et propagée au contexte de chaque tick.</p>
 */
@Slf4j
@Getter
public class SimulationEngine {

    private static final int DEFAULT_DEPTH = 16;

    private final long             simulationId;
    private final SimulationConfig config;
    private final Grid             grid;
    private final int              depth;
    private final BiologicalConfig bioConfig;

    private EcosystemRegulator regulator; // initialisé après populateGrid()

    private volatile boolean running     = false;
    private volatile boolean paused      = false;
    private volatile long    tickDelayMs;

    private float[][] terrainMap;

    private final AtomicLong currentStep = new AtomicLong(0);
    private final List<Consumer<SimulationEvent>> eventListeners = new CopyOnWriteArrayList<>();

    private static final RandomGenerator RNG = RandomGeneratorFactory.getDefault().create();

    public SimulationEngine(long simulationId, SimulationConfig config) {
        this.simulationId = simulationId;
        this.config       = config;
        this.grid         = new Grid(config.width(), config.height());
        this.depth        = DEFAULT_DEPTH;
        this.bioConfig    = config.effectiveBioConfig();
    }

    // ─── Cycle de vie ─────────────────────────────────────────────────────────

    public void initialize() {
        log.info("Initialisation simulation #{} ({}×{} depth={})",
                simulationId, config.width(), config.height(), depth);
        terrainMap = generateTerrain();
        populateGrid();

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
                grid, step, this::publishEvent, config.mutationRate(),
                depth, regulator, bioConfig, terrainMap);

        for (Entity entity : grid.snapshot()) {
            if (!entity.isDead()) entity.update(context);
        }

        cleanupDead(context);

        if (regulator != null) regulator.regulate(step, context);

        context.flush();

        if (step % 100 == 0) {
            long organisms = grid.getAllEntities().stream().filter(e -> e instanceof Organism && !e.isDead()).count();
            long plants    = grid.getAllEntities().stream().filter(e -> e instanceof Plant    && !e.isDead()).count();
            long nutrients = grid.getAllEntities().stream().filter(e -> e instanceof Nutrient && !e.isDead()).count();
            publishEvent(new SimulationEvent.StepCompleted(
                    step, (int)organisms, (int)plants, (int)nutrients, Instant.now()));
            log.debug("Step {} | O:{} P:{} N:{} | photoBonus:{:.3f}",
                    step, organisms, plants, nutrients,
                    regulator != null ? regulator.getPlantPhotosynthesisBonus() : 0f);
        }
    }

    private void cleanupDead(GridSimulationContext context) {
        grid.getAllEntities().stream().filter(Entity::isDead).forEach(dead -> {
            context.scheduleRemoval(dead);
            if (dead instanceof Organism organism) {
                context.spawnNutrientNear(dead.getPosition(), 8f + organism.getAge() * 0.04f);
            } else if (dead instanceof Plant plant) {
                context.spawnNutrientNear(dead.getPosition(), 4f + plant.getEnergy() * 0.1f);
            }
        });
    }

    // ─── Initialisation de la grille ──────────────────────────────────────────

    private void populateGrid() {
        Set<Position> used = new HashSet<>();

        spawnEntities(config.plantCount(), used,
                pos -> new Plant(pos, bioConfig.plantEnergyMax(), 0.3f + RNG.nextFloat() * 0.7f));

        for (int i = 0; i < config.nutrientCount(); i++) {
            Position pos = new Position(
                    RNG.nextInt(config.width()), RNG.nextInt(config.height()), RNG.nextInt(depth));
            float richness = 8f + RNG.nextFloat() * 15f;
            grid.addEntity(new Nutrient(pos, richness, richness));
        }

        spawnEntities(config.organismCount(), used,
                pos -> Organism.spawn(pos, bioConfig.organismEnergyStart()));
    }

    private void spawnEntities(int count, Set<Position> used, Function<Position, Entity> factory) {
        int attempts = 0, spawned = 0;
        int max = Math.max(count * 10, 1000);
        while (spawned < count && attempts < max) {
            attempts++;
            int rx  = RNG.nextInt(config.width());
            int ry  = RNG.nextInt(config.height());
            float tz = terrainMap != null ? terrainMap[rx][ry] : 0f;
            Position pos = new Position(rx, ry, tz);
            if (used.add(pos)) { grid.addEntity(factory.apply(pos)); spawned++; }
        }
        if (spawned < count) log.warn("Seulement {}/{} entités spawned", spawned, count);
    }

    // ─── Génération du terrain ────────────────────────────────────────────────

    private float[][] generateTerrain() {
        int w = config.width(), h = config.height();
        float maxTerrainHeight = depth * 0.35f;
        float[][] map = new float[w][h];

        double ox1 = RNG.nextDouble(Math.PI * 2), ox2 = RNG.nextDouble(Math.PI * 2);
        double oy1 = RNG.nextDouble(Math.PI * 2), oy2 = RNG.nextDouble(Math.PI * 2);

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                double nx = (double) x / w, ny = (double) y / h;
                double v =
                        Math.sin(nx * 2 * Math.PI + ox1) * Math.cos(ny * 2 * Math.PI + oy1) * 0.5
                                + Math.sin(nx * 5 * Math.PI + ox2) * Math.sin(ny * 4 * Math.PI + oy2) * 0.3
                                + Math.cos(nx * 9 * Math.PI)       * Math.sin(ny * 7 * Math.PI)       * 0.2;
                map[x][y] = (float) ((v + 1.0) * 0.5 * maxTerrainHeight);
            }
        }
        return map;
    }

    // ─── API ──────────────────────────────────────────────────────────────────

    public List<Entity> getSnapshot()         { return grid.snapshot(); }
    public long         getCurrentStepValue() { return currentStep.get(); }
    public boolean      isRunning()           { return running; }
    public boolean      isPaused()            { return paused; }
    public int          getDepth()            { return depth; }

    public float getTerrainHeight(int x, int y) {
        if (terrainMap == null || x < 0 || x >= terrainMap.length
                || y < 0 || y >= terrainMap[0].length) return 0f;
        return terrainMap[x][y];
    }

    public void addEventListener(Consumer<SimulationEvent> l) { eventListeners.add(l); }

    private void publishEvent(SimulationEvent event) {
        for (var l : eventListeners) {
            try { l.accept(event); }
            catch (Exception ex) { log.warn("Erreur listener : {}", ex.getMessage()); }
        }
    }
}