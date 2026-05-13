package com.kether.pixellife.common.model;

/**
 * Configurations prédéfinies pour différents types de parties.
 *
 * <p>Chaque preset définit à la fois les paramètres structurels (grille, populations)
 * et les règles biologiques via {@link BiologicalConfig}.</p>
 *
 * <h3>Choix du preset</h3>
 * <pre>
 *   Découverte         →  {@link #balanced()}
 *   Pression sélective →  {@link #competitive()}
 *   Grand écosystème   →  {@link #large()}
 *   Test rapide        →  {@link #quick()}
 *   Debug / pas à pas  →  {@link #debug()}
 *   Île isolée         →  {@link #island()}
 * </pre>
 */
public final class SimulationPreset {

    private SimulationPreset() {}

    /**
     * <b>Équilibré</b> — preset recommandé pour découvrir la simulation.
     *
     * <p>Population modérée, ressources suffisantes, évolution lente.
     * Cycle plantes ↔ organismes stable.</p>
     */
    public static SimulationConfig balanced() {
        return new SimulationConfig(
                80, 80, 600, 300, 40, 0,
                System.currentTimeMillis(), 0.05f,
                BiologicalConfig.defaults()
        );
    }

    /**
     * <b>Compétitif</b> — survie du plus apte, évolution accélérée.
     *
     * <p>Ressources rares, plantes plus fragiles, nutriments éphémères.
     * Extinctions locales fréquentes et forte pression sélective.</p>
     */
    public static SimulationConfig competitive() {
        return new SimulationConfig(
                60, 60, 200, 80, 80, 0,
                System.currentTimeMillis(), 0.15f,
                new BiologicalConfig(
                        150f,   // plantEnergyMax — plantes moins résistantes
                        0.08f,  // plantMetabolism — coût accru
                        16.0f,
                        0.18f,  // plantPhotosynthesisBase — moins d'énergie solaire
                        22f,    // plantBaseBite — morsures plus destructrices
                        0.50f,
                        150f,   // organismEnergyMax — organismes moins résistants
                        8f,     // organismEnergyStart — départ plus difficile
                        35f,    // organismReproCost — reproduction coûteuse
                        60,
                        90,     // organismAgePenaltyStart — vieillissement précoce
                        180,    // organismMaxAge — durée de vie réduite
                        0.35f,
                        0.15f,  // nutrientDriftSpeed — nutriments plus rapides
                        0.04f,
                        0.90f,
                        0.10f   // nutrientMetabolism — disparaissent vite
                )
        );
    }

    /**
     * <b>Grande simulation</b> — monde vaste favorisant la diversité des espèces.
     *
     * <p>L'espace permet à plusieurs niches écologiques de coexister sans
     * se croiser systématiquement. Nécessite plus de ressources CPU.</p>
     */
    public static SimulationConfig large() {
        return new SimulationConfig(
                150, 150, 2000, 800, 100, 0,
                System.currentTimeMillis(), 0.05f,
                BiologicalConfig.defaults()
        );
    }

    /**
     * <b>Rapide</b> — partie courte pour tester un paramètre précis.
     * S'arrête automatiquement à 1000 steps.
     */
    public static SimulationConfig quick() {
        return new SimulationConfig(
                40, 40, 200, 100, 20, 1000,
                System.currentTimeMillis(), 0.08f,
                BiologicalConfig.defaults()
        );
    }

    /**
     * <b>Debug</b> — environnement minimaliste pour le développement.
     * Peu d'entités, grille réduite, durée limitée à 300 steps.
     */
    public static SimulationConfig debug() {
        return new SimulationConfig(
                20, 20, 30, 20, 5, 300,
                System.currentTimeMillis(), 0.10f,
                BiologicalConfig.defaults()
        );
    }

    /**
     * <b>Île isolée</b> — écosystème fermé à faible biodiversité initiale.
     *
     * <p>Très peu d'organismes de départ : goulot d'étranglement génétique.
     * Reproduction facilitée pour permettre l'établissement de la population.</p>
     */
    public static SimulationConfig island() {
        return new SimulationConfig(
                35, 35, 150, 60, 8, 0,
                System.currentTimeMillis(), 0.12f,
                new BiologicalConfig(
                        200f, 0.04f, 16f, 0.22f, 18f, 0.50f,
                        200f,
                        12f,    // organismEnergyStart — légèrement plus d'énergie
                        20f,    // organismReproCost — reproduction moins coûteuse
                        40,     // organismReproCooldown — cooldown réduit
                        80,     // organismAgePenaltyStart — vieillissement tardif
                        200,
                        0.40f,  // organismMergeThreshold — fusions plus faciles
                        0.12f, 0.03f, 0.92f, 0.05f
                )
        );
    }
}