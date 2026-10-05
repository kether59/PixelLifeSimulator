package com.kether.pixellife.backend.constant;

import java.nio.file.Path;

/**
 * Constantes structurelles de la simulation — v2.
 *
 * Nouveautés v2 (équilibrage) :
 *   - ORGANISM_FOOD_SCARCITY_PENALTY   : pénalité quand aucune nourriture visible
 *   - ORGANISM_FOOD_SCARCITY_THRESHOLD : seuil d'énergie activant la disette
 *   - PLANT_REPRO_BASE_CHANCE  : 0.008 → 0.013 (plantes reproduisent plus souvent)
 *   - PLANT_REPRO_ENERGY_THRESHOLD : 0.60 → 0.50 (reproduction possible plus tôt)
 *   - REGULATION_NUTRIENT_RATE_MAX : réduit pour limiter l'explosion de nutriments
 */
public final class GameConstants {

    private GameConstants() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // ORGANISME
    // ═══════════════════════════════════════════════════════════════════════════

    public static final float ORGANISM_MAX_MERGED_ENERGY     = 100f;
    public static final float ORGANISM_DEFAULT_MUTATION_RATE = 0.10f;

    public static final float ORGANISM_AGGRESSION_HUNT_THRESHOLD  = 0.6f;
    public static final float ORGANISM_AGGRESSION_STEAL_THRESHOLD = 0.7f;
    public static final float ORGANISM_STEAL_FRACTION             = 0.25f;
    public static final float ORGANISM_STEAL_EFFICIENCY           = 0.60f;
    public static final float ORGANISM_PLANT_BITE_EFFICIENCY      = 0.80f;
    public static final float ORGANISM_SIZE_COST_FACTOR           = 0.08f;
    public static final float ORGANISM_SIZE_COST_BASE             = 0.15f;
    public static final float ORGANISM_LONE_PENALTY               = 0.50f;
    public static final float ORGANISM_AGE_PENALTY_FACTOR         = 0.15f;
    public static final float ORGANISM_MERGE_MIN_ENERGY           = 40f;
    public static final float ORGANISM_PREY_ENERGY_RATIO          = 0.50f;
    public static final float ORGANISM_PLANT_MATURITY_HERBIVORE   = 0.85f;
    public static final float ORGANISM_PLANT_MATURITY_DEFAULT     = 0.60f;
    public static final float ORGANISM_HUNGER_CRITICAL_THRESHOLD  = 20f;

    /**
     * Pénalité d'énergie/tick quand l'organisme est en état de faim (énergie < seuil)
     * ET qu'aucune source de nourriture n'est visible dans son rayon de vision.
     * Simule le coût métabolique de la recherche active sans succès.
     */
    public static final float ORGANISM_FOOD_SCARCITY_PENALTY   = 0.40f;

    /**
     * Fraction de l'énergie max définissant l'état de faim.
     * En dessous de ce seuil, la pénalité de disette s'active.
     */
    public static final float ORGANISM_FOOD_SCARCITY_THRESHOLD = 0.60f;

    // ═══════════════════════════════════════════════════════════════════════════
    // PLANTE
    // ═══════════════════════════════════════════════════════════════════════════

    public static final float PLANT_MAX_HEIGHT = 16.0f;
    public static final float PLANT_MIN_BITE_ENERGY = 1.5f;

    public static final float PLANT_GROW_ENERGY_THRESHOLD    = 0.30f;
    public static final float PLANT_GROW_RATE_FACTOR         = 0.01f;
    public static final float PLANT_NUTRIENT_SPAWN_THRESHOLD = 0.50f;
    public static final float PLANT_NUTRIENT_SPAWN_CHANCE    = 0.04f;  // 0.05 → 0.04

    /** Seuil abaissé : les plantes commencent à se reproduire à 50% d'énergie (vs 60%). */
    public static final float PLANT_REPRO_ENERGY_THRESHOLD   = 0.50f;  // était 0.60

    /** Taux de reproduction augmenté pour maintenir la population face aux herbivores. */
    public static final float PLANT_REPRO_BASE_CHANCE        = 0.013f; // était 0.008

    public static final float PLANT_REPRO_ENERGY_COST        = 0.15f;

    // ═══════════════════════════════════════════════════════════════════════════
    // NUTRIMENT
    // ═══════════════════════════════════════════════════════════════════════════

    public static final float NUTRIENT_Z_BURST_CHANCE  = 0.05f;
    public static final float NUTRIENT_Z_DRIFT_DAMPING = 0.20f;
    public static final float NUTRIENT_Z_SPEED_RATIO   = 0.50f;
    public static final float NUTRIENT_Z_BURST_FACTOR  = 4.0f;
    public static final float NUTRIENT_CURRENT_FORCE   = 0.02f;

    // ═══════════════════════════════════════════════════════════════════════════
    // MOTEUR
    // ═══════════════════════════════════════════════════════════════════════════

    public static final int   ENGINE_DEPTH                = 16;
    public static final float ENGINE_TERRAIN_HEIGHT_RATIO = 0.35f;

    // ═══════════════════════════════════════════════════════════════════════════
    // RÉGULATION
    // ═══════════════════════════════════════════════════════════════════════════

    public static final long  REGULATION_INTERVAL          = 50L;

    public static final float REGULATION_CRITICAL_PLANT    = 0.15f;
    public static final float REGULATION_CRITICAL_ORGANISM = 0.10f;
    public static final float REGULATION_CRITICAL_NUTRIENT = 0.05f;

    public static final float REGULATION_EMA_ALPHA      = 0.10f;
    public static final float REGULATION_EMA_COMPLEMENT = 1f - REGULATION_EMA_ALPHA;

    public static final float REGULATION_PHOTO_BONUS_MIN   = 0.00f;
    public static final float REGULATION_PHOTO_BONUS_MAX   = 0.50f;
    public static final float REGULATION_META_PENALTY_MIN  = 0.00f;
    public static final float REGULATION_META_PENALTY_MAX  = 0.30f;
    public static final float REGULATION_NUTRIENT_RATE_MIN = 0.000f;
    /** Réduit de 0.050 → 0.030 pour limiter l'explosion de nutriments. */
    public static final float REGULATION_NUTRIENT_RATE_MAX = 0.030f;
    public static final float REGULATION_REPRO_BONUS_MIN   = 0.000f;
    public static final float REGULATION_REPRO_BONUS_MAX   = 0.020f;

    // ═══════════════════════════════════════════════════════════════════════════
    // PERSISTANCE
    // ═══════════════════════════════════════════════════════════════════════════

    public static final Path CONFIG_PATH = Path.of(
            System.getProperty("user.home"), ".pixellife", "ecosystem_params.json");

    // ═══════════════════════════════════════════════════════════════════════════
    // CLÉS HTTP / WEBSOCKET
    // ═══════════════════════════════════════════════════════════════════════════

    public static final String KEY_SIMULATION_ID = "simulationId";
    public static final String KEY_TICK_DELAY_MS = "tickDelayMs";
    public static final String KEY_ERROR         = "error";
    public static final String KEY_STATUS        = "status";
}