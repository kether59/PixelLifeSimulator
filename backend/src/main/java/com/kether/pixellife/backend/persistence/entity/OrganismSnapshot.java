package com.kether.pixellife.backend.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Snapshot d'une entité organisme à un step donné.
 * Stocke la position, l'énergie, la génération et l'âge.
 */
@Entity
@Table(name = "organism_snapshots",
        indexes = {
                @Index(name = "idx_sim_step", columnList = "simulationId, step"),
                @Index(name = "idx_entity",   columnList = "entityId")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganismSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long simulationId;

    @Column(nullable = false)
    private long step;

    @Column(nullable = false)
    private long entityId;

    // État biologique
    private int x;
    private int y;
    private float energy;
    private int age;
    private int generation;
    private String gender;

    /** Séquence ADN encodée en base64 */
    @Column(columnDefinition = "TEXT")
    private String dnaSequence;

    /** Vecteur de mémoire cellulaire sérialisé en JSON (futur) */
    @Column(columnDefinition = "TEXT")
    private String cellularMemory;
}