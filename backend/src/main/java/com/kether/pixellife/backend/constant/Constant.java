package com.kether.pixellife.backend.constant;

import java.nio.file.Path;

public class Constant {
    public static final String SIMULATION_ID = "simulationId";
    public static final String TICK_DELAY_MS = "tickDelayMs";
    public static final String ERROR = "error";
    public static final String STATUS = "status";
    // ─── Fréquence de régulation ─────────────────────────────────────────────
    public static final long REGULATION_INTERVAL = 50;   // toutes les 50 steps
    // ─── Seuils critiques (relatifs à la population de départ) ───────────────
    public static final float CRITICAL_RATIO_PLANT    = 0.15f; // < 15% de la pop initiale
    public static final float CRITICAL_RATIO_ORGANISM = 0.10f;
    public static final float CRITICAL_RATIO_NUTRIENT = 0.05f;
    // ─── Limites des paramètres adaptatifs ───────────────────────────────────
    public static final float PHOTO_BONUS_MIN    = 0.0f;
    public static final float PHOTO_BONUS_MAX    = 0.5f;
    public static final float META_PENALTY_MIN   = 0.0f;
    public static final float META_PENALTY_MAX   = 0.3f;
    public static final float NUTRIENT_RATE_MIN  = 0.0f;
    public static final float NUTRIENT_RATE_MAX  = 0.05f;
    public static final float REPRO_BONUS_MIN    = 0.0f;
    public static final float REPRO_BONUS_MAX    = 0.02f;
    // ─── Fichier de persistance ───────────────────────────────────────────────
    public static final Path CONFIG_PATH = Path.of(
            System.getProperty("user.home"), ".pixellife", "ecosystem_params.json");

    // Ajout des constantes pour les valeurs flottantes spécifiques
    public static final float ZERO_DOT_9_FLOAT  = 0.9f;
    public static final float ZERO_DOT_ONE_FLOAT  = 0.1f;
    public static final float FIVE_HUNDRED_MS   = 500.0f;
}
