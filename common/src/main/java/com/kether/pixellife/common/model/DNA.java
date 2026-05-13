package com.kether.pixellife.common.model;

import java.util.random.RandomGenerator;

/**
 * Génome d'un organisme — 8 gènes déterminant physiologie et comportement.
 *
 * <h3>Gènes</h3>
 * <table>
 *   <tr><th>Gène</th><th>Plage</th><th>Rôle</th></tr>
 *   <tr><td>speed</td><td>0.2 – 8.0</td><td>Pas par tick</td></tr>
 *   <tr><td>visionRadius</td><td>1 – 30</td><td>Rayon de détection</td></tr>
 *   <tr><td>metabolism</td><td>0.3 – 5.0</td><td>Coût énergie/tick — gène le plus coûteux</td></tr>
 *   <tr><td>aggression</td><td>0.0 – 1.0</td><td>Seuil du comportement agressif</td></tr>
 *   <tr><td>socialRadius</td><td>0 – 20</td><td>Rayon social (réservé)</td></tr>
 *   <tr><td>reproEnergy</td><td>40 – 110</td><td>Énergie min pour se reproduire</td></tr>
 *   <tr><td>size</td><td>0.15 – 0.48</td><td>Taille physique + coût de maintenance</td></tr>
 *   <tr><td>diet</td><td>0–3</td><td>0=omnivore, 1=herbivore, 2=carnivore, 3=cannibal</td></tr>
 * </table>
 */
public record DNA(
        float speed,
        float visionRadius,
        float metabolism,
        float aggression,
        float socialRadius,
        float reproEnergy,
        float size,
        int   diet
) {
    private static final float SPEED_MIN = 0.2f,  SPEED_MAX = 8.0f;
    private static final float VIS_MIN   = 1.0f,  VIS_MAX   = 30.0f;
    private static final float META_MIN  = 0.3f,  META_MAX  = 5.0f;
    private static final float AGGR_MIN  = 0.0f,  AGGR_MAX  = 1.0f;
    private static final float SOC_MIN   = 0.0f,  SOC_MAX   = 20.0f;
    private static final float REPR_MIN  = 40.0f, REPR_MAX  = 110.0f;
    private static final float SIZE_MIN  = 0.15f, SIZE_MAX  = 0.48f;

    /** Génome par défaut équilibré. */
    public static DNA defaults() {
        return new DNA(1.0f, 5.0f, 0.8f, 0.1f, 3.0f, 70.0f, 0.30f, 0);
    }

    public static DNA random(RandomGenerator rng) {
        return new DNA(
                lerp(SPEED_MIN, SPEED_MAX, rng.nextFloat()),
                lerp(VIS_MIN,   VIS_MAX,   rng.nextFloat()),
                lerp(META_MIN,  META_MAX,  rng.nextFloat()),
                lerp(AGGR_MIN,  AGGR_MAX,  rng.nextFloat()),
                lerp(SOC_MIN,   SOC_MAX,   rng.nextFloat()),
                lerp(REPR_MIN,  REPR_MAX,  rng.nextFloat()),
                lerp(SIZE_MIN,  SIZE_MAX,  rng.nextFloat()),
                rng.nextInt(4)
        );
    }

    public static DNA crossover(DNA a, DNA b, RandomGenerator rng) {
        return new DNA(
                rng.nextBoolean() ? a.speed        : b.speed,
                rng.nextBoolean() ? a.visionRadius : b.visionRadius,
                rng.nextBoolean() ? a.metabolism   : b.metabolism,
                rng.nextBoolean() ? a.aggression   : b.aggression,
                rng.nextBoolean() ? a.socialRadius : b.socialRadius,
                rng.nextBoolean() ? a.reproEnergy  : b.reproEnergy,
                rng.nextBoolean() ? a.size         : b.size,
                rng.nextBoolean() ? a.diet         : b.diet
        );
    }

    /**
     * Mutation gaussienne de chaque gène flottant proportionnellement à {@code rate}.
     *
     * <p>Le régime alimentaire (diet) mute seulement avec une probabilité {@code rate × 2}
     * — correction d'un bug où il mutait systématiquement à 100 % quel que soit le taux.</p>
     */
    public DNA mutate(float rate, RandomGenerator rng) {
        // Régime : mute avec probabilité proportionnelle au taux (±1 cran circulaire)
        int newDiet = rng.nextFloat() < rate * 2f
                ? (rng.nextBoolean() ? (diet + 1) % 4 : (diet + 3) % 4)
                : diet;

        return new DNA(
                clamp(speed        + (float)rng.nextGaussian() * rate * (SPEED_MAX - SPEED_MIN), SPEED_MIN, SPEED_MAX),
                clamp(visionRadius + (float)rng.nextGaussian() * rate * (VIS_MAX   - VIS_MIN),   VIS_MIN,   VIS_MAX),
                clamp(metabolism   + (float)rng.nextGaussian() * rate * (META_MAX  - META_MIN),  META_MIN,  META_MAX),
                clamp(aggression   + (float)rng.nextGaussian() * rate * (AGGR_MAX  - AGGR_MIN),  AGGR_MIN,  AGGR_MAX),
                clamp(socialRadius + (float)rng.nextGaussian() * rate * (SOC_MAX   - SOC_MIN),   SOC_MIN,   SOC_MAX),
                clamp(reproEnergy  + (float)rng.nextGaussian() * rate * (REPR_MAX  - REPR_MIN),  REPR_MIN,  REPR_MAX),
                clamp(size         + (float)rng.nextGaussian() * rate * (SIZE_MAX  - SIZE_MIN),  SIZE_MIN,  SIZE_MAX),
                newDiet
        );
    }

    public static DNA merge(DNA a, DNA b) {
        return new DNA(
                (a.speed        + b.speed)        / 2f,
                (a.visionRadius + b.visionRadius) / 2f,
                (a.metabolism   + b.metabolism)   / 2f,
                (a.aggression   + b.aggression)   / 2f,
                (a.socialRadius + b.socialRadius) / 2f,
                (a.reproEnergy  + b.reproEnergy)  / 2f,
                clamp(Math.max(a.size, b.size) + 0.05f, SIZE_MIN, SIZE_MAX),
                (a.diet + b.diet) / 2
        );
    }

    /**
     * Distance euclidienne normalisée entre deux génomes.
     * Retourne une valeur dans [0, 1] — utilisée pour la fusion et la spéciation.
     */
    public float distance(DNA other) {
        float ds = (speed        - other.speed)        / (SPEED_MAX - SPEED_MIN);
        float dv = (visionRadius - other.visionRadius) / (VIS_MAX   - VIS_MIN);
        float dm = (metabolism   - other.metabolism)   / (META_MAX  - META_MIN);
        float da = (aggression   - other.aggression)   / (AGGR_MAX  - AGGR_MIN);
        float dc = (socialRadius - other.socialRadius) / (SOC_MAX   - SOC_MIN);
        float dr = (reproEnergy  - other.reproEnergy)  / (REPR_MAX  - REPR_MIN);
        float dz = (size         - other.size)         / (SIZE_MAX  - SIZE_MIN);
        return (float) Math.sqrt(ds*ds + dv*dv + dm*dm + da*da + dc*dc + dr*dr + dz*dz);
    }

    /** Couleur RGB [0,1] dérivée du génome et du sexe, pour le rendu. */
    public float[] toColor(Gender gender) {
        float r = (aggression   - AGGR_MIN) / (AGGR_MAX - AGGR_MIN);
        float g = 1f - (metabolism - META_MIN) / (META_MAX - META_MIN);
        float b = (visionRadius  - VIS_MIN)  / (VIS_MAX  - VIS_MIN);
        float[] color = gender == Gender.FEMALE
                ? new float[]{ 0.55f + r*0.45f, g*0.45f, 0.45f + b*0.55f }
                : new float[]{ r*0.45f, g*0.45f, 0.55f + b*0.45f };

        switch (diet) {
            case 1 -> color[1] = Math.min(1.0f, color[1] + 0.3f); // herbivore : plus vert
            case 2 -> color[0] = Math.min(1.0f, color[0] + 0.3f); // carnivore : plus rouge
            case 3 -> {                                            // cannibal : assombri
                color[0] = Math.max(0.0f, color[0] - 0.2f);
                color[1] = Math.max(0.0f, color[1] - 0.2f);
                color[2] = Math.max(0.0f, color[2] - 0.2f);
            }
        }
        return color;
    }

    public float[] toArray() {
        return new float[]{ speed, visionRadius, metabolism, aggression,
                socialRadius, reproEnergy, size, diet };
    }

    public static DNA fromArray(float[] arr) {
        DNA d = defaults();
        if (arr == null || arr.length == 0) return d;
        return new DNA(
                arr.length > 0 ? arr[0] : d.speed,
                arr.length > 1 ? arr[1] : d.visionRadius,
                arr.length > 2 ? arr[2] : d.metabolism,
                arr.length > 3 ? arr[3] : d.aggression,
                arr.length > 4 ? arr[4] : d.socialRadius,
                arr.length > 5 ? arr[5] : d.reproEnergy,
                arr.length > 6 ? arr[6] : d.size,
                arr.length > 7 ? (int)arr[7] : d.diet
        );
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static float lerp(float min, float max, float t)  { return min + (max - min) * t; }
}