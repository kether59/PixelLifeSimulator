package com.kether.pixellife.backend.engine;

import com.kether.pixellife.backend.constant.Constant;
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
 * Régulateur homéostatique de l'écosystème — v4.
 *
 * ════════════════════════════════════════════════════════════
 * PRINCIPE
 * ════════════════════════════════════════════════════════════
 * Toutes les REGULATION_INTERVAL steps, le régulateur observe
 * les populations et applique des correctifs si nécessaire :
 *
 *   1. INJECTION D'URGENCE : si une population < seuil critique,
 *      spawn d'entités pour éviter l'extinction immédiate.
 *
 *   2. AJUSTEMENT DYNAMIQUE : les paramètres biologiques globaux
 *      (photosynthèse, métabolisme) sont légèrement ajustés selon
 *      le ratio observé → contrôle proportionnel (P-controller).
 *
 *   3. APPRENTISSAGE INTER-SESSIONS : après chaque simulation,
 *      les paramètres optimaux sont sauvegardés dans un fichier JSON.
 *      La simulation suivante repart de ces paramètres, pas des défauts.
 *
 * ════════════════════════════════════════════════════════════
 * PARAMÈTRES ADAPTATIFS (sauvegardés entre sessions)
 * ════════════════════════════════════════════════════════════
 *   - plantPhotosynthesisBonus : bonus de photosynthèse appliqué globalement
 *   - organismMetabolismPenalty : pénalité de métabolisme si trop d'organismes
 *   - nutrientSpawnRate : fréquence de spawn spontané de nutriments
 *   - plantReproductionBonus : bonus de reproduction des plantes
 *
 * Ces valeurs convergent vers l'équilibre au fil des parties.
 */
@Slf4j
public class EcosystemRegulator {

    // ─── État de la simulation courante ──────────────────────────────────────
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

    // ─── Statistiques de la session courante ─────────────────────────────────
    private long   stepsSinceLastExtinctionRisk = 0;
    private int    regulationCount = 0;
    private float  avgOrganismRatio = 1.0f; // ratio moyen organismes/initial (EMA)

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

    // ─── Régulation principale — appelée depuis SimulationEngine.tick() ──────

    public void regulate(long step, GridSimulationContext context) {
        if (step % Constant.REGULATION_INTERVAL != 0) return;

        regulationCount++;

        long plants    = countAlive(Plant.class);
        long organisms = countAlive(Organism.class);
        long nutrients = countAlive(Nutrient.class);

        // Ratios par rapport à la population initiale
        float plantRatio    = (float) plants    / initialPlants;
        float organismRatio = (float) organisms / initialOrganisms;
        float nutrientRatio = (float) nutrients / Math.max(initialNutrients, 1);

        // EMA du ratio d'organismes (alpha=0.1 → mémoire ~10 intervals)
        avgOrganismRatio = Constant.ZERO_DOT_9_FLOAT * avgOrganismRatio + Constant.ZERO_DOT_ONE_FLOAT * organismRatio;

        // ── 1. Injections d'urgence ──────────────────────────────────────────
        boolean crisis = false;

        if (plantRatio < Constant.CRITICAL_RATIO_PLANT) {
            int injectCount = Math.max(3, (int)(initialPlants * 0.10f));
            injectPlants(injectCount, context);
            log.info("Régulateur : injection d'urgence {} plantes (ratio={:.2f})",
                    injectCount, plantRatio);
            crisis = true;
        }

        if(organismRatio < Constant.CRITICAL_RATIO_ORGANISM) {
            int injectCount = Math.max(3, (int)(initialOrganisms * 0.10f));
            injectOrganisms(injectCount, context);
            log.info("Régulateur : injection d'urgence {} organisme (ratio={:.2f})",
                    injectCount, plantRatio);
            crisis = true;
        }

        if (nutrientRatio < Constant.CRITICAL_RATIO_NUTRIENT) {
            int injectCount = Math.max(5, (int)(initialNutrients * 0.15f));
            injectNutrients(injectCount, context);
            log.info("Régulateur : injection d'urgence {} nutriments (ratio={:.2f})",
                    injectCount, nutrientRatio);
        }

        if (organisms > 0 && plants > 0 && organisms > plants * 3) {
            // Surpopulation d'organismes — les freiner légèrement
            int excess = (int)(organisms - plants * 2.5f);
            if (excess > 0) {
                log.debug("Régulateur : surpopulation organismes ({} vs {} plantes)",
                        organisms, plants);
            }
        }

        // ── 2. Spawn spontané de nutriments (arrière-plan) ──────────────────
        if (RNG.nextFloat() < nutrientSpawnRate * Constant.REGULATION_INTERVAL) {
            spawnRandomNutrient(context);
        }

        // ── 3. Ajustement adaptatif des paramètres ───────────────────────────
        adjustAdaptiveParams(plantRatio, organismRatio, nutrientRatio, crisis, step);

        if (crisis) stepsSinceLastExtinctionRisk = 0;
        else stepsSinceLastExtinctionRisk += Constant.REGULATION_INTERVAL;
    }

    /**
     * Ajuste les paramètres adaptatifs selon l'état de l'écosystème.
     * Contrôle proportionnel : erreur → correction proportionnelle.
     */
    private void adjustAdaptiveParams(float plantRatio, float organismRatio,
                                      float nutrientRatio, boolean crisis, long step) {
        float lr = 0.002f; // taux d'apprentissage (learning rate)

        // Plantes en danger → bonus photosynthèse
        if (plantRatio < 0.5f) {
            plantPhotosynthesisBonus = clamp(
                    plantPhotosynthesisBonus + lr * (0.5f - plantRatio),
                    Constant.PHOTO_BONUS_MIN, Constant.PHOTO_BONUS_MAX);
            plantReproductionBonus = clamp(
                    plantReproductionBonus + lr * 0.5f * (0.5f - plantRatio),
                    Constant.REPRO_BONUS_MIN, Constant.REPRO_BONUS_MAX);
        } else if (plantRatio > 2.0f) {
            // Trop de plantes → réduire le bonus
            plantPhotosynthesisBonus = clamp(
                    plantPhotosynthesisBonus - lr * (plantRatio - 2.0f),
                    Constant.PHOTO_BONUS_MIN, Constant.PHOTO_BONUS_MAX);
        }

        // Trop d'organismes → pénalité de métabolisme légère
        if (avgOrganismRatio > 1.5f) {
            organismMetabolismPenalty = clamp(
                    organismMetabolismPenalty + lr * (avgOrganismRatio - 1.5f),
                    Constant.META_PENALTY_MIN, Constant.META_PENALTY_MAX);
        } else if (avgOrganismRatio < 0.5f) {
            // Pas assez d'organismes → réduire la pénalité
            organismMetabolismPenalty = clamp(
                    organismMetabolismPenalty - lr,
                    Constant.META_PENALTY_MIN, Constant.META_PENALTY_MAX);
        }

        // Nutriments rares → augmenter le taux de spawn spontané
        if (nutrientRatio < 0.3f) {
            nutrientSpawnRate = clamp(
                    nutrientSpawnRate + lr * 0.1f,
                    Constant.NUTRIENT_RATE_MIN, Constant.NUTRIENT_RATE_MAX);
        } else if (nutrientRatio > 1.5f) {
            nutrientSpawnRate = clamp(
                    nutrientSpawnRate - lr * 0.05f,
                    Constant.NUTRIENT_RATE_MIN, Constant.NUTRIENT_RATE_MAX);
        }

        // Sauvegarde toutes les 1000 steps
        if (step % 1000 == 0) {
            saveParams();
        }
    }

    // ─── Injections d'urgence ─────────────────────────────────────────────────

    private void injectPlants(int count, GridSimulationContext context) {
        for (int i = 0; i < count; i++) {
            Position pos = new Position(
                    RNG.nextInt(grid.getWidth()),
                    RNG.nextInt(grid.getHeight()),
                    0
            );
            if (grid.isFree(pos)) {
                context.spawnPlantNear(pos);
            }
        }
    }

    private void injectOrganisms(int count, GridSimulationContext context) {
        for (int i = 0; i < count; i++) {
            Position pos = new Position(
                    RNG.nextInt(grid.getWidth()),
                    RNG.nextInt(grid.getHeight()),
                    0
            );
            if (grid.isFree(pos)) {
                DNA dna = DNA.defaults().mutate(0.15f, RNG);
                Organism organism = new Organism(pos, Organism.ENERGY_START, Gender.random(), 0, dna);
                context.spawnOrganism(organism);
            }
        }
    }


    private void injectNutrients(int count, GridSimulationContext context) {
        for (int i = 0; i < count; i++) {
            Position pos = new Position(
                    RNG.nextInt(grid.getWidth()),
                    RNG.nextInt(grid.getHeight()),
                    RNG.nextInt(depth)
            );
            context.spawnNutrientNear(pos, 8f + RNG.nextFloat() * 12f);
        }
    }

    private void spawnRandomNutrient(GridSimulationContext context) {
        Position pos = new Position(
                RNG.nextInt(grid.getWidth()),
                RNG.nextInt(grid.getHeight()),
                RNG.nextInt(Math.max(1, depth))
        );
        context.spawnNutrientNear(pos, 5f + RNG.nextFloat() * 8f);
    }

    private long countAlive(Class<?> type) {
        return grid.getAllEntities().stream()
                .filter(e -> type.isInstance(e) && !e.isDead())
                .count();
    }

    // ─── Persistance des paramètres appris ───────────────────────────────────

    /**
     * Sauvegarde les paramètres adaptatifs dans ~/.pixellife/ecosystem_params.json.
     * Appelé toutes les 1000 steps et à la fin de chaque simulation.
     */
    public void saveParams() {
        try {
            Files.createDirectories(Constant.CONFIG_PATH.getParent());
            var params = new EcosystemParams(
                    plantPhotosynthesisBonus,
                    organismMetabolismPenalty,
                    nutrientSpawnRate,
                    plantReproductionBonus,
                    regulationCount
            );
            new ObjectMapper().writeValue(Constant.CONFIG_PATH.toFile(), params);
            log.debug("Paramètres écosystème sauvegardés : photoBonus={:.3f} metaPenalty={:.3f}",
                    plantPhotosynthesisBonus, organismMetabolismPenalty);
        } catch (IOException e) {
            log.warn("Impossible de sauvegarder les paramètres écosystème : {}", e.getMessage());
        }
    }

    /**
     * Charge les paramètres depuis la session précédente.
     * Si le fichier n'existe pas (première exécution), utilise les défauts.
     */
    private void loadParams() {
        if (!Files.exists(Constant.CONFIG_PATH)) {
            log.info("Première exécution — paramètres écosystème par défaut");
            return;
        }
        try {
            var params = new ObjectMapper().readValue(Constant.CONFIG_PATH.toFile(), EcosystemParams.class);
            // Interpolation douce : 70% anciens + 30% défauts
            // → évite de repartir sur un déséquilibre figé
            plantPhotosynthesisBonus  = params.plantPhotosynthesisBonus()  * 0.70f;
            organismMetabolismPenalty = params.organismMetabolismPenalty() * 0.70f;
            nutrientSpawnRate         = params.nutrientSpawnRate()         * 0.70f + Constant.NUTRIENT_RATE_MIN * 0.30f;
            plantReproductionBonus    = params.plantReproductionBonus()    * 0.70f;
            log.info("Paramètres écosystème chargés : photoBonus={:.3f} metaPenalty={:.3f} (session #{})",
                    plantPhotosynthesisBonus, organismMetabolismPenalty,
                    params.regulationCount() + 1);
        } catch (IOException e) {
            log.warn("Impossible de charger les paramètres écosystème : {}", e.getMessage());
        }
    }

    // ─── DTO de persistance ───────────────────────────────────────────────────

    public record EcosystemParams(
            float plantPhotosynthesisBonus,
            float organismMetabolismPenalty,
            float nutrientSpawnRate,
            float plantReproductionBonus,
            int   regulationCount
    ) {
        // Constructeur sans args requis par Jackson
        public EcosystemParams() {
            this(0f, 0f, 0.005f, 0.002f, 0);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}