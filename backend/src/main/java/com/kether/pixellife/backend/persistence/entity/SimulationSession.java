package com.kether.pixellife.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Session de jeu persistée en SQLite.
 */
@Entity
@Table(name = "simulation_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Référence vers l'enregistrement complet (nullable si utilisé de façon autonome) */
    @Column(name = "simulation_record_id")
    private Long simulationRecordId;

    // ─── Configuration de la grille ───────────────────────────────────────────

    @Column(nullable = false)
    private int gridWidth;

    @Column(nullable = false)
    private int gridHeight;

    // ─── Horodatages ──────────────────────────────────────────────────────────

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    // ─── Compteurs au DÉMARRAGE ───────────────────────────────────────────────

    /** Nombre d'organismes vivants au step 0 */
    @Column(nullable = false)
    private int initialOrganismCount;

    /** Nombre de plantes au step 0 */
    @Column(nullable = false)
    private int initialPlantCount;

    /** Nombre de nutriments au step 0 */
    @Column(nullable = false)
    private int initialNutrientCount;

    // ─── Compteurs à la FIN ───────────────────────────────────────────────────

    /** Nombre d'organismes vivants au dernier step */
    private int finalOrganismCount;

    /** Nombre de plantes au dernier step */
    private int finalPlantCount;

    /** Nombre de nutriments au dernier step */
    private int finalNutrientCount;

    // ─── Déroulement ──────────────────────────────────────────────────────────

    /** Nombre total de ticks/steps exécutés */
    @Column(nullable = false)
    private long totalSteps;

    /** Pic du nombre d'organismes atteint pendant la session */
    private int peakOrganismCount;

    /** Raison de fin : "max_steps_reached" | "extinction" | "stopped" */
    private String endReason;

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Nombre total d'entités au démarrage (toutes catégories confondues) */
    @Transient
    public int totalInitialEntities() {
        return initialOrganismCount + initialPlantCount + initialNutrientCount;
    }

    /** Nombre total d'entités à la fin */
    @Transient
    public int totalFinalEntities() {
        return finalOrganismCount + finalPlantCount + finalNutrientCount;
    }

    /** Durée de la session en secondes, -1 si pas encore terminée */
    @Transient
    public long durationSeconds() {
        if (finishedAt == null) return -1L;
        return finishedAt.getEpochSecond() - startedAt.getEpochSecond();
    }
}