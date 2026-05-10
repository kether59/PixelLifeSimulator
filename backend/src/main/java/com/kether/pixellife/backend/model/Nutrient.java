package com.kether.pixellife.backend.model;

import com.kether.pixellife.backend.engine.SimulationContext;
import com.kether.pixellife.common.model.Position;
import lombok.Getter;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Nutriment — v4.
 *
 * Nouveauté : déplacement 3D flottant avec courant fictif.
 * Les nutriments dérivent dans l'espace comme des particules dans un fluide.
 *  - Vélocité flottante (vx, vy, vz) en float → position accumulée
 *  - La vélocité subit une légère perturbation aléatoire à chaque tick (brownian motion)
 *  - Courant fictif : variation lente de Z entre les couches (monte/descend occasionnellement)
 *  - Durée de vie réduite (dégradation 0.012/tick) → moins de nutriments accumulés
 *  - Ne se déplace que dans les limites de la grille 3D
 *
 * Résultat visuel : les nutriments flottent et tourbillonnent dans l'espace,
 * variant lentement entre les couches avec un effet de courant fictif.
 */
@Getter
public final class Nutrient extends Entity {

    private static final RandomGenerator RNG =
            RandomGeneratorFactory.getDefault().create();

    // ─── Paramètres de flottaison ─────────────────────────────────────────────
    private static final float DRIFT_SPEED     = 0.08f;   // vitesse max de dérive
    private static final float BROWNIAN_FORCE  = 0.015f;  // perturbation aléatoire par tick
    private static final float CURRENT_FORCE   = 0.005f;  // force du courant fictif (lent)
    private static final float DRAG            = 0.92f;   // amortissement (friction)
    private static final float METABOLISM      = 0.06f;   // dégradation naturelle /tick — 5× plus vite pour éviter l'accumulation

    private final float richness;  // énergie transférée à l'organisme à la consommation

    // Vélocité flottante accumulée — X, Y et Z lent pour variation entre couches
    private float vx = 0f, vy = 0f, vz = 0f;

    // Position accumulée en float pour interpolation sub-cellulaire
    private float fx, fy, fz;

    public Nutrient(Position position, float energy, float richness) {
        super(position, energy);
        this.richness = richness;
        this.fx = position.x();
        this.fy = position.y();
        this.fz = position.z();

        // Vitesse initiale aléatoire faible (X et Y seulement)
        this.vx = (RNG.nextFloat() - 0.5f) * DRIFT_SPEED;
        this.vy = (RNG.nextFloat() - 0.5f) * DRIFT_SPEED;
    }

    // ─── Cycle de vie ─────────────────────────────────────────────────────────

    @Override
    public void update(SimulationContext context) {
        drift(context);
        consumeEnergy(METABOLISM);
    }

    /**
     * Mouvement brownien en 3D avec courant fictif — simule la diffusion dans un fluide.
     * Les nutriments varient lentement entre les couches Z avec un courant fictif.
     */
    private void drift(SimulationContext context) {
        // Perturbation aléatoire (mouvement brownien) — X, Y et Z lent
        vx += (RNG.nextFloat() - 0.5f) * BROWNIAN_FORCE;
        vy += (RNG.nextFloat() - 0.5f) * BROWNIAN_FORCE;

        // Courant fictif : variation lente de Z (monte/descend occasionnellement)
        if (RNG.nextFloat() < 0.02f) {  // 2% de chance par tick de changer de direction Z
            vz += (RNG.nextFloat() - 0.5f) * CURRENT_FORCE * 2;
        } else {
            vz += (RNG.nextFloat() - 0.5f) * CURRENT_FORCE * 0.1f;  // variation très lente sinon
        }

        // Amortissement (drag fluide) — X, Y et Z
        vx *= DRAG;
        vy *= DRAG;
        vz *= DRAG;

        // Clamp de vitesse — X, Y et Z
        vx = clamp(vx, -DRIFT_SPEED, DRIFT_SPEED);
        vy = clamp(vy, -DRIFT_SPEED, DRIFT_SPEED);
        vz = clamp(vz, -DRIFT_SPEED * 0.5f, DRIFT_SPEED * 0.5f);  // Z plus lent

        // Intégration de position — X, Y et Z
        fx += vx;
        fy += vy;
        fz += vz;

        // Rebond sur les bords (réflexion) — X et Y seulement (Z peut varier librement)
        if (fx < 0)                         { fx = 0;                       vx = Math.abs(vx); }
        if (fx >= context.getWidth())       { fx = context.getWidth()  - 1; vx = -Math.abs(vx); }
        if (fy < 0)                         { fy = 0;                       vy = Math.abs(vy); }
        if (fy >= context.getHeight())      { fy = context.getHeight() - 1; vy = -Math.abs(vy); }

        // Z : rebond sur les limites de profondeur
        if (fz < 0)                         { fz = 0;                       vz = Math.abs(vz); }
        if (fz >= context.getDepth())       { fz = context.getDepth() - 1; vz = -Math.abs(vz); }

        // Mise à jour de la position discrète seulement si le nutriment change de cellule
        int nx = (int) fx, ny = (int) fy, nz = (int) fz;
        if (nx != position.x() || ny != position.y() || nz != position.z()) {
            Position oldPos = position;
            position = new Position(nx, ny, nz);
            context.updateEntityPosition(this, oldPos);
        }
    }

    /**
     * Position flottante continue pour le renderer (interpolation sub-cellulaire).
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