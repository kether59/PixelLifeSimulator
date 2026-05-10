package com.kether.pixellife.common.dto;

import com.kether.pixellife.common.model.SimulationConfig;

import java.time.Instant;
import java.util.List;

/**
 * DTOs partagés backend ↔ frontend — v4.
 *
 * EntitySnapshot enrichi :
 *  - floatX, floatY, floatZ : position continue (sub-cellulaire) pour les nutriments flottants
 *  - renderHeight : hauteur normalisée [0..1] pour les plantes qui grandissent
 */
public final class SimulationDtos {

    private SimulationDtos() {}

    public record SimulationSummary(
            Long id,
            SimulationConfig config,
            Instant startedAt,
            Instant finishedAt,
            long totalSteps,
            SimulationStatus status
    ) {}

    public record GridSnapshot(
            long simulationId,
            long step,
            int width,
            int height,
            int depth,
            List<EntitySnapshot> entities
    ) {
        public GridSnapshot(long simulationId, long step, int width, int height,
                            List<EntitySnapshot> entities) {
            this(simulationId, step, width, height, 1, entities);
        }
    }

    /**
     * Snapshot d'une entité pour le rendu 3D.
     *
     * @param x, y, z       position discrète (cellule)
     * @param floatX/Y/Z    position continue en float — pour les nutriments flottants.
     *                      Vaut x+0.5, y+0.5, z+0.5 pour les entités non-flottantes.
     * @param renderRadius  rayon de rendu (sphère/cylindre)
     * @param renderHeight  hauteur normalisée [0..1] pour les plantes (0 = petite, 1 = max)
     * @param dna           génome float[7] — null pour Plant/Nutrient
     */
    public record EntitySnapshot(
            long    id,
            String  type,           // "ORGANISM" | "PLANT" | "NUTRIENT"
            int     x,
            int     y,
            int     z,
            float   floatX,         // position continue — surtout utile pour NUTRIENT
            float   floatY,
            float   floatZ,
            float   energy,
            String  gender,
            float[] dna,
            float   renderRadius,
            float   renderHeight    // [0..1] — pour PLANT uniquement (hauteur du cylindre)
    ) {
        /** Constructeur de compatibilité minimale (2D, sans extras). */
        public EntitySnapshot(long id, String type, int x, int y, float energy, String gender) {
            this(id, type, x, y, 0,
                    x + 0.5f, y + 0.5f, 0.5f,
                    energy, gender, null, 0.32f, 0f);
        }
    }

    public record SimulationStartRequest(SimulationConfig config) {}
    public record SimulationStartResponse(long simulationId, String message) {}
    public enum SimulationStatus { RUNNING, PAUSED, FINISHED, ERROR }
}