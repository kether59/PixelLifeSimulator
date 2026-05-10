package com.kether.pixellife.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Enregistrement JPA d'une simulation en base SQLite — v3.
 *
 * Migration :
 *   plantRatio   (float) → plantCount   (int)
 *   nutrientRatio(float) → nutrientCount(int)
 *   organismRatio(float) → organismCount(int)
 *   + mutationRate (float) ajouté
 *
 * Script de migration SQLite à exécuter UNE FOIS sur la base existante :
 * <pre>
 *   ALTER TABLE simulations ADD COLUMN plant_count    INTEGER NOT NULL DEFAULT 0;
 *   ALTER TABLE simulations ADD COLUMN nutrient_count INTEGER NOT NULL DEFAULT 0;
 *   ALTER TABLE simulations ADD COLUMN organism_count INTEGER NOT NULL DEFAULT 0;
 *   ALTER TABLE simulations ADD COLUMN mutation_rate  REAL    NOT NULL DEFAULT 0.05;
 *   -- Mise à jour des anciennes lignes depuis les ratios
 *   UPDATE simulations SET
 *       plant_count    = CAST(grid_width * grid_height * plant_ratio    AS INTEGER),
 *       nutrient_count = CAST(grid_width * grid_height * nutrient_ratio AS INTEGER),
 *       organism_count = CAST(grid_width * grid_height * organism_ratio AS INTEGER);
 *   -- Les anciennes colonnes peuvent être supprimées dans SQLite via recreate-table
 * </pre>
 *
 * Avec Hibernate DDL auto=update, les nouvelles colonnes sont créées automatiquement.
 * Les anciennes colonnes (plantRatio, etc.) sont laissées en base pour compatibilité
 * des anciennes données — Hibernate les ignore simplement.
 */
@Entity
@Table(name = "simulations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ─── Config grille ────────────────────────────────────────────────────────

    @Column(nullable = false)
    private int gridWidth;

    @Column(nullable = false)
    private int gridHeight;

    // ─── Comptages initiaux (remplacent les anciens ratios) ───────────────────

    /** Nombre de plantes au démarrage de la simulation. */
    @Column(nullable = false)
    private int plantCount;

    /** Nombre de nutriments au démarrage. */
    @Column(nullable = false)
    private int nutrientCount;

    /** Nombre d'organismes au démarrage. */
    @Column(nullable = false)
    private int organismCount;

    /** Taux de mutation génétique configuré (0.0 – 1.0). */
    @Column(nullable = false)
    private float mutationRate;

    // ─── Config simulation ────────────────────────────────────────────────────

    /** Nombre maximum de steps. 0 = simulation infinie. */
    @Column(nullable = false)
    private int maxSteps;

    @Column(nullable = false)
    private long seed;

    // ─── Résultats ────────────────────────────────────────────────────────────

    @Column(nullable = false)
    private long totalSteps;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SimulationStatus status;

    private String endReason;

    // ─── Timestamps ───────────────────────────────────────────────────────────

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    // ─── Snapshots JSON ───────────────────────────────────────────────────────

    @Column(columnDefinition = "TEXT")
    private String initialGridSnapshot;

    @Column(columnDefinition = "TEXT")
    private String finalGridSnapshot;

    // ─── Statistiques agrégées ────────────────────────────────────────────────

    private int peakOrganismCount;
    private int finalOrganismCount;
    private int finalPlantCount;
    private int finalNutrientCount;

    public enum SimulationStatus {
        RUNNING, PAUSED, FINISHED, ERROR
    }
}