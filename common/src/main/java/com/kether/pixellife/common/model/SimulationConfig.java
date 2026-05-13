package com.kether.pixellife.common.model;

/**
 * Configuration immuable d'une simulation.
 *
 * <p>Deux niveaux de configuration :</p>
 * <ul>
 *   <li><b>Structurel</b> — grille, populations initiales, durée, graine, taux de mutation.</li>
 *   <li><b>Biologique</b> — {@link BiologicalConfig} : règles biologiques ajustables à chaud
 *       (énergie, métabolisme, vitesse, etc.). {@code null} = utilisation des défauts.</li>
 * </ul>
 *
 * <p>{@code maxSteps = 0} → simulation infinie.</p>
 *
 * @see BiologicalConfig
 * @see SimulationPreset
 */
public record SimulationConfig(
        int              width,
        int              height,
        int              plantCount,
        int              nutrientCount,
        int              organismCount,
        int              maxSteps,
        long             seed,
        float            mutationRate,
        BiologicalConfig biologicalConfig   // null → BiologicalConfig.defaults()
) {
    public SimulationConfig {
        if (width         <= 0)                    throw new IllegalArgumentException("Dimensions invalides");
        if (height        <= 0)                    throw new IllegalArgumentException("Dimensions invalides");
        if (plantCount    <  0)                    throw new IllegalArgumentException("plantCount < 0");
        if (nutrientCount <  0)                    throw new IllegalArgumentException("nutrientCount < 0");
        if (organismCount <  0)                    throw new IllegalArgumentException("organismCount < 0");
        if (maxSteps      <  0)                    throw new IllegalArgumentException("maxSteps < 0");
        if (mutationRate  < 0 || mutationRate > 1) throw new IllegalArgumentException("mutationRate hors [0,1]");
    }

    // ─── Constructeurs de compatibilité ───────────────────────────────────────

    /** Constructeur sans biologicalConfig (utilise les défauts). */
    public SimulationConfig(int width, int height, int plantCount, int nutrientCount,
                            int organismCount, int maxSteps, long seed, float mutationRate) {
        this(width, height, plantCount, nutrientCount, organismCount, maxSteps, seed, mutationRate, null);
    }

    /** Constructeur sans biologicalConfig ni mutationRate. */
    public SimulationConfig(int width, int height, int plantCount, int nutrientCount,
                            int organismCount, int maxSteps, long seed) {
        this(width, height, plantCount, nutrientCount, organismCount, maxSteps, seed, 0.05f, null);
    }

    // ─── API ──────────────────────────────────────────────────────────────────

    /**
     * Retourne la configuration biologique effective.
     * Si {@code biologicalConfig} est {@code null} (simulations existantes, appel sans config),
     * retourne {@link BiologicalConfig#defaults()}.
     */
    public BiologicalConfig effectiveBioConfig() {
        return biologicalConfig != null ? biologicalConfig : BiologicalConfig.defaults();
    }

    /** Simulation infinie si maxSteps == 0. */
    public boolean isInfinite() { return maxSteps == 0; }

    public int totalCells() { return width * height; }

    // Accesseurs de compatibilité (ratios calculés à la demande)
    public float plantRatio()    { return (float) plantCount    / Math.max(totalCells(), 1); }
    public float nutrientRatio() { return (float) nutrientCount / Math.max(totalCells(), 1); }
    public float organismRatio() { return (float) organismCount / Math.max(totalCells(), 1); }

    // ─── Factories ────────────────────────────────────────────────────────────

    /** Configuration équilibrée par défaut — cycle stable plantes ↔ organismes. */
    public static SimulationConfig defaults() {
        return new SimulationConfig(80, 80, 600, 300, 40, 0,
                System.currentTimeMillis(), 0.05f, null);
    }

    /** Migration depuis l'ancienne API à ratios. */
    public static SimulationConfig fromRatios(int width, int height,
                                              float plantRatio, float nutrientRatio, float organismRatio,
                                              int maxSteps, long seed) {
        int total = width * height;
        return new SimulationConfig(
                width, height,
                (int)(total * plantRatio), (int)(total * nutrientRatio), (int)(total * organismRatio),
                maxSteps, seed, 0.05f, null);
    }
}