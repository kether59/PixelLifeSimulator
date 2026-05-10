package com.kether.pixellife.common.model;

/**
 * Sealed interface représentant les types d'entités de la simulation.
 * Exploite les sealed types Java 17+ pour un pattern matching exhaustif.
 */
public sealed interface EntityType permits EntityType.OrganismType, EntityType.PlantType, EntityType.NutrientType {

    record OrganismType(Gender gender, int generation) implements EntityType {}
    record PlantType(float growthRate) implements EntityType {}
    record NutrientType(float richness) implements EntityType {}

    static OrganismType organism(Gender gender, int generation) {
        return new OrganismType(gender, generation);
    }

    static PlantType plant(float growthRate) {
        return new PlantType(growthRate);
    }

    static NutrientType nutrient(float richness) {
        return new NutrientType(richness);
    }
}