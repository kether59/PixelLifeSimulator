package com.kether.pixellife.backend.model;

import com.kether.pixellife.backend.constant.GameConstants;
import com.kether.pixellife.backend.engine.SimulationContext;
import com.kether.pixellife.common.model.BiologicalConfig;
import com.kether.pixellife.common.model.Position;
import lombok.Getter;
import lombok.Setter;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Plante — producteur primaire de l'écosystème.
 *
 * <p>Cycle de vie par tick :</p>
 * <ol>
 *   <li><b>Photosynthèse</b> — gain proportionnel à la hauteur, au génome et au bonus adaptatif.</li>
 *   <li><b>Croissance</b> — monte vers {@code BiologicalConfig.plantMaxHeight}.</li>
 *   <li><b>Production de nutriments</b> — spawn spontané si énergie > 50 %.</li>
 *   <li><b>Reproduction</b> — spawn d'une plante fille à ±4 cases si énergie > 60 %.</li>
 *   <li><b>Métabolisme</b> — coût de maintenance par tick.</li>
 * </ol>
 *
 * <p>Tous les seuils biologiques sont lus depuis {@link BiologicalConfig} à chaque tick,
 * ce qui permet une configuration à chaud sans recompilation.</p>
 */
@Getter
@Setter
public final class Plant extends Entity {

    private static final RandomGenerator RNG = RandomGeneratorFactory.getDefault().create();

    /** Taux de croissance intrinsèque — déterminé à la naissance, invariant. */
    private final float growthRate;

    /** Hauteur actuelle en niveaux Z (0.0 → {@code plantMaxHeight}). */
    private float height = 0.0f;

    public Plant(Position position, float energy, float growthRate) {
        super(position, energy);
        this.growthRate = growthRate;
    }

    // ─── Cycle de vie ─────────────────────────────────────────────────────────

    @Override
    public void update(SimulationContext context) {
        BiologicalConfig bio = context.getBiologicalConfig();

        photosynthesise(context, bio);
        grow(context, bio);
        maybeSpawnNutrient(context, bio);
        maybeReproduce(context, bio);
        consumeEnergy(bio.plantMetabolism());
    }

    private void photosynthesise(SimulationContext context, BiologicalConfig bio) {
        float heightFactor = 1f + height / bio.plantMaxHeight();
        float gain = (bio.plantPhotosynthesisBase() + context.getPlantPhotosynthesisBonus())
                * growthRate * heightFactor;
        energy = Math.min(energy + gain, bio.plantEnergyMax());
    }

    private void grow(SimulationContext context, BiologicalConfig bio) {
        if (energy <= bio.plantEnergyMax() * GameConstants.PLANT_GROW_ENERGY_THRESHOLD) return;
        if (height >= bio.plantMaxHeight()) return;

        height += growthRate * GameConstants.PLANT_GROW_RATE_FACTOR * (energy / bio.plantEnergyMax());
        height  = Math.min(height, bio.plantMaxHeight());

        int newZ = (int) height;
        if (newZ != position.z()) {
            Position oldPos = position;
            position = new Position(position.x(), position.y(), newZ);
            context.updateEntityPosition(this, oldPos);
        }
    }

    private void maybeSpawnNutrient(SimulationContext context, BiologicalConfig bio) {
        if (energy > bio.plantEnergyMax() * GameConstants.PLANT_NUTRIENT_SPAWN_THRESHOLD
                && RNG.nextFloat() < growthRate * GameConstants.PLANT_NUTRIENT_SPAWN_CHANCE) {
            context.spawnNutrientNear(position, 5f + RNG.nextFloat() * 10f);
        }
    }

    private void maybeReproduce(SimulationContext context, BiologicalConfig bio) {
        if (energy <= bio.plantEnergyMax() * GameConstants.PLANT_REPRO_ENERGY_THRESHOLD) return;
        if (RNG.nextFloat() >= GameConstants.PLANT_REPRO_BASE_CHANCE * growthRate) return;

        Position groundPos = new Position(position.x(), position.y(), 0);
        context.findFreePositionNear(groundPos, 4).ifPresent(newPos -> {
            context.spawnPlantNear(newPos);
            energy -= bio.plantEnergyMax() * GameConstants.PLANT_REPRO_ENERGY_COST;
        });
    }

    // ─── Calculs de rendu et de combat ────────────────────────────────────────

    /**
     * Énergie prélevée par une morsure — réduite par la robustesse liée à la hauteur.
     *
     * <p>Protection = 1 + hauteur × robustnessFactor.<br>
     * À la hauteur max, la plante reçoit au minimum {@code PLANT_MIN_BITE_ENERGY}.</p>
     *
     * @param bio configuration biologique de la simulation courante
     */
    public float getBiteEnergyForThis(BiologicalConfig bio) {
        float protection = 1f + height * bio.plantRobustnessFactor();
        return Math.max(GameConstants.PLANT_MIN_BITE_ENERGY, bio.plantBaseBite() / protection);
    }

    /** Hauteur normalisée [0, 1] par rapport à {@code PLANT_MAX_HEIGHT} pour le rendu. */
    public float getRenderHeight() {
        return height / GameConstants.PLANT_MAX_HEIGHT;
    }

    /** Rayon de rendu proportionnel à la maturité. */
    public float getRenderRadius() {
        return 0.30f + getRenderHeight() * 0.12f;
    }

    @Override public String typeName() { return "PLANT"; }

    @Override public String toString() {
        return "Plant{id=%d, energy=%.1f, height=%.2f, growthRate=%.2f}"
                .formatted(id, energy, height, growthRate);
    }
}