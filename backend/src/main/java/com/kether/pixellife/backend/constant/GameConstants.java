package com.kether.pixellife.backend.constant;

import java.nio.file.Path;

/**
 * Point de configuration central de la simulation.
 *
 * <p>Ce fichier fusionne et remplace {@code GameConstants} et {@code Constant} (supprimé).
 * Toute constante partagée entre moteur, entités et régulateur est définie ici.</p>
 *
 * <p>Les valeurs biologiques ajustables à chaud par simulation sont dans
 * {@link com.kether.pixellife.common.model.BiologicalConfig}. Les constantes
 * ci-dessous restent les seuils structurels et les magic numbers extraits
 * du code métier pour la lisibilité.</p>
 *
 * <h3>Sections</h3>
 * <ul>
 *   <li>{@code ORGANISM_*} — constantes structurelles des organismes</li>
 *   <li>{@code PLANT_*}    — seuils internes de Plant.update()</li>
 *   <li>{@code ENGINE_*}   — structure du monde 3D</li>
 *   <li>{@code REGULATION_*} — homéostase adaptative</li>
 *   <li>{@code KEY_*}      — clés HTTP / WebSocket</li>
 * </ul>
 */
public final class GameConstants {

    private GameConstants() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // ORGANISME — constantes structurelles (non configurables à chaud)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Énergie combinée maximale après fusion de deux organismes. */
    public static final float ORGANISM_MAX_MERGED_ENERGY     = 100f;

    /** Taux de mutation par défaut lors de la reproduction (si non précisé par SimulationConfig). */
    public static final float ORGANISM_DEFAULT_MUTATION_RATE = 0.10f;

    // Seuils comportementaux extraits de Organism.java
    /** Seuil d'agressivité déclenchant la chasse d'organismes plus faibles. */
    public static final float ORGANISM_AGGRESSION_HUNT_THRESHOLD  = 0.6f;
    /** Seuil d'agressivité déclenchant le vol d'énergie par contact. */
    public static final float ORGANISM_AGGRESSION_STEAL_THRESHOLD = 0.7f;
    /** Fraction de l'énergie de la proie volée par bouchée. */
    public static final float ORGANISM_STEAL_FRACTION             = 0.25f;
    /** Efficacité de conversion du vol (énergie volée → énergie gagnée). */
    public static final float ORGANISM_STEAL_EFFICIENCY           = 0.60f;
    /** Efficacité de conversion du broutage de plante. */
    public static final float ORGANISM_PLANT_BITE_EFFICIENCY      = 0.80f;
    /** Coût de taille par tick : (size − base) × factor. */
    public static final float ORGANISM_SIZE_COST_FACTOR           = 0.08f;
    /** Taille de référence en dessous de laquelle le coût de taille est nul. */
    public static final float ORGANISM_SIZE_COST_BASE             = 0.15f;
    /** Pénalité d'énergie par tick quand aucun voisin n'est présent dans un rayon de 3. */
    public static final float ORGANISM_LONE_PENALTY               = 0.50f;
    /** Multiplicateur du vieillissement quadratique après AGE_PENALTY_START. */
    public static final float ORGANISM_AGE_PENALTY_FACTOR         = 0.15f;
    /** Énergie minimale pour qu'un organisme tente une fusion. */
    public static final float ORGANISM_MERGE_MIN_ENERGY           = 40f;
    /** Fraction de l'énergie max en dessous de laquelle une proie est vulnérable au vol. */
    public static final float ORGANISM_PREY_ENERGY_RATIO          = 0.50f;
    /** Maturité maximale d'une plante herbivore pouvant encore être chassée. */
    public static final float ORGANISM_PLANT_MATURITY_HERBIVORE   = 0.85f;
    /** Maturité maximale d'une plante omnivore/carnivore pouvant être chassée. */
    public static final float ORGANISM_PLANT_MATURITY_DEFAULT     = 0.60f;
    /** Énergie en dessous de laquelle un organisme mange n'importe quelle plante (faim critique). */
    public static final float ORGANISM_HUNGER_CRITICAL_THRESHOLD  = 20f;

    // ═══════════════════════════════════════════════════════════════════════════
    // PLANTE — seuils internes de Plant.update()
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Hauteur maximale par défaut — utilisée uniquement par le renderer pour
     * normaliser {@code getRenderHeight()} indépendamment de la BiologicalConfig.
     */
    public static final float PLANT_MAX_HEIGHT = 16.0f;

    /** Dégâts minimaux par morsure, quelle que soit la robustesse de la plante. */
    public static final float PLANT_MIN_BITE_ENERGY = 1.5f;

    // Seuils de Plant.update() — exprimés en fraction de plantEnergyMax
    /** Fraction d'énergie max requise pour déclencher la croissance verticale. */
    public static final float PLANT_GROW_ENERGY_THRESHOLD    = 0.30f;
    /** Facteur de vitesse de croissance par tick. */
    public static final float PLANT_GROW_RATE_FACTOR         = 0.01f;
    /** Fraction d'énergie max requise pour spawner un nutriment spontanément. */
    public static final float PLANT_NUTRIENT_SPAWN_THRESHOLD = 0.50f;
    /** Probabilité de base de spawn de nutriment (multipliée par growthRate). */
    public static final float PLANT_NUTRIENT_SPAWN_CHANCE    = 0.05f;
    /** Fraction d'énergie max requise pour se reproduire. */
    public static final float PLANT_REPRO_ENERGY_THRESHOLD   = 0.60f;
    /** Probabilité de base de reproduction (multipliée par growthRate). */
    public static final float PLANT_REPRO_BASE_CHANCE        = 0.008f;
    /** Fraction d'énergie max consommée lors d'une reproduction. */
    public static final float PLANT_REPRO_ENERGY_COST        = 0.15f;

    // ═══════════════════════════════════════════════════════════════════════════
    // NUTRIMENT — constante structurelle
    // ═══════════════════════════════════════════════════════════════════════════

    /** Probabilité par tick d'un burst de courant vertical (variation de Z rapide). */
    public static final float NUTRIENT_Z_BURST_CHANCE  = 0.05f;
    /** Facteur de courant vertical hors burst (variation très lente). */
    public static final float NUTRIENT_Z_DRIFT_DAMPING = 0.20f;
    /** Fraction de la vitesse max X/Y appliquée à l'axe Z. */
    public static final float NUTRIENT_Z_SPEED_RATIO   = 0.50f;
    /** Facteur amplificateur du burst vertical. */
    public static final float NUTRIENT_Z_BURST_FACTOR  = 4.0f;
    /** Force du courant vertical fictif (stable, non configurable). */
    public static final float NUTRIENT_CURRENT_FORCE   = 0.02f;

    // ═══════════════════════════════════════════════════════════════════════════
    // MOTEUR — structure du monde 3D
    // ═══════════════════════════════════════════════════════════════════════════

    /** Nombre de niveaux Z de la grille (profondeur). Doit être cohérent avec PLANT_MAX_HEIGHT. */
    public static final int   ENGINE_DEPTH                = 16;
    /** Fraction de ENGINE_DEPTH utilisée pour la hauteur maximale du terrain procédural. */
    public static final float ENGINE_TERRAIN_HEIGHT_RATIO = 0.35f;

    // ═══════════════════════════════════════════════════════════════════════════
    // RÉGULATION — homéostase adaptative
    // ═══════════════════════════════════════════════════════════════════════════

    /** Nombre de ticks entre deux cycles de régulation homéostatique. */
    public static final long  REGULATION_INTERVAL          = 50L;

    /** Ratio population/initiale en dessous duquel une injection d'urgence de plantes est déclenchée. */
    public static final float REGULATION_CRITICAL_PLANT    = 0.15f;
    /** Seuil critique pour les organismes (ratio courant/initial). */
    public static final float REGULATION_CRITICAL_ORGANISM = 0.10f;
    /** Seuil critique pour les nutriments. */
    public static final float REGULATION_CRITICAL_NUTRIENT = 0.05f;

    /**
     * Coefficient α de la moyenne mobile exponentielle (EMA) du ratio d'organismes.
     * Mémoire ≈ 1/α cycles. 0.1 ≈ 10 intervalles de régulation.
     */
    public static final float REGULATION_EMA_ALPHA      = 0.10f;
    /** Complément de l'alpha EMA (1 − α). Évite les magic numbers dans le code. */
    public static final float REGULATION_EMA_COMPLEMENT = 1f - REGULATION_EMA_ALPHA;

    /** Borne inférieure du bonus de photosynthèse adaptatif. */
    public static final float REGULATION_PHOTO_BONUS_MIN   = 0.00f;
    /** Borne supérieure du bonus de photosynthèse adaptatif. */
    public static final float REGULATION_PHOTO_BONUS_MAX   = 0.50f;
    /** Borne inférieure de la pénalité de métabolisme adaptative. */
    public static final float REGULATION_META_PENALTY_MIN  = 0.00f;
    /** Borne supérieure de la pénalité de métabolisme adaptative. */
    public static final float REGULATION_META_PENALTY_MAX  = 0.30f;
    /** Borne inférieure du taux de spawn spontané de nutriments. */
    public static final float REGULATION_NUTRIENT_RATE_MIN = 0.000f;
    /** Borne supérieure du taux de spawn spontané de nutriments. */
    public static final float REGULATION_NUTRIENT_RATE_MAX = 0.050f;
    /** Borne inférieure du bonus de reproduction des plantes. */
    public static final float REGULATION_REPRO_BONUS_MIN   = 0.000f;
    /** Borne supérieure du bonus de reproduction des plantes. */
    public static final float REGULATION_REPRO_BONUS_MAX   = 0.020f;

    // ═══════════════════════════════════════════════════════════════════════════
    // PERSISTANCE
    // ═══════════════════════════════════════════════════════════════════════════

    /** Chemin du fichier JSON des paramètres adaptatifs inter-sessions. */
    public static final Path CONFIG_PATH = Path.of(
            System.getProperty("user.home"), ".pixellife", "ecosystem_params.json");

    // ═══════════════════════════════════════════════════════════════════════════
    // CLÉS HTTP / WEBSOCKET
    // ═══════════════════════════════════════════════════════════════════════════

    /** Clé JSON pour l'identifiant de simulation dans les réponses REST. */
    public static final String KEY_SIMULATION_ID = "simulationId";
    /** Clé JSON pour le délai entre ticks. */
    public static final String KEY_TICK_DELAY_MS = "tickDelayMs";
    /** Clé JSON pour les messages d'erreur. */
    public static final String KEY_ERROR         = "error";
    /** Clé JSON pour les messages de statut (paused, running, stopped). */
    public static final String KEY_STATUS        = "status";
}