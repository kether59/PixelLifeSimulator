package com.kether.pixellife.common.model;

/**
 * Paramètres biologiques configurables à chaud par simulation.
 *
 * Ces valeurs surchargent les constantes par défaut de {@code GameConstants}
 * pour chaque lancement, sans recompilation. Elles sont transmises depuis
 * le frontend via {@code SimulationConfig.biologicalConfig} et lues par les
 * entités à chaque tick via {@code SimulationContext.getBiologicalConfig()}.
 *
 * <h3>Utilisation typique</h3>
 * <pre>
 *   // Utiliser les défauts
 *   BiologicalConfig bio = BiologicalConfig.defaults();
 *
 *   // Personnaliser (simulation compétitive)
 *   BiologicalConfig bio = new BiologicalConfig(
 *       150f,  // plantEnergyMax — plantes plus fragiles
 *       0.08f, // plantMetabolism — coût plus élevé
 *       ...
 *   );
 * </pre>
 *
 * @see SimulationConfig
 * @see SimulationPreset
 */
public record BiologicalConfig(

        // ─── Plantes ──────────────────────────────────────────────────────────
        /** Réserve d'énergie maximale d'une plante. */
        float plantEnergyMax,
        /** Coût métabolique par tick (très faible — plantes quasi-autosuffisantes). */
        float plantMetabolism,
        /** Hauteur maximale en niveaux Z. Doit être ≤ ENGINE_DEPTH. */
        float plantMaxHeight,
        /** Gain de photosynthèse de base par tick, avant le facteur hauteur. */
        float plantPhotosynthesisBase,
        /** Énergie prélevée par une morsure au niveau du sol (hauteur 0). */
        float plantBaseBite,
        /** Réduction des dégâts par niveau de hauteur : protection = 1 + h × facteur. */
        float plantRobustnessFactor,

        // ─── Organismes ───────────────────────────────────────────────────────
        /** Réserve d'énergie maximale d'un organisme. */
        float organismEnergyMax,
        /** Énergie initiale lors d'un spawn — volontairement basse pour forcer la recherche. */
        float organismEnergyStart,
        /** Coût énergétique par parent à chaque reproduction. */
        float organismReproCost,
        /** Nombre minimum de ticks entre deux reproductions (cooldown). */
        int   organismReproCooldown,
        /** Tick à partir duquel la pénalité de vieillissement quadratique démarre. */
        int   organismAgePenaltyStart,
        /** Âge maximal absolu — mort garantie à cet âge quelle que soit l'énergie. */
        int   organismMaxAge,
        /** Distance ADN maximale [0-1] autorisant la fusion entre deux organismes. */
        float organismMergeThreshold,

        // ─── Nutriments ───────────────────────────────────────────────────────
        /** Vitesse de dérive maximale (unités/tick). */
        float nutrientDriftSpeed,
        /** Amplitude de la perturbation brownienne par tick. */
        float nutrientBrownianForce,
        /** Coefficient d'amortissement fluide — 1.0 = sans friction, 0.0 = arrêt immédiat. */
        float nutrientDrag,
        /** Dégradation naturelle par tick — les nutriments disparaissent s'ils ne sont pas consommés. */
        float nutrientMetabolism

) {
    /** Validation compacte à la construction. */
    public BiologicalConfig {
        if (plantEnergyMax          <= 0)           throw new IllegalArgumentException("plantEnergyMax <= 0");
        if (plantMetabolism         <  0)           throw new IllegalArgumentException("plantMetabolism < 0");
        if (plantMaxHeight          <= 0)           throw new IllegalArgumentException("plantMaxHeight <= 0");
        if (plantPhotosynthesisBase <  0)           throw new IllegalArgumentException("plantPhotosynthesisBase < 0");
        if (plantBaseBite           <= 0)           throw new IllegalArgumentException("plantBaseBite <= 0");
        if (plantRobustnessFactor   <  0)           throw new IllegalArgumentException("plantRobustnessFactor < 0");
        if (organismEnergyMax       <= 0)           throw new IllegalArgumentException("organismEnergyMax <= 0");
        if (organismEnergyStart     <= 0)           throw new IllegalArgumentException("organismEnergyStart <= 0");
        if (organismReproCost       <  0)           throw new IllegalArgumentException("organismReproCost < 0");
        if (organismReproCooldown   <  0)           throw new IllegalArgumentException("organismReproCooldown < 0");
        if (organismAgePenaltyStart <= 0)           throw new IllegalArgumentException("organismAgePenaltyStart <= 0");
        if (organismMaxAge          <= 0)           throw new IllegalArgumentException("organismMaxAge <= 0");
        if (nutrientMetabolism      <  0)           throw new IllegalArgumentException("nutrientMetabolism < 0");
        if (nutrientDrag < 0 || nutrientDrag > 1)  throw new IllegalArgumentException("nutrientDrag hors [0,1]");
    }

    /**
     * Paramètres biologiques par défaut — équilibrés pour {@link SimulationConfig#defaults()}.
     * Ces valeurs correspondent aux constantes historiques de GameConstants.
     */
    public static BiologicalConfig defaults() {
        return new BiologicalConfig(
                200f,   // plantEnergyMax
                0.05f,  // plantMetabolism
                16.0f,  // plantMaxHeight
                0.20f,  // plantPhotosynthesisBase
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
                0.06f   // nutrientMetabolism
        );
    }
}