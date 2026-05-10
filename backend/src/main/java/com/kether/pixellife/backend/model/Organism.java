package com.kether.pixellife.backend.model;

import com.kether.pixellife.common.model.DNA;
import com.kether.pixellife.common.model.Gender;
import com.kether.pixellife.common.model.Position;
import com.kether.pixellife.backend.engine.SimulationContext;
import com.kether.pixellife.common.event.SimulationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Organisme vivant — v3.
 *
 * Rééquilibrage du cycle de vie :
 *  - ENERGY_MAX réduite à 120 (était 200) → moins de "réserve tampon"
 *  - Énergie de départ réduite à 30 (était 50)
 *  - Métabolisme de base du génome plus élevé par défaut (0.8 au lieu de 0.5)
 *  - Coût de reproduction augmenté : 30 énergie (était 20)
 *  - Seuil de reproduction relevé à 70 énergie (était 60)
 *  - Cooldown de reproduction allongé : 50 ticks (était 30)
 *  - Broutage des plantes : l'organisme ne prend que BITE_ENERGY par tick
 *    (la plante ne meurt pas d'un seul coup)
 *  - Vieillissement plus agressif après 150 ticks (était 200)
 *  - Déplacement 3D : z peut varier légèrement (organismes "nageurs/grimpeurs")
 *
 * DNA intégré : tous les traits phénotypiques passent par le génome.
 */
@Getter
public final class Organism extends Entity {

    private static final RandomGenerator RNG =
            RandomGeneratorFactory.getDefault().create();

    // ─── Constantes biologiques ───────────────────────────────────────────────
    private static final float ENERGY_MAX           = 120f;  // réduit de 200 → 120
    public static final float ENERGY_START         = 30f;   // réduit de 50 → 30
    private static final float REPRO_COST           = 30f;   // augmenté de 20 → 30
    private static final float REPRO_COOLDOWN_TICKS = 50;    // augmenté de 30 → 50
    private static final float AGE_PENALTY_START    = 150;   // réduit de 200 → 150
    private static final float MERGE_THRESHOLD      = 0.35f;
    private static final float MAX_MERGED_ENERGY    = 100f;
    private static final float DEFAULT_MUTATION_RATE = 0.05f;

    // ─── Identité génétique ───────────────────────────────────────────────────
    private final DNA    dna;
    private final Gender gender;
    private final int    generation;

    // ─── État ────────────────────────────────────────────────────────────────
    private int reproductionCooldown = 0;
    private int mergeCooldown        = 0;
    private int age                  = 0;
    private float targetX, targetY;

    public Organism(Position position, float energy, Gender gender, int generation, DNA dna) {
        super(position, energy);
        this.gender     = gender;
        this.generation = generation;
        this.dna        = dna;
        this.targetX = getFloatX();
        this.targetY = getFloatY();
    }

    // ─── Factories ────────────────────────────────────────────────────────────

    public static Organism spawn(Position position) {
        DNA dna = DNA.defaults().mutate(0.15f, RNG);
        return new Organism(position, ENERGY_START, Gender.random(), 0, dna);
    }

    public static Organism offspring(Organism a, Organism b, Position position, float mutationRate) {
        DNA childDna = DNA.crossover(a.dna, b.dna, RNG).mutate(mutationRate, RNG);
        return new Organism(position, ENERGY_START * 0.8f, Gender.random(),
                Math.max(a.generation, b.generation) + 1, childDna);
    }

    public static Organism offspring(Organism a, Organism b, Position position) {
        return offspring(a, b, position, DEFAULT_MUTATION_RATE);
    }

    // ─── Cycle de vie ─────────────────────────────────────────────────────────

    @Override
    public void update(SimulationContext context) {
        age++;
        if (reproductionCooldown > 0) reproductionCooldown--;
        if (mergeCooldown > 0) mergeCooldown--;

        move(context);
        eat(context);
        tryMerge(context);
        reproduce(context);

        // Coût métabolique de base (génome) + pénalité adaptative du régulateur
        // La pénalité augmente doucement si les organismes surpeuplent l'écosystème
        consumeEnergy(dna.metabolism() + context.getOrganismMetabolismPenalty());

        // Coût de la taille (les grands organismes coûtent plus cher)
        consumeEnergy((dna.size() - 0.15f) * 0.08f);

        // Vieillissement accéléré après AGE_PENALTY_START ticks
        if (age > AGE_PENALTY_START) {
            consumeEnergy(0.15f * (age - AGE_PENALTY_START) / AGE_PENALTY_START);
        }

        if (isDead()) {
            context.publishEvent(new SimulationEvent.EntityDied(
                    id, position, energy <= 0 ? "starvation" : "age", Instant.now()));
        }
    }

    // ─── Déplacement ─────────────────────────────────────────────────────────

    private void move(SimulationContext context) {
        int steps = Math.max(1, Math.round(dna.speed()));
        for (int i = 0; i < steps; i++) {
            Position target = chooseMoveTarget(context);
            if (target != null && target.isWithinBounds(context.getWidth(), context.getHeight())) {
                Position from = position;
                position = target;
                this.targetX = position.x() + 0.5f;
                this.targetY = position.y() + 0.5f;
                // Synchronise la position flottante héritée d'Entity,
                // lue par le renderer via getFloatX() / getFloatY()
                this.floatX  = this.targetX;
                this.floatY  = this.targetY;
                context.updateEntityPosition(this, from);
            }
        }
    }

    private Position chooseMoveTarget(SimulationContext context) {
        int vision = Math.max(1, (int) dna.visionRadius());
        List<Entity> nearby = context.getEntitiesInRadius(position, vision);

        // Comportement agressif : cherche une proie faible
        if (dna.aggression() > 0.6f) {
            var prey = nearby.stream()
                    .filter(e -> e instanceof Organism o && o != this && !o.isDead() && o.energy < energy)
                    .min(Comparator.comparingDouble(e -> e.getPosition().distanceTo2D(position)));
            if (prey.isPresent()) return stepToward(position, prey.get().getPosition());
        }

        // Cherche nourriture : privilégie les nutriments, puis les plantes si pas de nutriments
        var nutrient = nearby.stream()
                .filter(e -> e instanceof Nutrient && !e.isDead())
                .min(Comparator.comparingDouble(e -> e.getPosition().distanceTo2D(position)));

        if (nutrient.isPresent()) {
            return stepToward(position, nutrient.get().getPosition());
        }

        // Si pas de nutriments proches, cherche des plantes
        var plant = nearby.stream()
                .filter(e -> e instanceof Plant && !e.isDead())
                .min(Comparator.comparingDouble(e -> e.getPosition().distanceTo2D(position)));

        if (plant.isPresent()) {
            return stepToward(position, plant.get().getPosition());
        }

        // Déplacement aléatoire avec légère variation Z
        return randomNeighbor3D(context);
    }

    private Position stepToward(Position from, Position to) {
        int dx = Integer.signum(to.x() - from.x());
        int dy = Integer.signum(to.y() - from.y());
        if (Math.abs(to.x() - from.x()) >= Math.abs(to.y() - from.y())) {
            return new Position(from.x() + dx, from.y(), from.z());
        } else {
            return new Position(from.x(), from.y() + dy, from.z());
        }
    }

    /**
     * Déplacement aléatoire en 3D — z varie d'un cran avec faible probabilité.
     * Simule des organismes qui nagent/grimpent légèrement.
     */
    private Position randomNeighbor3D(SimulationContext context) {
        int[] dx = {-1, 0, 1, 0};
        int[] dy = {0, -1, 0, 1};
        int dir = RNG.nextInt(4);

        int nx = position.x() + dx[dir];
        int ny = position.y() + dy[dir];

        // Variation verticale : 10 % de chance de monter ou descendre d'un cran
        int nz = position.z();
        if (RNG.nextFloat() < 0.65f) {
            int dz = RNG.nextBoolean() ? 1 : -1;
            int candidate = nz + dz;
            int maxZ = context.getDepth();
            if (candidate >= 0 && candidate < maxZ) nz = candidate;
        }

        try {
            return new Position(nx, ny, nz);
        } catch (IllegalArgumentException e) {
            return position;
        }
    }

    // ─── Alimentation — BROUTAGE ──────────────────────────────────────────────

    /**
     * v3 — Broutage : l'organisme prend BITE_ENERGY par tick sur une plante
     * sans la tuer instantanément. La plante ne meurt que si son énergie tombe à 0.
     * Cela crée un cycle de prédation plus réaliste et évite l'effondrement rapide.
     */
    private void eat(SimulationContext context) {
        // Priorité aux nutriments, plantes en dernier recours
        boolean ateNutrient = false;
        boolean atePlant = false;

        for (Entity e : context.getEntitiesAt(position)) {
            if (e instanceof Nutrient nutrient && !nutrient.isDead() && !ateNutrient) {
                float gained = nutrient.getRichness();
                energy = Math.min(energy + gained, ENERGY_MAX);
                nutrient.die();
                context.scheduleRemoval(nutrient);
                context.publishEvent(new SimulationEvent.EntityAte(
                        id, nutrient.getId(), gained, Instant.now()));
                ateNutrient = true;
            }

            if (e instanceof Plant plant && !plant.isDead() && !atePlant && !ateNutrient) {
                float bite   = plant.getBiteEnergyForThis();
                float gained = Math.min(bite, plant.getEnergy());
                plant.consumeEnergy(bite);
                energy = Math.min(energy + gained * 0.8f, ENERGY_MAX);
                if (plant.isDead()) context.scheduleRemoval(plant);
                context.publishEvent(new SimulationEvent.EntityAte(
                        id, plant.getId(), gained, Instant.now()));
                atePlant = true;
            }

            // Agression : vol d'énergie sur un organisme affaibli
            if (dna.aggression() > 0.7f
                    && e instanceof Organism prey
                    && prey != this
                    && !prey.isDead()
                    && prey.energy < energy * 0.5f) {
                float stolen = prey.energy * 0.25f;
                prey.consumeEnergy(stolen);
                energy = Math.min(energy + stolen * 0.6f, ENERGY_MAX);
                return;
            }
        }
    }

    // ─── Fusion ───────────────────────────────────────────────────────────────

    private void tryMerge(SimulationContext context) {
        if (mergeCooldown > 0 || energy < 40f || isDead()) return;

        context.getEntitiesInRadius(position, 1).stream()
                .filter(e -> e instanceof Organism other
                        && other != this
                        && !other.isDead()
                        && other.mergeCooldown == 0
                        && other.energy >= 40f
                        && dna.distance(other.dna) < MERGE_THRESHOLD
                        && energy + other.energy <= MAX_MERGED_ENERGY)
                .findFirst()
                .map(e -> (Organism) e)
                .ifPresent(partner -> {
                    DNA mergedDNA     = DNA.merge(dna, partner.dna);
                    float mergedEnergy = Math.min(energy + partner.energy, MAX_MERGED_ENERGY);
                    context.findFreePositionNear(position, 1).ifPresent(pos -> {
                        Organism merged = new Organism(pos, mergedEnergy, Gender.random(),
                                Math.max(generation, partner.generation) + 1, mergedDNA);
                        context.spawnOrganism(merged);
                        context.publishEvent(new SimulationEvent.EntityMerged(
                                id, partner.id, merged.getId(), pos, Instant.now()));
                        this.die();
                        context.scheduleRemoval(this);
                        partner.die();
                        context.scheduleRemoval(partner);
                    });
                });
    }

    // ─── Reproduction ─────────────────────────────────────────────────────────

    private void reproduce(SimulationContext context) {
        if (reproductionCooldown > 0 || energy < dna.reproEnergy()) return;

        context.getEntitiesInRadius(position, (int) dna.visionRadius()).stream()
                .filter(e -> e instanceof Organism other
                        && other.gender != this.gender
                        && !other.isDead()
                        && other.reproductionCooldown == 0
                        && other.energy >= other.dna.reproEnergy())
                .map(e -> (Organism) e)
                .findFirst()
                .ifPresent(partner -> context.findFreePositionNear(position, 2).ifPresent(birthPos -> {
                    Organism child = Organism.offspring(this, partner, birthPos, context.getMutationRate());
                    context.spawnOrganism(child);
                    consumeEnergy(REPRO_COST);
                    partner.consumeEnergy(REPRO_COST);
                    reproductionCooldown = (int) REPRO_COOLDOWN_TICKS;
                    partner.reproductionCooldown = (int) REPRO_COOLDOWN_TICKS;
                    context.publishEvent(new SimulationEvent.EntityReproduced(
                            id, partner.id, child.getId(), birthPos, Instant.now()));
                }));
    }

    // ─── Accesseurs ───────────────────────────────────────────────────────────

    public float getSpeed()        { return dna.speed(); }
    public float getVisionRadius() { return dna.visionRadius(); }
    public float getMetabolism()   { return dna.metabolism(); }
    public float getRenderRadius() { return dna.size(); }

    @Override public String typeName() { return "ORGANISM"; }

    @Override public String toString() {
        return "Organism{id=%d, gen=%d, gender=%s, energy=%.1f, age=%d, z=%d}"
                .formatted(id, generation, gender, energy, age, position.z());
    }
}