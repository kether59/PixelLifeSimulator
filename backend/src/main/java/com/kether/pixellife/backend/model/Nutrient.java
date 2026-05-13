package com.kether.pixellife.backend.model;

import com.kether.pixellife.backend.constant.GameConstants;
import com.kether.pixellife.backend.engine.SimulationContext;
import com.kether.pixellife.common.model.BiologicalConfig;
import com.kether.pixellife.common.model.Position;
import lombok.Getter;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Nutriment — particule d'énergie flottante en mouvement brownien 3D.
 *
 * <p>Les nutriments dérivent dans l'espace comme des particules dans un fluide.
 * Leurs paramètres physiques (vitesse, friction, dégradation) sont configurables
 * via {@link BiologicalConfig}.</p>
 *
 * <h3>Physique du mouvement</h3>
 * <pre>
 *   vx += bruit() × brownianForce   // perturbation aléatoire
 *   vx *= drag                       // amortissement fluide
 *   fx += vx                         // intégration position
 * </pre>
 * L'axe Z (vertical) se déplace plus lentement que X/Y ({@code Z_SPEED_RATIO}).
 * Un "burst" de courant vertical se produit aléatoirement tous les ~20 ticks.
 */
@Getter
public final class Nutrient extends Entity {

    private static final RandomGenerator RNG = RandomGeneratorFactory.getDefault().create();

    private final float richness; // énergie transférée à l'organisme à la consommation

    private float vx, vy, vz;
    private float fx, fy, fz;

    public Nutrient(Position position, float energy, float richness) {
        super(position, energy);
        this.richness = richness;
        this.fx = position.x();
        this.fy = position.y();
        this.fz = position.z();

        // Vitesse initiale aléatoire faible sur X/Y (Z commence immobile)
        float initSpeed = BiologicalConfig.defaults().nutrientDriftSpeed();
        this.vx = (RNG.nextFloat() - 0.5f) * initSpeed;
        this.vy = (RNG.nextFloat() - 0.5f) * initSpeed;
    }

    // ─── Cycle de vie ─────────────────────────────────────────────────────────

    @Override
    public void update(SimulationContext context) {
        BiologicalConfig bio = context.getBiologicalConfig();
        drift(context, bio);
        consumeEnergy(bio.nutrientMetabolism());
    }

    // ─── Physique brownienne 3D ───────────────────────────────────────────────

    private void drift(SimulationContext context, BiologicalConfig bio) {
        float maxSpeed = bio.nutrientDriftSpeed();
        float brownian = bio.nutrientBrownianForce();
        float drag     = bio.nutrientDrag();

        // Perturbation brownienne X/Y
        vx += (RNG.nextFloat() - 0.5f) * brownian;
        vy += (RNG.nextFloat() - 0.5f) * brownian;

        // Courant vertical — burst occasionnel + variation douce
        vz += RNG.nextFloat() < GameConstants.NUTRIENT_Z_BURST_CHANCE
                ? (RNG.nextFloat() - 0.5f) * GameConstants.NUTRIENT_CURRENT_FORCE * GameConstants.NUTRIENT_Z_BURST_FACTOR
                : (RNG.nextFloat() - 0.5f) * GameConstants.NUTRIENT_CURRENT_FORCE * GameConstants.NUTRIENT_Z_DRIFT_DAMPING;

        // Amortissement fluide
        vx *= drag;
        vy *= drag;
        vz *= drag;

        // Clamp vitesses
        vx = clamp(vx, -maxSpeed,                            maxSpeed);
        vy = clamp(vy, -maxSpeed,                            maxSpeed);
        vz = clamp(vz, -maxSpeed * GameConstants.NUTRIENT_Z_SPEED_RATIO,
                maxSpeed * GameConstants.NUTRIENT_Z_SPEED_RATIO);

        // Intégration position
        fx += vx; fy += vy; fz += vz;

        // Rebonds sur les bords
        if (fx < 0)                     { fx = 0;                        vx =  Math.abs(vx); }
        if (fx >= context.getWidth())   { fx = context.getWidth()  - 1f; vx = -Math.abs(vx); }
        if (fy < 0)                     { fy = 0;                        vy =  Math.abs(vy); }
        if (fy >= context.getHeight())  { fy = context.getHeight() - 1f; vy = -Math.abs(vy); }
        if (fz < 0)                     { fz = 0;                        vz =  Math.abs(vz); }
        if (fz >= context.getDepth())   { fz = context.getDepth()  - 1f; vz = -Math.abs(vz); }

        // Mise à jour de la position discrète seulement si changement de cellule
        int nx = (int) fx, ny = (int) fy, nz = (int) fz;
        if (nx != position.gridX() || ny != position.gridY() || nz != position.gridZ()) {
            Position oldPos = position;
            position = new Position(nx, ny, nz);
            context.updateEntityPosition(this, oldPos);
        }
    }

    /**
     * Position flottante continue [x, y, z] pour le renderer.
     * Permet une animation fluide sans saccades entre cellules.
     */
    public float[] getFloatPosition() {
        return new float[]{ fx, fy, fz };
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override public String typeName() { return "NUTRIENT"; }
}