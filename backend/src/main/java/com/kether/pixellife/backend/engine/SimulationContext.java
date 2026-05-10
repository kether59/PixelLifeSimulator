package com.kether.pixellife.backend.engine;

import com.kether.pixellife.backend.model.Entity;
import com.kether.pixellife.backend.model.Organism;
import com.kether.pixellife.common.model.Position;
import com.kether.pixellife.common.event.SimulationEvent;

import java.util.List;
import java.util.Optional;

/**
 * Interface contexte de simulation — v4.
 *
 * Nouveautés :
 *  - getPlantPhotosynthesisBonus()   → bonus adaptatif fourni par EcosystemRegulator
 *  - getOrganismMetabolismPenalty()  → pénalité adaptative fournie par EcosystemRegulator
 */
public interface SimulationContext {

    List<Entity> getEntitiesAt(Position pos);
    List<Entity> getEntitiesInRadius(Position center, int radius);
    boolean isFree(Position pos);
    Optional<Position> findFreePositionNear(Position center, int maxRadius);

    int   getWidth();
    int   getHeight();
    int   getDepth();
    long  getCurrentStep();
    float getMutationRate();

    /** Bonus additif de photosynthèse appris par l'EcosystemRegulator. Défaut 0. */
    float getPlantPhotosynthesisBonus();

    /** Pénalité additive de métabolisme apprise par l'EcosystemRegulator. Défaut 0. */
    float getOrganismMetabolismPenalty();

    void spawnNutrientNear(Position origin, float richness);
    void spawnPlantNear(Position origin);
    void spawnOrganism(Organism organism);
    void scheduleRemoval(Entity entity);
    void publishEvent(SimulationEvent event);
    void updateEntityPosition(Entity entity, Position oldPos);
}