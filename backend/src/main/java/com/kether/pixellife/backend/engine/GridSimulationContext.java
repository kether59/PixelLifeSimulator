package com.kether.pixellife.backend.engine;

import com.kether.pixellife.backend.model.*;
import com.kether.pixellife.common.event.SimulationEvent;
import com.kether.pixellife.common.model.BiologicalConfig;
import com.kether.pixellife.common.model.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Implémentation du contexte de simulation pour la grille 3D.
 *
 * <p>Créée à chaque tick par {@link SimulationEngine}, elle est passagère :
 * elle accumule les entités à ajouter/supprimer dans des listes,
 * puis les applique en une seule passe via {@link #flush()} après l'update
 * de toutes les entités — évitant ainsi les modifications concurrentes.</p>
 */
public class GridSimulationContext implements SimulationContext {

    private static final RandomGenerator RNG = RandomGeneratorFactory.getDefault().create();

    private final Grid             grid;
    private final long             currentStep;
    private final Consumer<SimulationEvent> eventPublisher;
    private final float            mutationRate;
    private final int              depth;
    private final EcosystemRegulator regulator;   // nullable
    private final BiologicalConfig bioConfig;
    private final float[][]        terrainMap;    // nullable

    private final List<Entity> toAdd    = new ArrayList<>();
    private final List<Entity> toRemove = new ArrayList<>();

    public GridSimulationContext(Grid grid, long currentStep,
                                 Consumer<SimulationEvent> eventPublisher,
                                 float mutationRate, int depth,
                                 EcosystemRegulator regulator,
                                 BiologicalConfig bioConfig,
                                 float[][] terrainMap) {
        this.grid           = Objects.requireNonNull(grid, "grid");
        this.currentStep    = currentStep;
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.mutationRate   = mutationRate;
        this.depth          = Math.max(1, depth);
        this.regulator      = regulator;
        this.bioConfig      = Objects.requireNonNull(bioConfig, "bioConfig");
        this.terrainMap     = terrainMap;
    }

    // ─── SimulationContext ────────────────────────────────────────────────────

    @Override public List<Entity> getEntitiesAt(Position pos)                      { return grid.getEntitiesAt(pos); }
    @Override public List<Entity> getEntitiesInRadius(Position center, int radius) { return grid.getEntitiesInRadius(center, radius); }
    @Override public boolean isFree(Position pos)                                   { return grid.isFree(pos); }
    @Override public Optional<Position> findFreePositionNear(Position c, int r)    { return grid.findFreePositionNear(c, r); }

    @Override public int             getWidth()              { return grid.getWidth(); }
    @Override public int             getHeight()             { return grid.getHeight(); }
    @Override public int             getDepth()              { return depth; }
    @Override public long            getCurrentStep()        { return currentStep; }
    @Override public float           getMutationRate()       { return mutationRate; }
    @Override public BiologicalConfig getBiologicalConfig()  { return bioConfig; }

    @Override
    public float getTerrainHeight(int x, int y) {
        if (terrainMap == null || x < 0 || x >= terrainMap.length
                || y < 0 || y >= terrainMap[0].length) return 0f;
        return terrainMap[x][y];
    }

    @Override
    public float getPlantPhotosynthesisBonus() {
        return regulator != null ? regulator.getPlantPhotosynthesisBonus() : 0f;
    }

    @Override
    public float getOrganismMetabolismPenalty() {
        return regulator != null ? regulator.getOrganismMetabolismPenalty() : 0f;
    }

    @Override
    public void spawnNutrientNear(Position origin, float richness) {
        grid.findFreePositionNear(origin, 2).ifPresent(pos -> {
            int randomZ = RNG.nextInt(depth);
            toAdd.add(new Nutrient(new Position(pos.x(), pos.y(), randomZ), richness, richness));
        });
    }

    @Override
    public void spawnPlantNear(Position origin) {
        grid.findFreePositionNear(origin, 3).ifPresent(pos -> {
            float tz         = getTerrainHeight(pos.gridX(), pos.gridY());
            float growthRate = 0.4f + (float)(Math.random() * 0.6f);
            toAdd.add(new Plant(new Position(pos.x(), pos.y(), tz), bioConfig.plantEnergyMax(), growthRate));
        });
    }

    @Override public void spawnOrganism(Organism o)              { toAdd.add(o); }
    @Override public void scheduleRemoval(Entity e)              { toRemove.add(e); }
    @Override public void publishEvent(SimulationEvent ev)       { eventPublisher.accept(ev); }

    @Override
    public void updateEntityPosition(Entity entity, Position oldPos) {
        grid.updatePosition(entity, oldPos);
    }

    /**
     * Applique les ajouts et suppressions en attente après le tick.
     * Doit être appelé une seule fois par tick, après l'update de toutes les entités.
     */
    public void flush() {
        toRemove.forEach(grid::removeEntity);
        toAdd.forEach(grid::addEntity);
    }
}