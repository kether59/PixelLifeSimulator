package com.kether.pixellife.backend.engine;

import com.kether.pixellife.backend.model.Entity;
import com.kether.pixellife.backend.model.Organism;
import com.kether.pixellife.common.event.SimulationEvent;
import com.kether.pixellife.common.model.BiologicalConfig;
import com.kether.pixellife.common.model.Position;

import java.util.List;
import java.util.Optional;

/**
 * Interface contexte de simulation — v5.
 *
 * <p>Expose aux entités tout ce dont elles ont besoin pendant leur {@code update()} :</p>
 * <ul>
 *   <li>Lecture spatiale (voisins, positions libres)</li>
 *   <li>Mutations du monde (spawn, suppression, déplacement)</li>
 *   <li>{@link BiologicalConfig} — règles biologiques configurées par partie</li>
 *   <li>Bonus adaptatifs appris par {@link EcosystemRegulator}</li>
 * </ul>
 */
public interface SimulationContext {

    // ─── Lecture spatiale ─────────────────────────────────────────────────────

    List<Entity> getEntitiesAt(Position pos);
    List<Entity> getEntitiesInRadius(Position center, int radius);
    boolean isFree(Position pos);
    Optional<Position> findFreePositionNear(Position center, int maxRadius);

    // ─── Métadonnées du monde ─────────────────────────────────────────────────

    int   getWidth();
    int   getHeight();
    int   getDepth();

    /** Hauteur du terrain (sol) pour une cellule donnée. Retourne 0 si terrain plat. */
    default float getTerrainHeight(int x, int y) { return 0f; }

    long  getCurrentStep();
    float getMutationRate();

    // ─── Configuration biologique ─────────────────────────────────────────────

    /** Règles biologiques configurées pour cette simulation. Jamais null. */
    BiologicalConfig getBiologicalConfig();

    // ─── Paramètres adaptatifs (EcosystemRegulator) ───────────────────────────

    /** Bonus additif de photosynthèse appris par le régulateur. Défaut : 0. */
    float getPlantPhotosynthesisBonus();

    /** Pénalité additive de métabolisme apprise par le régulateur. Défaut : 0. */
    float getOrganismMetabolismPenalty();

    // ─── Mutations du monde ───────────────────────────────────────────────────

    void spawnNutrientNear(Position origin, float richness);
    void spawnPlantNear(Position origin);
    void spawnOrganism(Organism organism);
    void scheduleRemoval(Entity entity);
    void publishEvent(SimulationEvent event);
    void updateEntityPosition(Entity entity, Position oldPos);
}