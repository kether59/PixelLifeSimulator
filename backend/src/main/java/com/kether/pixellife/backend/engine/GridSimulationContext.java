package com.kether.pixellife.backend.engine;

import com.kether.pixellife.backend.model.*;
import com.kether.pixellife.common.event.SimulationEvent;
import com.kether.pixellife.common.model.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Contexte de simulation v4.
 * Nouveauté : référence au {@link EcosystemRegulator} pour exposer
 * les bonus adaptatifs aux entités pendant leur update().
 *  - getPlantPhotosynthesisBonus() → Plant l'utilise pour sa photosynthèse
 *  - getOrganismMetabolismPenalty() → Organism l'utilise pour son coût
 */
public class GridSimulationContext implements SimulationContext {

    private static final RandomGenerator RNG = RandomGeneratorFactory.getDefault().create();

    private final Grid  grid;
    private final long  currentStep;
    private final Consumer<SimulationEvent> eventPublisher;
    private final float mutationRate;
    private final int   depth;
    private final EcosystemRegulator regulator; // peut être null

    private final List<Entity> toAdd    = new ArrayList<>();
    private final List<Entity> toRemove = new ArrayList<>();

    public GridSimulationContext(Grid grid, long currentStep,
                                 Consumer<SimulationEvent> eventPublisher,
                                 float mutationRate, int depth,
                                 EcosystemRegulator regulator) {
        this.grid           = grid;
        this.currentStep    = currentStep;
        this.eventPublisher = eventPublisher;
        this.mutationRate   = mutationRate;
        this.depth          = Math.max(1, depth);
        this.regulator      = regulator;
    }

    // ─── SimulationContext ────────────────────────────────────────────────────

    @Override public List<Entity> getEntitiesAt(Position pos)                      { return grid.getEntitiesAt(pos); }
    @Override public List<Entity> getEntitiesInRadius(Position center, int radius) { return grid.getEntitiesInRadius(center, radius); }
    @Override public boolean isFree(Position pos)                                   { return grid.isFree(pos); }
    @Override public Optional<Position> findFreePositionNear(Position c, int r)    { return grid.findFreePositionNear(c, r); }

    @Override public int   getWidth()        { return grid.getWidth(); }
    @Override public int   getHeight()       { return grid.getHeight(); }
    @Override public int   getDepth()        { return depth; }
    @Override public long  getCurrentStep()  { return currentStep; }
    @Override public float getMutationRate() { return mutationRate; }

    /**
     * Bonus de photosynthèse appris par le régulateur.
     * 0.0 si pas de régulateur (première exécution ou mode debug).
     */
    @Override
    public float getPlantPhotosynthesisBonus() {
        return regulator != null ? regulator.getPlantPhotosynthesisBonus() : 0f;
    }

    /**
     * Pénalité de métabolisme apprise par le régulateur.
     * 0.0 si pas de régulateur.
     */
    @Override
    public float getOrganismMetabolismPenalty() {
        return regulator != null ? regulator.getOrganismMetabolismPenalty() : 0f;
    }

    @Override public void spawnNutrientNear(Position origin, float richness) {
        grid.findFreePositionNear(origin, 2).ifPresent(pos -> {
            // Ajoute une hauteur Z aléatoire entre 0 et depth-1
            int randomZ = RNG.nextInt(depth);
            Position posWithZ = new Position(pos.x(), pos.y(), randomZ);
            toAdd.add(new Nutrient(posWithZ, richness, richness));
        });
    }

    @Override public void spawnPlantNear(Position origin) {
        grid.findFreePositionNear(origin, 3).ifPresent(pos ->
                toAdd.add(new Plant(pos, 300f, 0.4f + (float)(Math.random() * 0.6f))));
    }

    @Override public void spawnOrganism(Organism o)   { toAdd.add(o); }
    @Override public void scheduleRemoval(Entity e)   { toRemove.add(e); }
    @Override public void publishEvent(SimulationEvent ev) { eventPublisher.accept(ev); }
    @Override public void updateEntityPosition(Entity entity, Position oldPos) {
        grid.updatePosition(entity, oldPos);
    }

    public void flush() {
        toRemove.forEach(grid::removeEntity);
        toAdd.forEach(grid::addEntity);
    }
}