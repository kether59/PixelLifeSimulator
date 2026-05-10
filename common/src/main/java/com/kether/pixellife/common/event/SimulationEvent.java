package com.kether.pixellife.common.event;

import com.kether.pixellife.common.model.EntityType;
import com.kether.pixellife.common.model.Position;

import java.time.Instant;

/**
 * Événements du moteur de simulation — v3.
 */
public sealed interface SimulationEvent permits
        SimulationEvent.EntitySpawned,
        SimulationEvent.EntityDied,
        SimulationEvent.EntityMoved,
        SimulationEvent.EntityAte,
        SimulationEvent.EntityReproduced,
        SimulationEvent.EntityMerged,
        SimulationEvent.EntityMutated,
        SimulationEvent.StepCompleted,
        SimulationEvent.SimulationEnded {

    Instant timestamp();

    record EntitySpawned(long entityId, EntityType type, Position position, Instant timestamp)
            implements SimulationEvent {}

    record EntityDied(long entityId, Position position, String cause, Instant timestamp)
            implements SimulationEvent {}

    record EntityMoved(long entityId, Position from, Position to, Instant timestamp)
            implements SimulationEvent {}

    record EntityAte(long entityId, long targetId, float energyGained, Instant timestamp)
            implements SimulationEvent {}

    record EntityReproduced(long parentAId, long parentBId, long childId, Position position, Instant timestamp)
            implements SimulationEvent {}

    /** Fusion de deux organismes génétiquement proches en entité composite. */
    record EntityMerged(long parentAId, long parentBId, long childId, Position position, Instant timestamp)
            implements SimulationEvent {}

    /** Mutation notable lors d'une reproduction (variation > seuil). */
    record EntityMutated(long entityId, float[] oldDna, float[] newDna, Instant timestamp)
            implements SimulationEvent {}

    record StepCompleted(long step, int organismCount, int plantCount, int nutrientCount, Instant timestamp)
            implements SimulationEvent {}

    record SimulationEnded(long simulationId, long totalSteps, String reason, Instant timestamp)
            implements SimulationEvent {}
}