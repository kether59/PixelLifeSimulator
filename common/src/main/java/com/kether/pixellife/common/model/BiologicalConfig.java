package com.kether.pixellife.common.model;

/**
 * Paramètres biologiques configurables par simulation — v2.
 *
 * Ajustements v2 (équilibrage plantes/organismes) :
 *   - plantMetabolism : 0.05 → 0.04  (plantes moins coûteuses à maintenir)
 *   - plantPhotosynthesisBase : 0.20 → 0.23 (gain solaire légèrement plus fort)
 */
public record BiologicalConfig(
        float plantEnergyMax,
        float plantMetabolism,
        float plantMaxHeight,
        float plantPhotosynthesisBase,
        float plantBaseBite,
        float plantRobustnessFactor,
        float organismEnergyMax,
        float organismEnergyStart,
        float organismReproCost,
        int   organismReproCooldown,
        int   organismAgePenaltyStart,
        int   organismMaxAge,
        float organismMergeThreshold,
        float nutrientDriftSpeed,
        float nutrientBrownianForce,
        float nutrientDrag,
        float nutrientMetabolism
) {
    public BiologicalConfig {
        if (plantEnergyMax          <= 0) throw new IllegalArgumentException("plantEnergyMax <= 0");
        if (plantMetabolism         <  0) throw new IllegalArgumentException("plantMetabolism < 0");
        if (plantMaxHeight          <= 0) throw new IllegalArgumentException("plantMaxHeight <= 0");
        if (plantPhotosynthesisBase <  0) throw new IllegalArgumentException("plantPhotosynthesisBase < 0");
        if (plantBaseBite           <= 0) throw new IllegalArgumentException("plantBaseBite <= 0");
        if (plantRobustnessFactor   <  0) throw new IllegalArgumentException("plantRobustnessFactor < 0");
        if (organismEnergyMax       <= 0) throw new IllegalArgumentException("organismEnergyMax <= 0");
        if (organismEnergyStart     <= 0) throw new IllegalArgumentException("organismEnergyStart <= 0");
        if (organismReproCost       <  0) throw new IllegalArgumentException("organismReproCost < 0");
        if (organismReproCooldown   <  0) throw new IllegalArgumentException("organismReproCooldown < 0");
        if (organismAgePenaltyStart <= 0) throw new IllegalArgumentException("organismAgePenaltyStart <= 0");
        if (organismMaxAge          <= 0) throw new IllegalArgumentException("organismMaxAge <= 0");
        if (nutrientMetabolism      <  0) throw new IllegalArgumentException("nutrientMetabolism < 0");
        if (nutrientDrag < 0 || nutrientDrag > 1) throw new IllegalArgumentException("nutrientDrag hors [0,1]");
    }

    public static BiologicalConfig defaults() {
        return new BiologicalConfig(
                200f,   // plantEnergyMax
                0.04f,  // plantMetabolism        ← réduit (était 0.05) : plantes moins fragiles
                16.0f,  // plantMaxHeight
                0.23f,  // plantPhotosynthesisBase ← augmenté (était 0.20) : photosynthèse plus forte
                18f,    // plantBaseBite
                0.50f,  // plantRobustnessFactor
                200f,   // organismEnergyMax
                10f,    // organismEnergyStart
                30f,    // organismReproCost
                50,     // organismReproCooldown
                100,    // organismAgePenaltyStart
                200,    // organismMaxAge
                0.35f,  // organismMergeThreshold
                0.12f,  // nutrientDriftSpeed
                0.03f,  // nutrientBrownianForce
                0.92f,  // nutrientDrag
                0.07f   // nutrientMetabolism      ← légèrement augmenté (0.06→0.07) : dégradation plus rapide
        );
    }
}