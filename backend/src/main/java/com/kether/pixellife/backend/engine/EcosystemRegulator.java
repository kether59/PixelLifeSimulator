package com.kether.pixellife.backend.engine;

import com.kether.pixellife.backend.constant.GameConstants;
import com.kether.pixellife.backend.model.Nutrient;
import com.kether.pixellife.backend.model.Organism;
import com.kether.pixellife.backend.model.Plant;
import com.kether.pixellife.common.model.DNA;
import com.kether.pixellife.common.model.Gender;
import com.kether.pixellife.common.model.Position;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Régulateur homéostatique de l'écosystème.
 *
 * <h3>Principe</h3>
 * Toutes les {@link GameConstants#REGULATION_INTERVAL} steps, le régulateur :
 * <ol>
 *   <li><b>Injecte d'urgence</b> si une population tombe sous un seuil critique.</li>
 *   <li><b>Ajuste dynamiquement</b> les paramètres biologiques globaux via
 *       un P-controller (photosynthèse, métabolisme, spawn de nutriments).</li>
 *   <li><b>Sauvegarde</b> les paramètres appris en JSON pour les sessions suivantes.</li>
 * </ol>
 *
 * <h3>Apprentissage inter-sessions</h3>
 * <p>Les paramètres optimaux convergent au fil des parties.
 * Au rechargement, 70 % de l'ancienne valeur + 30 % des défauts évitent
 * de repartir sur un déséquilibre figé.</p>
 *
 * <p>Supprimer {@code ~/.pixellife/ecosystem_params.json} pour repartir de zéro.</p>
 */
@Slf4j
public class EcosystemRegulator {

    private final Grid grid;
    private final int  initialPlants;
    private final int  initialOrganisms;
    private final int  initialNutrients;
    private final int  depth;

    private static final RandomGenerator RNG = RandomGeneratorFactory.getDefault().create();

    // ─── Paramètres adaptatifs (chargés/sauvegardés) ─────────────────────────

    private volatile float plantPhotosynthesisBonus  = 0.0f;
    private volatile float organismMetabolismPenalty = 0.0f;
    private volatile float nutrientSpawnRate         = 0.005f;
    private volatile float plantReproductionBonus    = 0.002f;

    // ─── Statistiques de session ──────────────────────────────────────────────

    private int   regulationCount              = 0;
    private float avgOrganismRatio             = 1.0f; // EMA du ratio courant/initial

    // ─── Construction ─────────────────────────────────────────────────────────

    public EcosystemRegulator(Grid grid, int initialPlants, int initialOrganisms,
                              int initialNutrients, int depth) {
        this.grid             = grid;
        this.initialPlants    = Math.max(1, initialPlants);
        this.initialOrganisms = Math.max(1, initialOrganisms);
        this.initialNutrients = Math.max(1, initialNutrients);
        this.depth            = depth;
        loadParams();
    }

    // ─── Accesseurs pour les entités ─────────────────────────────────────────

    public float getPlantPhotosynthesisBonus()  { return plantPhotosynthesisBonus; }
    public float getOrganismMetabolismPenalty() { return organismMetabolismPenalty; }

    // ─── Régulation principale ────────────────────────────────────────────────

    public void regulate(long step, GridSimulationContext context) {
        if (step % GameConstants.REGULATION_INTERVAL != 0) return;

        regulationCount++;

        long plants    = countAlive(Plant.class);
        long organisms = countAlive(Organism.class);
        long nutrients = countAlive(Nutrient.class);

        float plantRatio    = (float) plants    / initialPlants;
        float organismRatio = (float) organisms / initialOrganisms;
        float nutrientRatio = (float) nutrients / initialNutrients;

        // EMA du ratio d'organismes (mémoire ≈ 10 intervals de régulation)
        avgOrganismRatio = GameConstants.REGULATION_EMA_COMPLEMENT * avgOrganismRatio
                + GameConstants.REGULATION_EMA_ALPHA      * organismRatio;

        boolean crisis = injectIfCritical(plantRatio, organismRatio, nutrientRatio, context);

        // Spawn spontané de nutriments en arrière-plan
        if (RNG.nextFloat() < nutrientSpawnRate * GameConstants.REGULATION_INTERVAL) {
            spawnRandomNutrient(context);
        }

        adjustAdaptiveParams(plantRatio, nutrientRatio);

        if (step % 1000 == 0) saveParams();
    }

    // ─── Injections d'urgence ─────────────────────────────────────────────────

    private boolean injectIfCritical(float plantRatio, float organismRatio,
                                     float nutrientRatio, GridSimulationContext context) {
        boolean crisis = false;

        if (plantRatio < GameConstants.REGULATION_CRITICAL_PLANT) {
            int count = Math.max(3, (int)(initialPlants * 0.10f));
            injectPlants(count, context);
            log.info("Régulateur : injection {} plantes (ratio={:.2f})", count, plantRatio);
            crisis = true;
        }

        if (organismRatio < GameConstants.REGULATION_CRITICAL_ORGANISM) {
            int count = Math.max(3, (int)(initialOrganisms * 0.10f));
            injectOrganisms(count, context);
            log.info("Régulateur : injection {} organismes (ratio={:.2f})", count, organismRatio);
            crisis = true;
        }

        if (nutrientRatio < GameConstants.REGULATION_CRITICAL_NUTRIENT) {
            int count = Math.max(5, (int)(initialNutrients * 0.15f));
            injectNutrients(count, context);
            log.info("Régulateur : injection {} nutriments (ratio={:.2f})", count, nutrientRatio);
        }

        return crisis;
    }

    private void injectPlants(int count, GridSimulationContext context) {
        for (int i = 0; i < count; i++) {
            Position pos = randomPosition();
            if (grid.isFree(pos)) context.spawnPlantNear(pos);
        }
    }

    private void injectOrganisms(int count, GridSimulationContext context) {
        for (int i = 0; i < count; i++) {
            Position pos = randomPosition();
            if (grid.isFree(pos)) {
                DNA dna = DNA.defaults().mutate(0.15f, RNG);
                context.spawnOrganism(new Organism(pos,
                        context.getBiologicalConfig().organismEnergyStart(),
                        Gender.random(), 0, dna));
            }
        }
    }

    private void injectNutrients(int count, GridSimulationContext context) {
        for (int i = 0; i < count; i++) {
            context.spawnNutrientNear(
                    new Position(RNG.nextInt(grid.getWidth()), RNG.nextInt(grid.getHeight()),
                            RNG.nextInt(Math.max(1, depth))),
                    8f + RNG.nextFloat() * 12f);
        }
    }

    private void spawnRandomNutrient(GridSimulationContext context) {
        context.spawnNutrientNear(
                new Position(RNG.nextInt(grid.getWidth()), RNG.nextInt(grid.getHeight()),
                        RNG.nextInt(Math.max(1, depth))),
                5f + RNG.nextFloat() * 8f);
    }

    // ─── Ajustement adaptatif (P-controller) ─────────────────────────────────

    private void adjustAdaptiveParams(float plantRatio, float nutrientRatio) {
        final float lr = 0.002f; // taux d'apprentissage

        if (plantRatio < 0.5f) {
            plantPhotosynthesisBonus = clamp(
                    plantPhotosynthesisBonus + lr * (0.5f - plantRatio),
                    GameConstants.REGULATION_PHOTO_BONUS_MIN, GameConstants.REGULATION_PHOTO_BONUS_MAX);
            plantReproductionBonus = clamp(
                    plantReproductionBonus + lr * 0.5f * (0.5f - plantRatio),
                    GameConstants.REGULATION_REPRO_BONUS_MIN, GameConstants.REGULATION_REPRO_BONUS_MAX);
        } else if (plantRatio > 2.0f) {
            plantPhotosynthesisBonus = clamp(
                    plantPhotosynthesisBonus - lr * (plantRatio - 2.0f),
                    GameConstants.REGULATION_PHOTO_BONUS_MIN, GameConstants.REGULATION_PHOTO_BONUS_MAX);
        }

        if (avgOrganismRatio > 1.5f) {
            organismMetabolismPenalty = clamp(
                    organismMetabolismPenalty + lr * (avgOrganismRatio - 1.5f),
                    GameConstants.REGULATION_META_PENALTY_MIN, GameConstants.REGULATION_META_PENALTY_MAX);
        } else if (avgOrganismRatio < 0.5f) {
            organismMetabolismPenalty = clamp(
                    organismMetabolismPenalty - lr,
                    GameConstants.REGULATION_META_PENALTY_MIN, GameConstants.REGULATION_META_PENALTY_MAX);
        }

        if (nutrientRatio < 0.3f) {
            nutrientSpawnRate = clamp(nutrientSpawnRate + lr * 0.1f,
                    GameConstants.REGULATION_NUTRIENT_RATE_MIN, GameConstants.REGULATION_NUTRIENT_RATE_MAX);
        } else if (nutrientRatio > 1.5f) {
            nutrientSpawnRate = clamp(nutrientSpawnRate - lr * 0.05f,
                    GameConstants.REGULATION_NUTRIENT_RATE_MIN, GameConstants.REGULATION_NUTRIENT_RATE_MAX);
        }
    }

    // ─── Persistance ─────────────────────────────────────────────────────────

    public void saveParams() {
        try {
            Files.createDirectories(GameConstants.CONFIG_PATH.getParent());
            new ObjectMapper().writeValue(GameConstants.CONFIG_PATH.toFile(),
                    new EcosystemParams(plantPhotosynthesisBonus, organismMetabolismPenalty,
                            nutrientSpawnRate, plantReproductionBonus, regulationCount));
            log.debug("Paramètres sauvegardés : photoBonus={:.3f} metaPenalty={:.3f}",
                    plantPhotosynthesisBonus, organismMetabolismPenalty);
        } catch (IOException e) {
            log.warn("Impossible de sauvegarder les paramètres : {}", e.getMessage());
        }
    }

    private void loadParams() {
        if (!Files.exists(GameConstants.CONFIG_PATH)) {
            log.info("Première exécution — paramètres écosystème par défaut");
            return;
        }
        try {
            EcosystemParams p = new ObjectMapper()
                    .readValue(GameConstants.CONFIG_PATH.toFile(), EcosystemParams.class);
            // 70 % anciens + 30 % défauts — évite de figer un déséquilibre
            plantPhotosynthesisBonus  = p.plantPhotosynthesisBonus()  * 0.70f;
            organismMetabolismPenalty = p.organismMetabolismPenalty() * 0.70f;
            nutrientSpawnRate         = p.nutrientSpawnRate()         * 0.70f
                    + GameConstants.REGULATION_NUTRIENT_RATE_MIN * 0.30f;
            plantReproductionBonus    = p.plantReproductionBonus()    * 0.70f;
            log.info("Paramètres chargés : photoBonus={:.3f} metaPenalty={:.3f} (session #{})",
                    plantPhotosynthesisBonus, organismMetabolismPenalty, p.regulationCount() + 1);
        } catch (IOException e) {
            log.warn("Impossible de charger les paramètres : {}", e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private long countAlive(Class<?> type) {
        return grid.getAllEntities().stream()
                .filter(e -> type.isInstance(e) && !e.isDead())
                .count();
    }

    private Position randomPosition() {
        return new Position(RNG.nextInt(grid.getWidth()), RNG.nextInt(grid.getHeight()), 0);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ─── DTO de persistance ───────────────────────────────────────────────────

    public record EcosystemParams(
            float plantPhotosynthesisBonus,
            float organismMetabolismPenalty,
            float nutrientSpawnRate,
            float plantReproductionBonus,
            int   regulationCount
    ) {
        /** Constructeur sans args requis par Jackson. */
        public EcosystemParams() {
            this(0f, 0f, 0.005f, 0.002f, 0);
        }
    }
}