package com.kether.pixellife.common.model;

/**
 * Configuration immuable d'une simulation — v3.
 *
 * Changements :
 *  - plantRatio/nutrientRatio/organismRatio → plantCount/nutrientCount/organismCount (valeurs absolues)
 *  - maxSteps = 0  →  simulation infinie
 *  - mutationRate pour l'évolution génétique
 */
public record SimulationConfig(
        int   width,
        int   height,
        int   plantCount,       // nombre initial de plantes
        int   nutrientCount,    // nombre initial de nutriments
        int   organismCount,    // nombre initial d'organismes
        int   maxSteps,         // 0 = infini
        long  seed,
        float mutationRate      // 0.01 – 0.20, défaut 0.05
) {
    public SimulationConfig {
        if (width        <= 0)                   throw new IllegalArgumentException("Dimensions invalides");
        if (height       <= 0)                   throw new IllegalArgumentException("Dimensions invalides");
        if (plantCount    < 0)                   throw new IllegalArgumentException("plantCount < 0");
        if (nutrientCount < 0)                   throw new IllegalArgumentException("nutrientCount < 0");
        if (organismCount < 0)                   throw new IllegalArgumentException("organismCount < 0");
        if (maxSteps      < 0)                   throw new IllegalArgumentException("maxSteps < 0");
        if (mutationRate  < 0 || mutationRate > 1) throw new IllegalArgumentException("mutationRate hors [0,1]");
    }

    /** Constructeur sans mutationRate — rétro-compatibilité. */
    public SimulationConfig(int width, int height, int plantCount, int nutrientCount,
                            int organismCount, int maxSteps, long seed) {
        this(width, height, plantCount, nutrientCount, organismCount, maxSteps, seed, 0.05f);
    }

    /** Migration depuis l'ancienne API à ratios. */
    public static SimulationConfig fromRatios(int width, int height,
                                              float plantRatio, float nutrientRatio, float organismRatio,
                                              int maxSteps, long seed) {
        int total = width * height;
        return new SimulationConfig(
                width, height,
                (int)(total * plantRatio),
                (int)(total * nutrientRatio),
                (int)(total * organismRatio),
                maxSteps, seed, 0.05f
        );
    }

    /** Config par défaut équilibrée — cycle stable plantes ↔ organismes. */
    public static SimulationConfig defaults() {
        return new SimulationConfig(
                80, 80,
                600,    // plantes
                300,    // nutriments
                40,     // organismes (peu = départ lent mais plus stable)
                0,      // infini
                System.currentTimeMillis(),
                0.05f
        );
    }

    /** true si la simulation tourne sans limite de steps. */
    public boolean isInfinite() { return maxSteps == 0; }

    public int totalCells() { return width * height; }

    // Accesseurs de compatibilité (ratios calculés)
    public float plantRatio()    { return (float) plantCount    / Math.max(totalCells(), 1); }
    public float nutrientRatio() { return (float) nutrientCount / Math.max(totalCells(), 1); }
    public float organismRatio() { return (float) organismCount / Math.max(totalCells(), 1); }
}