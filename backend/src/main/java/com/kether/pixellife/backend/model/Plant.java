package com.kether.pixellife.backend.model;

import com.kether.pixellife.backend.engine.SimulationContext;
import com.kether.pixellife.common.model.Position;
import lombok.Getter;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Plante avec croissance progressive en hauteur.
 */
@Getter
public final class Plant extends Entity {

    private static final RandomGenerator RNG =
            RandomGeneratorFactory.getDefault().create();

    // Paramètres biologiques
    private static final float ENERGY_MAX       = 200f;
    private static final float METABOLISM       = 0.05f;
    private static final float MAX_HEIGHT       = 16.0f;  // augmenté pour 16 niveaux
    private static final float GROWTH_SPEED     = 0.005f; // accéléré de 0.002f à 0.005f

    // Robustesse proportionnelle à la taille
    static final float BASE_BITE        = 10f;
    static final float ROBUSTNESS_FACTOR= 0.15f;

    private final float growthRate;
    private float height = 0.0f;      // hauteur actuelle (instance variable, pas static)

    public Plant(Position position, float energy, float growthRate) {
        super(position, energy);
        this.growthRate = growthRate;
    }

    @Override
    public void update(SimulationContext context) {
        // Photosynthèse proportionnelle à la hauteur
        float photosynthesisFactor = 1.0f + height / MAX_HEIGHT;
        float bonus = context.getPlantPhotosynthesisBonus();
        float gain = (0.20f + bonus) * growthRate * photosynthesisFactor;
        energy = Math.min(energy + gain, ENERGY_MAX);

        // Croissance en hauteur avec vitesse aléatoire entre 50 et 300 ticks par étage
        if (energy > ENERGY_MAX * 0.3f && height < MAX_HEIGHT) {
            float growthSpeedFactor = 1.0f / RNG.nextInt(50, 300);
            height = Math.min(height + GROWTH_SPEED * growthRate * energy / ENERGY_MAX * growthSpeedFactor, MAX_HEIGHT);

            // Mise à jour de la position Z
            int newZ = (int) height;
            if (newZ != position.z()) {
                Position oldPos = position;
                position = new Position(position.x(), position.y(), newZ);
                context.updateEntityPosition(this, oldPos);
            }
        }

        // Production de nutriments
        if (energy > ENERGY_MAX * 0.5f && RNG.nextFloat() < growthRate * 0.05f) {
            float richness = 5f + RNG.nextFloat() * 10f;
            context.spawnNutrientNear(position, richness);
        }

        // Reproduction au niveau du sol
        if (energy > ENERGY_MAX * 0.6f && RNG.nextFloat() < 0.008f * growthRate) {
            Position groundPos = new Position(position.x(), position.y(), 0);
            context.findFreePositionNear(groundPos, 4).ifPresent(newPos -> {
                context.spawnPlantNear(newPos);
                energy -= ENERGY_MAX * 0.15f;
            });
        }

        consumeEnergy(METABOLISM);
    }

    public float getBiteEnergyForThis() {
        return BASE_BITE / (1f + height * ROBUSTNESS_FACTOR);
    }

    public float getRenderHeight() {
        return height / MAX_HEIGHT;
    }

    public float getRenderRadius() {
        return 0.30f + (height / MAX_HEIGHT) * 0.12f;
    }

    @Override public String typeName() { return "PLANT"; }

    @Override public String toString() {
        return "Plant{id=%d, energy=%.1f, height=%.2f, growthRate=%.2f}"
                .formatted(id, energy, height, growthRate);
    }
}