# PixelLife — Règles complètes & guide de réglage

## Vue d'ensemble

PixelLife est un simulateur d'écosystème 3D où trois espèces interagissent dans un cycle
énergétique fermé. L'énergie circule toujours dans le même sens :

```
Photosynthèse → Plantes → Nutriments / Organismes → Nutriments → Plantes
```

Chaque entité a un budget énergétique : quand il tombe à 0, elle meurt.

---

## Les trois espèces

### 🌿 Plantes (`Plant.java`)

Les plantes sont les **producteurs primaires** : elles créent de l'énergie à partir de rien
(photosynthèse simulée). Elles grandissent verticalement sur 16 niveaux.

#### Cycle de vie par tick

| Étape | Calcul | Paramètre à modifier |
|-------|--------|----------------------|
| **Photosynthèse** | `gain = (0.20 + bonus) × growthRate × (1 + height/MAX_HEIGHT)` | `GROWTH_SPEED`, `ENERGY_MAX` |
| **Croissance** | Si énergie > 30 % → la hauteur monte de `GROWTH_SPEED × growthRate × (énergie/max) × facteur_aléatoire` | `GROWTH_SPEED`, `MAX_HEIGHT` |
| **Production de nutriments** | Si énergie > 50 % : chance `growthRate × 0.05` → spawn nutriment voisin | seuil 0.5, facteur 0.05 |
| **Reproduction** | Si énergie > 60 % : chance 0.8 % × growthRate → nouvelle plante à ±4 cases | seuil 0.6, 0.008 |
| **Métabolisme** | −0.05 énergie/tick | `METABOLISM = 0.05f` |

#### Robustesse au broutage

Plus une plante est grande, plus elle résiste :

```
énergie_perdue_par_morsure = BASE_BITE / (1 + hauteur × ROBUSTNESS_FACTOR)
                           = 10 / (1 + hauteur × 0.15)
```

Une petite plante (hauteur 0) perd **10** par morsure.  
Une plante mature (hauteur 16) perd seulement **10 / 3.4 ≈ 2.9** — elle est presque invincible.

#### Paramètres clés (`Plant.java`)

```java
float ENERGY_MAX       = 200f;   // réserve max — augmenter → plantes plus résistantes
float METABOLISM       = 0.05f;  // coût/tick — augmenter → plantes plus fragiles
float MAX_HEIGHT       = 16.0f;  // niveaux verticaux max
float GROWTH_SPEED     = 0.005f; // vitesse de croissance — augmenter → pousse plus vite
float BASE_BITE        = 10f;    // dégâts de base d'une morsure
float ROBUSTNESS_FACTOR= 0.15f;  // protection par niveau de hauteur
```

---

### 💊 Nutriments (`Nutrient.java`)

Les nutriments sont des **particules d'énergie flottantes**. Ils naissent à la mort des
organismes et des plantes, et sont consommés instantanément au contact d'un organisme.

#### Mouvement (mouvement brownien 3D)

Chaque tick, la vélocité est perturbée aléatoirement, amortie, puis intégrée :

```
vx += bruit() × BROWNIAN_FORCE   → perturbation aléatoire
vx ×= DRAG                       → frottement fluide
fx += vx                         → intégration position
```

Les bords sont réfléchissants. L'axe Z (vertical) se déplace 10× plus lentement que X/Y.

#### Paramètres clés (`Nutrient.java`)

```java
float DRIFT_SPEED     = 0.08f;  // vitesse max de dérive X/Y
float BROWNIAN_FORCE  = 0.015f; // agitation aléatoire — augmenter → plus chaotique
float CURRENT_FORCE   = 0.005f; // courant fictif vertical
float DRAG            = 0.92f;  // amortissement — proche de 1 → lent, proche de 0 → s'arrête
float METABOLISM      = 0.06f;  // dégradation /tick — augmenter → disparaissent plus vite
```

> **Durée de vie** : un nutriment de richesse 20 vit `20 / 0.06 ≈ 333 ticks`.  
> Pour des nutriments plus persistants : réduire `METABOLISM`.  
> Pour réduire l'accumulation : augmenter `METABOLISM`.

---

### 🦠 Organismes (`Organism.java`)

Les organismes sont les **consommateurs**. Chaque individu possède un génome (`DNA`)
qui détermine tous ses traits. Ils se déplacent, mangent, se reproduisent et vieillissent.

#### Cycle de vie par tick

```
1. Âge++, cooldowns--
2. move()       → déplacement selon vision + comportement
3. eat()        → alimentation (nutriments > plantes > vol)
4. tryMerge()   → fusion si génomes proches
5. reproduce()  → reproduction sexuée si conditions remplies
6. consumeEnergy(métabolisme + pénalité_régulateur + coût_taille + vieillissement)
```

#### Déplacement (`move()`)

L'organisme choisit sa cible selon ses priorités :

1. **Agressif** (aggression > 0.6) → se dirige vers l'organisme le plus faible dans son champ de vision
2. **Nutriment** le plus proche dans le rayon de vision → s'en approche
3. **Plante** la plus proche si pas de nutriment → s'en approche
4. **Aléatoire** → direction aléatoire + 10 % de chance de monter/descendre d'un niveau Z

Le nombre de pas par tick = `round(dna.speed)`.

#### Alimentation (`eat()`)

L'organisme ne mange que les entités **à sa position exacte** :

| Priorité | Cible | Gain | Effet sur la cible |
|----------|-------|------|--------------------|
| 1 | Nutriment | `richesse` complète | Détruite immédiatement |
| 2 | Plante (si pas de nutriment) | `morsure × 0.8` | Perd `morsure` énergie (peut survivre) |
| 3 | Organisme affaibli (aggression > 0.7, cible < 50 % énergie) | `25 %` de l'énergie de la cible × 0.6 | Perd 25 % énergie |

#### Reproduction

Deux organismes de **sexes différents** proches (`visionRadius`) se reproduisent si :
- Les deux ont assez d'énergie (`dna.reproEnergy` ≥ 70 par défaut)
- Les deux n'ont pas de cooldown de reproduction

L'enfant reçoit un **croisement des deux génomes** puis une mutation gaussienne (taux `mutationRate`).
Coût pour chaque parent : **30 énergie**. Cooldown : **50 ticks**.

#### Fusion (`tryMerge()`)

Si deux organismes génétiquement **très proches** (distance ADN < 0.35) se touchent avec ≥ 40 énergie,
ils fusionnent en un seul avec leurs énergies combinées et un ADN moyenné.

#### Vieillissement

Après **150 ticks**, un coût supplémentaire s'applique :

```
coût_vieillissement = 0.15 × (âge − 150) / 150
```

Un organisme de 300 ticks paie donc 0.15 énergie/tick en plus de son métabolisme de base.

#### Paramètres clés (`Organism.java`)

```java
float ENERGY_MAX            = 120f;  // réserve max
float ENERGY_START          = 30f;   // énergie de naissance
float REPRO_COST            = 30f;   // coût de reproduction — augmenter → moins de bébés
float REPRO_COOLDOWN_TICKS  = 50f;   // pause entre reproductions
float AGE_PENALTY_START     = 150f;  // début du vieillissement accéléré
float MERGE_THRESHOLD       = 0.35f; // distance ADN max pour fusionner
```

---

## Le génome DNA (`DNA.java`)

Chaque organisme porte 7 gènes en `float` :

| Gène | Plage | Rôle | Impact métabolique |
|------|-------|------|--------------------|
| `speed` | 0.2 – 8.0 | Pas par tick | Indirect (plus de mouvement = plus de contacts) |
| `visionRadius` | 1 – 30 | Rayon de détection | Indirect |
| `metabolism` | 0.3 – 5.0 | Coût de base/tick | **Direct** — gène le plus coûteux |
| `aggression` | 0.0 – 1.0 | Seuil comportement agressif | Indirect |
| `socialRadius` | 0 – 20 | (réservé) | — |
| `reproEnergy` | 40 – 110 | Énergie min pour se reproduire | Direct — retarde la reproduction |
| `size` | 0.15 – 0.48 | Rayon visuel + coût taille | **Direct** — `(size − 0.15) × 0.08` /tick |

**Coût total d'un organisme par tick :**

```
métabolisme = dna.metabolism               (0.3 à 5.0)
            + pénalité_régulateur          (0 à 0.3 selon surpopulation)
            + (dna.size − 0.15) × 0.08    (0 à 0.026)
            + vieillissement               (0 à ~0.15+)
```

Les organismes évoluent naturellement vers des métabolismes bas et des tailles petites
car ces traits réduisent le coût de maintenance.

---

## Le régulateur homéostatique (`EcosystemRegulator.java`)

Le régulateur s'exécute **toutes les 50 steps** et ajuste l'écosystème dynamiquement.

### Seuils d'urgence

Si une population tombe sous un seuil critique, le régulateur **injecte des entités** :

| Population | Seuil critique | Injection |
|------------|---------------|-----------|
| Plantes    | < 15 % du départ | +10 % plantes |
| Organismes | < 10 % du départ | +10 % organismes (ADN aléatoire muté) |
| Nutriments | < 5 % du départ  | +15 % nutriments |

Modifier dans `Constant.java` :
```java
float CRITICAL_RATIO_PLANT    = 0.15f;
float CRITICAL_RATIO_ORGANISM = 0.10f;
float CRITICAL_RATIO_NUTRIENT = 0.05f;
```

### Paramètres adaptatifs (appris entre sessions)

Le régulateur ajuste 4 paramètres globaux via un **contrôle proportionnel** (P-controller)
et les **sauvegarde** dans `~/.pixellife/ecosystem_params.json` :

| Paramètre | Effet | Se déclenche si… |
|-----------|-------|-----------------|
| `plantPhotosynthesisBonus` (0–0.5) | Booste le gain énergétique des plantes | Plantes < 50 % pop initiale |
| `plantReproductionBonus` (0–0.02) | Non encore utilisé dans Plant.update() | Plantes < 50 % |
| `organismMetabolismPenalty` (0–0.3) | Augmente le coût/tick des organismes | Moyenne mobile > 150 % pop initiale |
| `nutrientSpawnRate` (0–0.05) | Spawn spontané de nutriments en fond | Nutriments < 30 % pop initiale |

**Taux d'apprentissage** : `lr = 0.002f` — les ajustements sont intentionnellement lents
pour éviter les oscillations.

**Mémoire entre sessions** : au chargement, 70 % des anciens paramètres + 30 % des défauts.
Cela évite de repartir sur un déséquilibre figé après une simulation pathologique.

---

## La simulation infinie

Quand `maxSteps = 0`, la simulation tourne jusqu'à **extinction totale simultanée**
(zéro plante ET zéro organisme vivant). Le régulateur intervient avant d'en arriver là.

---

## Guide de réglage rapide

### "Les organismes meurent trop vite"

```java
// Organism.java
ENERGY_START = 40f;        // plus d'énergie à la naissance (était 30)
AGE_PENALTY_START = 200f;  // vieillissement plus tardif (était 150)
REPRO_COST = 20f;          // reproduction moins coûteuse (était 30)

// DNA.java — valeurs par défaut
return new DNA(1.0f, 5.0f, 0.5f, ...); // metabolism à 0.5 (était 0.8)
```

### "Les plantes disparaissent"

```java
// Plant.java
GROWTH_SPEED = 0.008f;     // croissance plus rapide (était 0.005)
BASE_BITE = 7f;            // moins vulnérables aux morsures (était 10)

// Constant.java
CRITICAL_RATIO_PLANT = 0.25f; // injection plus tôt (était 0.15)
```

### "Trop de nutriments s'accumulent"

```java
// Nutrient.java
METABOLISM = 0.10f;        // dégradation plus rapide (était 0.06)

// EcosystemRegulator.java — dans adjustAdaptiveParams()
nutrientSpawnRate = 0.001f; // taux de spawn spontané réduit
```

### "L'évolution va trop vite / les mutations sont trop fortes"

```java
// SimulationConfig.defaults()
0.02f  // mutationRate réduit (était 0.05)

// Organism.java — dans offspring()
DEFAULT_MUTATION_RATE = 0.02f;
```

### "Les organismes ne se reproduisent jamais"

```java
// DNA.java — valeurs par défaut
return new DNA(1.0f, 5.0f, 0.8f, 0.1f, 3.0f, 55.0f, 0.30f); // reproEnergy à 55 (était 70)

// Organism.java
REPRO_COOLDOWN_TICKS = 30f;  // cooldown réduit (était 50)
REPRO_COST = 20f;            // moins coûteux (était 30)
```

### "La simulation est trop stable / trop ennuyeuse"

Augmenter la pression de sélection :

```java
// DNA.java — élargir les plages de mutation
META_MIN = 0.1f, META_MAX = 8.0f;   // métabolisme très variable
SPEED_MIN = 0.1f, SPEED_MAX = 12.0f; // vitesse très variable

// Constant.java
REGULATION_INTERVAL = 200L;  // régulateur moins fréquent (était 50)
CRITICAL_RATIO_ORGANISM = 0.05f; // injection d'urgence plus tardive
```

---

## Fichier de persistance

`~/.pixellife/ecosystem_params.json` contient les paramètres appris.
**Supprimer ce fichier** pour repartir de zéro (utile après un changement de règles majeur).

```json
{
  "plantPhotosynthesisBonus": 0.12,
  "organismMetabolismPenalty": 0.05,
  "nutrientSpawnRate": 0.008,
  "plantReproductionBonus": 0.004,
  "regulationCount": 47
}
```