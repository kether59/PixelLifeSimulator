package com.kether.pixellife.common.model;

import java.util.random.RandomGenerator;

/**
 * Génome d'un organisme — v3.
 *
 * Ajustement des valeurs par défaut pour l'équilibre du cycle :
 *  - metabolism default : 0.8 (était 0.5) → organismes plus coûteux à maintenir
 *  - reproEnergy default : 70.0 (était 60.0) → seuil de reproduction plus élevé
 */
public record DNA(
        float speed,
        float visionRadius,
        float metabolism,
        float aggression,
        float socialRadius,
        float reproEnergy,
        float size
) {
    private static final float SPEED_MIN  = 0.2f,  SPEED_MAX  = 8.0f;
    private static final float VIS_MIN    = 1.0f,  VIS_MAX    = 30.0f;
    private static final float META_MIN   = 0.3f,  META_MAX   = 5.0f;   // min relevé : 0.1→0.3
    private static final float AGGR_MIN   = 0.0f,  AGGR_MAX   = 1.0f;
    private static final float SOC_MIN    = 0.0f,  SOC_MAX    = 20.0f;
    private static final float REPR_MIN   = 40.0f, REPR_MAX   = 110.0f; // min relevé : 20→40
    private static final float SIZE_MIN   = 0.15f, SIZE_MAX   = 0.48f;

    /** Génome par défaut équilibré pour v3. */
    public static DNA defaults() {
        return new DNA(1.0f, 5.0f, 0.8f, 0.1f, 3.0f, 70.0f, 0.30f);
    }

    public static DNA random(RandomGenerator rng) {
        return new DNA(
                lerp(SPEED_MIN, SPEED_MAX, rng.nextFloat()),
                lerp(VIS_MIN,   VIS_MAX,   rng.nextFloat()),
                lerp(META_MIN,  META_MAX,  rng.nextFloat()),
                lerp(AGGR_MIN,  AGGR_MAX,  rng.nextFloat()),
                lerp(SOC_MIN,   SOC_MAX,   rng.nextFloat()),
                lerp(REPR_MIN,  REPR_MAX,  rng.nextFloat()),
                lerp(SIZE_MIN,  SIZE_MAX,  rng.nextFloat())
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
                rng.nextBoolean() ? a.size         : b.size
        );
    }

    public DNA mutate(float rate, RandomGenerator rng) {
        return new DNA(
                clamp(speed        + (float)rng.nextGaussian() * rate * (SPEED_MAX - SPEED_MIN), SPEED_MIN, SPEED_MAX),
                clamp(visionRadius + (float)rng.nextGaussian() * rate * (VIS_MAX   - VIS_MIN),   VIS_MIN,   VIS_MAX),
                clamp(metabolism   + (float)rng.nextGaussian() * rate * (META_MAX  - META_MIN),  META_MIN,  META_MAX),
                clamp(aggression   + (float)rng.nextGaussian() * rate * (AGGR_MAX  - AGGR_MIN),  AGGR_MIN,  AGGR_MAX),
                clamp(socialRadius + (float)rng.nextGaussian() * rate * (SOC_MAX   - SOC_MIN),   SOC_MIN,   SOC_MAX),
                clamp(reproEnergy  + (float)rng.nextGaussian() * rate * (REPR_MAX  - REPR_MIN),  REPR_MIN,  REPR_MAX),
                clamp(size         + (float)rng.nextGaussian() * rate * (SIZE_MAX  - SIZE_MIN),  SIZE_MIN,  SIZE_MAX)
        );
    }

    public static DNA merge(DNA a, DNA b) {
        return new DNA(
                (a.speed + b.speed) / 2f,
                (a.visionRadius + b.visionRadius) / 2f,
                (a.metabolism   + b.metabolism)   / 2f,
                (a.aggression   + b.aggression)   / 2f,
                (a.socialRadius + b.socialRadius) / 2f,
                (a.reproEnergy  + b.reproEnergy)  / 2f,
                clamp(Math.max(a.size, b.size) + 0.05f, SIZE_MIN, SIZE_MAX)
        );
    }

    public float distance(DNA other) {
        float ds = (speed        - other.speed)        / (SPEED_MAX - SPEED_MIN);
        float dv = (visionRadius - other.visionRadius) / (VIS_MAX   - VIS_MIN);
        float dm = (metabolism   - other.metabolism)   / (META_MAX  - META_MIN);
        float da = (aggression   - other.aggression)   / (AGGR_MAX  - AGGR_MIN);
        float dc = (socialRadius - other.socialRadius) / (SOC_MAX   - SOC_MIN);
        float dr = (reproEnergy  - other.reproEnergy)  / (REPR_MAX  - REPR_MIN);
        float dz = (size         - other.size)         / (SIZE_MAX  - SIZE_MIN);
        return (float)Math.sqrt(ds*ds + dv*dv + dm*dm + da*da + dc*dc + dr*dr + dz*dz);
    }

    public float[] toColor(Gender gender) {
        float r = (aggression   - AGGR_MIN) / (AGGR_MAX - AGGR_MIN);
        float g = 1f - (metabolism - META_MIN) / (META_MAX - META_MIN);
        float b = (visionRadius  - VIS_MIN)  / (VIS_MAX  - VIS_MIN);
        return gender == Gender.FEMALE
                ? new float[]{ 0.55f + r*0.45f, g*0.45f, 0.45f + b*0.55f }
                : new float[]{ r*0.45f, g*0.45f, 0.55f + b*0.45f };
    }

    public float[] toArray() {
        return new float[]{ speed, visionRadius, metabolism, aggression, socialRadius, reproEnergy, size };
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
                arr.length > 6 ? arr[6] : d.size
        );
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static float lerp(float min, float max, float t)  { return min + (max - min) * t; }
}