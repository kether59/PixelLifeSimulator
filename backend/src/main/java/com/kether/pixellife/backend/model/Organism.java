package com.kether.pixellife.backend.model;

import com.kether.pixellife.backend.constant.GameConstants;
import com.kether.pixellife.backend.engine.SimulationContext;
import com.kether.pixellife.common.event.SimulationEvent;
import com.kether.pixellife.common.model.BiologicalConfig;
import com.kether.pixellife.common.model.DNA;
import com.kether.pixellife.common.model.Gender;
import com.kether.pixellife.common.model.Position;
import lombok.Getter;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Organisme vivant — v2.
 *
 * Ajout v2 : pénalité de disette alimentaire.
 * Quand un organisme est en état de faim (énergie < 60% du max) et qu'aucune
 * source de nourriture n'est visible dans son rayon de vision, il subit une
 * pénalité supplémentaire ({@link GameConstants#ORGANISM_FOOD_SCARCITY_PENALTY}).
 * Cela rend les plantes indispensables aux herbivores/omnivores et favorise
 * un équilibre prédateur/proie plus réaliste.
 */
@Getter
public final class Organism extends Entity {

    private static final RandomGenerator RNG = RandomGeneratorFactory.getDefault().create();

    private final DNA    dna;
    private final Gender gender;
    private final int    generation;

    private int   reproductionCooldown = 0;
    private int   mergeCooldown        = 0;
    private int   age                  = 0;
    private float targetX, targetY;

    public Organism(Position position, float energy, Gender gender, int generation, DNA dna) {
        super(position, energy);
        this.gender     = gender;
        this.generation = generation;
        this.dna        = dna;
        this.targetX    = getFloatX();
        this.targetY    = getFloatY();
    }

    // ─── Factories ────────────────────────────────────────────────────────────

    public static Organism spawn(Position position, float startEnergy) {
        DNA dna = DNA.defaults().mutate(0.15f, RNG);
        return new Organism(position, startEnergy, Gender.random(), 0, dna);
    }

    public static Organism offspring(Organism a, Organism b, Position position,
                                     float mutationRate, float startEnergy) {
        DNA childDna = DNA.crossover(a.dna, b.dna, RNG).mutate(mutationRate, RNG);
        return new Organism(position, startEnergy * 0.8f, Gender.random(),
                Math.max(a.generation, b.generation) + 1, childDna);
    }

    // ─── Cycle de vie ─────────────────────────────────────────────────────────

    @Override
    public void update(SimulationContext context) {
        BiologicalConfig bio = context.getBiologicalConfig();
        age++;
        if (reproductionCooldown > 0) reproductionCooldown--;
        if (mergeCooldown        > 0) mergeCooldown--;

        if (age >= bio.organismMaxAge()) {
            die();
            context.publishEvent(new SimulationEvent.EntityDied(id, position, "old_age", Instant.now()));
            return;
        }

        move(context);
        eat(context, bio);
        tryMerge(context, bio);
        reproduce(context, bio);
        payEnergyCosts(context, bio);

        if (isDead()) {
            context.publishEvent(new SimulationEvent.EntityDied(
                    id, position, energy <= 0 ? "starvation" : "age", Instant.now()));
        }
    }

    // ─── Déplacement ──────────────────────────────────────────────────────────

    private void move(SimulationContext context) {
        int steps = Math.max(1, Math.round(dna.speed()));
        for (int i = 0; i < steps; i++) {
            Position target = chooseMoveTarget(context);
            if (target != null && target.isWithinBounds(context.getWidth(), context.getHeight())) {
                Position from = position;
                position = target;
                targetX  = position.x() + 0.5f;
                targetY  = position.y() + 0.5f;
                floatZ   = position.z();
                floatX   = targetX;
                floatY   = targetY;
                context.updateEntityPosition(this, from);
            }
        }
    }

    private Position chooseMoveTarget(SimulationContext context) {
        int vision = Math.max(1, (int) dna.visionRadius());
        List<Entity> nearby = context.getEntitiesInRadius(position, vision);

        if (dna.aggression() > GameConstants.ORGANISM_AGGRESSION_HUNT_THRESHOLD) {
            var prey = nearby.stream()
                    .filter(e -> e instanceof Organism o && o != this && !o.isDead() && o.energy < energy)
                    .min(Comparator.comparingDouble(e -> e.getPosition().distanceTo2D(position)));
            if (prey.isPresent()) return stepToward(position, prey.get().getPosition());
        }

        var nutrient = nearby.stream()
                .filter(e -> e instanceof Nutrient && !e.isDead())
                .min(Comparator.comparingDouble(e -> e.getPosition().distanceTo2D(position)));
        if (nutrient.isPresent()) return stepToward(position, nutrient.get().getPosition());

        var plant = nearby.stream()
                .filter(e -> e instanceof Plant p && !p.isDead() && canHuntPlant(p))
                .min(Comparator.comparingDouble(e -> e.getPosition().distanceTo2D(position)));
        if (plant.isPresent()) return stepToward(position, plant.get().getPosition());

        return randomNeighbor3D(context);
    }

    private boolean canHuntPlant(Plant plant) {
        if (energy < GameConstants.ORGANISM_HUNGER_CRITICAL_THRESHOLD) return true;
        float maturity = plant.getHeight() / GameConstants.PLANT_MAX_HEIGHT;
        return switch (dna.diet()) {
            case 1  -> maturity < GameConstants.ORGANISM_PLANT_MATURITY_HERBIVORE;
            default -> maturity < GameConstants.ORGANISM_PLANT_MATURITY_DEFAULT;
        };
    }

    private Position stepToward(Position from, Position to) {
        int dx = Integer.signum(to.gridX() - from.gridX());
        int dy = Integer.signum(to.gridY() - from.gridY());
        if (Math.abs(to.x() - from.x()) >= Math.abs(to.y() - from.y())) {
            return new Position(from.x() + dx, from.y(), from.z());
        } else {
            return new Position(from.x(), from.y() + dy, from.z());
        }
    }

    private Position randomNeighbor3D(SimulationContext context) {
        int[] dx = {-1, 0, 1, 0};
        int[] dy = { 0,-1, 0, 1};
        int dir  = RNG.nextInt(4);
        if (RNG.nextFloat() < 0.65f) {
            float candidate = floatZ + RNG.nextFloat() * 0.4f - 0.2f;
            if (candidate >= 0 && candidate < context.getDepth()) floatZ = candidate;
        }
        try {
            return new Position(position.gridX() + dx[dir], position.gridY() + dy[dir], floatZ);
        } catch (IllegalArgumentException e) {
            return position;
        }
    }

    // ─── Alimentation ─────────────────────────────────────────────────────────

    private void eat(SimulationContext context, BiologicalConfig bio) {
        boolean ateNutrient = false;
        boolean atePlant    = false;

        for (Entity e : context.getEntitiesAt(position)) {
            if (!ateNutrient && e instanceof Nutrient nutrient && !nutrient.isDead()) {
                float gained = nutrient.getRichness();
                energy = Math.min(energy + gained, bio.organismEnergyMax());
                nutrient.die();
                context.scheduleRemoval(nutrient);
                context.publishEvent(new SimulationEvent.EntityAte(id, nutrient.getId(), gained, Instant.now()));
                ateNutrient = true;
            }

            if (!atePlant && !ateNutrient && e instanceof Plant plant && !plant.isDead()) {
                boolean canEat = switch (dna.diet()) {
                    case 2, 3 -> false;
                    default   -> true;
                };
                if (canEat) {
                    float bite   = plant.getBiteEnergyForThis(bio);
                    float gained = Math.min(bite, plant.getEnergy());
                    plant.consumeEnergy(bite);
                    energy = Math.min(energy + gained * GameConstants.ORGANISM_PLANT_BITE_EFFICIENCY,
                            bio.organismEnergyMax());
                    if (plant.isDead()) context.scheduleRemoval(plant);
                    context.publishEvent(new SimulationEvent.EntityAte(id, plant.getId(), gained, Instant.now()));
                    atePlant = true;
                }
            }

            if (e instanceof Organism prey && prey != this && !prey.isDead()
                    && dna.aggression() > GameConstants.ORGANISM_AGGRESSION_STEAL_THRESHOLD
                    && prey.energy < energy * GameConstants.ORGANISM_PREY_ENERGY_RATIO) {
                boolean canSteal = switch (dna.diet()) {
                    case 1  -> false;
                    default -> true;
                };
                if (canSteal) {
                    float stolen = prey.energy * GameConstants.ORGANISM_STEAL_FRACTION;
                    prey.consumeEnergy(stolen);
                    energy = Math.min(energy + stolen * GameConstants.ORGANISM_STEAL_EFFICIENCY,
                            bio.organismEnergyMax());
                    return;
                }
            }
        }
    }

    // ─── Fusion ───────────────────────────────────────────────────────────────

    private void tryMerge(SimulationContext context, BiologicalConfig bio) {
        if (mergeCooldown > 0 || energy < GameConstants.ORGANISM_MERGE_MIN_ENERGY || isDead()) return;

        context.getEntitiesInRadius(position, 1).stream()
                .filter(e -> e instanceof Organism other
                        && other != this
                        && !other.isDead()
                        && other.mergeCooldown == 0
                        && other.energy >= GameConstants.ORGANISM_MERGE_MIN_ENERGY
                        && dna.distance(other.dna) < bio.organismMergeThreshold()
                        && energy + other.energy <= GameConstants.ORGANISM_MAX_MERGED_ENERGY)
                .findFirst()
                .map(e -> (Organism) e)
                .ifPresent(partner -> context.findFreePositionNear(position, 1).ifPresent(pos -> {
                    float mergedEnergy = Math.min(energy + partner.energy, GameConstants.ORGANISM_MAX_MERGED_ENERGY);
                    Organism merged = new Organism(pos, mergedEnergy, Gender.random(),
                            Math.max(generation, partner.generation) + 1,
                            DNA.merge(dna, partner.dna));
                    context.spawnOrganism(merged);
                    context.publishEvent(new SimulationEvent.EntityMerged(
                            id, partner.id, merged.getId(), pos, Instant.now()));
                    this.die();    context.scheduleRemoval(this);
                    partner.die(); context.scheduleRemoval(partner);
                }));
    }

    // ─── Reproduction ─────────────────────────────────────────────────────────

    private void reproduce(SimulationContext context, BiologicalConfig bio) {
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
                    Organism child = Organism.offspring(
                            this, partner, birthPos, context.getMutationRate(), bio.organismEnergyStart());
                    context.spawnOrganism(child);
                    consumeEnergy(bio.organismReproCost());
                    partner.consumeEnergy(bio.organismReproCost());
                    reproductionCooldown        = bio.organismReproCooldown();
                    partner.reproductionCooldown = bio.organismReproCooldown();
                    context.publishEvent(new SimulationEvent.EntityReproduced(
                            id, partner.id, child.getId(), birthPos, Instant.now()));
                }));
    }

    // ─── Dépenses énergétiques ────────────────────────────────────────────────

    private void payEnergyCosts(SimulationContext context, BiologicalConfig bio) {
        // Métabolisme + pénalité adaptative du régulateur
        consumeEnergy(dna.metabolism() + context.getOrganismMetabolismPenalty());

        // Coût de la taille
        consumeEnergy((dna.size() - GameConstants.ORGANISM_SIZE_COST_BASE)
                * GameConstants.ORGANISM_SIZE_COST_FACTOR);

        // Pénalité de solitude
        boolean hasNeighbour = context.getEntitiesInRadius(position, 3).stream()
                .anyMatch(e -> e instanceof Organism o && o != this && !o.isDead());
        if (!hasNeighbour) consumeEnergy(GameConstants.ORGANISM_LONE_PENALTY);

        // Vieillissement quadratique
        if (age > bio.organismAgePenaltyStart()) {
            float ageRatio = (float)(age - bio.organismAgePenaltyStart()) / bio.organismAgePenaltyStart();
            consumeEnergy(GameConstants.ORGANISM_AGE_PENALTY_FACTOR * ageRatio * ageRatio);
        }

        // ── Pénalité de disette ────────────────────────────────────────────────
        // Active si : énergie faible ET aucune nourriture en vue
        applyFoodScarcityPenalty(context, bio);
    }

    /**
     * Pénalité énergétique supplémentaire quand l'organisme est en état de faim
     * et qu'aucune source de nourriture appropriée n'est visible.
     *
     * <p>Réalisme : un animal qui cherche de la nourriture sans en trouver
     * brûle plus d'énergie que s'il était au repos.</p>
     *
     * <p>Règles selon le régime alimentaire :
     * <ul>
     *   <li>Omnivore (0) / Herbivore (1) : nourriture = nutriments OU plantes</li>
     *   <li>Carnivore (2) : nourriture = nutriments OU organismes plus faibles (si agressif)</li>
     *   <li>Cannibal (3) : nourriture = nutriments OU tout organisme plus faible</li>
     * </ul></p>
     */
    private void applyFoodScarcityPenalty(SimulationContext context, BiologicalConfig bio) {
        // Seuil de faim non atteint → pas de pénalité
        if (energy >= bio.organismEnergyMax() * GameConstants.ORGANISM_FOOD_SCARCITY_THRESHOLD) return;

        int   vision      = Math.max(2, (int) dna.visionRadius());
        int   dietVal     = dna.diet();
        boolean needsPlant  = (dietVal == 0 || dietVal == 1); // omnivore / herbivore
        boolean needsAnimal = (dietVal >= 2);                  // carnivore / cannibal
        boolean canAggress  = dna.aggression() > GameConstants.ORGANISM_AGGRESSION_HUNT_THRESHOLD;

        boolean hasFood = context.getEntitiesInRadius(position, vision).stream()
                .anyMatch(e -> {
                    if (e.isDead()) return false;
                    if (e instanceof Nutrient)                        return true;
                    if (e instanceof Plant && needsPlant)             return true;
                    if (e instanceof Organism prey && prey != this) {
                        if (needsAnimal && prey.energy < energy)      return true;
                        if (canAggress  && prey.energy < energy)      return true;
                    }
                    return false;
                });

        if (!hasFood) {
            consumeEnergy(GameConstants.ORGANISM_FOOD_SCARCITY_PENALTY);
        }
    }

    // ─── Accesseurs ───────────────────────────────────────────────────────────

    public float getSpeed()        { return dna.speed(); }
    public float getVisionRadius() { return dna.visionRadius(); }
    public float getMetabolism()   { return dna.metabolism(); }
    public float getRenderRadius() { return dna.size(); }
    public float getFloatZ()       { return floatZ; }

    @Override public String typeName() { return "ORGANISM"; }

    @Override public String toString() {
        return "Organism{id=%d, gen=%d, gender=%s, energy=%.1f, age=%d, z=%.2f}"
                .formatted(id, generation, gender, energy, age, floatZ);
    }
}