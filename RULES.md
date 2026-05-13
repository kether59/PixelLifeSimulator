# PixelLife — Règles complètes & guide de réglage

## Vue d'ensemble

PixelLife est un simulateur d'écosystème 3D où trois espèces interagissent dans un cycle
énergétique fermé. L'énergie circule toujours dans le même sens :

```
Photosynthèse → Plantes → Nutriments / Organismes → Nutriments → Plantes
```

Chaque entité a un budget énergétique : quand il tombe à 0, elle meurt.

---

## Configuration à deux niveaux

### Niveau 1 — Structure (`SimulationConfig`)
Taille de la grille, populations initiales, durée, taux de mutation.

### Niveau 2 — Biologie (`BiologicalConfig`)
Paramètres fins des espèces, ajustables à chaud depuis le panneau
**RÈGLES BIOLOGIQUES** du frontend avant chaque lancement.
Si non renseignés, les valeurs par défaut s'appliquent automatiquement.

---

## Les trois espèces

### 🌿 Plantes (`Plant.java`)

Les plantes sont les **producteurs primaires** : elles créent de l'énergie via
la photosynthèse et constituent la base alimentaire des herbivores et omnivores.

#### Cycle de vie par tick

| Étape | Calcul | Configurable via |
|-------|--------|-----------------|
| **Photosynthèse** | `gain = (photoBase + bonus) × growthRate × (1 + height/maxHeight)` | `plantPhotosynthesisBase`, `plantMaxHeight` |
| **Croissance** | Si énergie > 30 % max → `height += growthRate × 0.01 × (énergie/max)` | `plantMaxHeight` |
| **Production nutriments** | Si énergie > 50 % : chance `growthRate × 0.05` | — |
| **Reproduction** | Si énergie > 60 % : chance `0.008 × growthRate` → plante fille à ±4 cases | — |
| **Métabolisme** | −`plantMetabolism` /tick | `plantMetabolism` |

#### Robustesse au broutage

```
énergie_perdue = plantBaseBite / (1 + hauteur × plantRobustnessFactor)
minimum        = PLANT_MIN_BITE_ENERGY (1.5)
```

Valeurs par défaut : baseBite=18, robustnessFactor=0.50.
- Plante au sol (hauteur 0)  → 18 dégâts/morsure
- Plante max  (hauteur 16)   → 18 / 9 ≈ 2 dégâts/morsure

#### Paramètres `BiologicalConfig`

| Paramètre | Défaut | Effet |
|-----------|--------|-------|
| `plantEnergyMax` | 200 | Réserve max — augmenter → plantes plus résistantes |
| `plantMetabolism` | 0.05 | Coût/tick — augmenter → plantes plus fragiles |
| `plantMaxHeight` | 16 | Hauteur max — réduire → robustesse réduite |
| `plantPhotosynthesisBase` | 0.20 | Gain de base/tick — réduire → croissance plus lente |
| `plantBaseBite` | 18 | Dégâts de base — réduire → plantes plus durables |
| `plantRobustnessFactor` | 0.50 | Protection/niveau — augmenter → plantes plus coriaces |

---

### 💊 Nutriments (`Nutrient.java`)

Particules d'énergie flottantes. Créés à la mort des entités, consommés instantanément
au contact d'un organisme.

#### Mouvement brownien 3D

```
vx += bruit() × brownianForce    // perturbation aléatoire X/Y
vx *= drag                        // frottement fluide
fx += vx                          // intégration position
```
L'axe Z varie 2× plus lentement que X/Y. Bursts verticaux 5 % du temps.

#### Paramètres `BiologicalConfig`

| Paramètre | Défaut | Effet |
|-----------|--------|-------|
| `nutrientDriftSpeed` | 0.12 | Vitesse max de dérive |
| `nutrientBrownianForce` | 0.03 | Agitation aléatoire |
| `nutrientDrag` | 0.92 | Amortissement — proche 1 = lent |
| `nutrientMetabolism` | 0.06 | Dégradation/tick — augmenter → disparaissent plus vite |

> **Durée de vie** : richesse=20, metabolism=0.06 → `20/0.06 ≈ 333 ticks`.

---

### 🦠 Organismes (`Organism.java`)

Agents mobiles dont le comportement est entièrement codé dans leur `DNA`.

#### Cycle de vie par tick

```
1. age++ — mort à organismMaxAge
2. move()      — chasse / nutriment / plante / aléatoire
3. eat()       — nutriment > plante > vol
4. tryMerge()  — fusion si ADN proches
5. reproduce() — sexuée avec partenaire à portée
6. payEnergyCosts() — métabolisme + taille + solitude + vieillissement
```

#### Déplacement

Priorités de cible :
1. **Agressif** (aggression > 0.6) → organisme le plus faible à portée
2. **Nutriment** le plus proche dans le rayon de vision
3. **Plante** la plus proche (selon maturité et régime)
4. **Aléatoire** 3D (avec dérive verticale lente)

Nombre de pas/tick = `round(dna.speed)`.

#### Alimentation

| Priorité | Cible | Gain | Condition |
|----------|-------|------|-----------|
| 1 | Nutriment | Richesse complète | Toujours |
| 2 | Plante | Morsure × 0.8 | diet ≠ carnivore/cannibal |
| 3 | Organisme affaibli | 25 % énergie × 0.6 | aggression > 0.7, diet ≠ herbivore |

#### Reproduction

Deux organismes de **sexes différents** se reproduisent si :
- Énergie ≥ `dna.reproEnergy` (40–110 selon le génome)
- Pas de cooldown (`organismReproCooldown` ticks)

Coût : `organismReproCost` par parent. L'enfant hérite d'un croisement des génomes
puis d'une mutation gaussienne.

#### Paramètres `BiologicalConfig`

| Paramètre | Défaut | Effet |
|-----------|--------|-------|
| `organismEnergyMax` | 200 | Réserve max |
| `organismEnergyStart` | 10 | Énergie de naissance — augmenter → démarrage plus facile |
| `organismReproCost` | 30 | Coût repro. — réduire → plus de naissances |
| `organismReproCooldown` | 50 | Pause entre repro. — réduire → rythme accéléré |
| `organismAgePenaltyStart` | 100 | Début du vieillissement — augmenter → organismes vivent plus longtemps |
| `organismMaxAge` | 200 | Âge maximal absolu |
| `organismMergeThreshold` | 0.35 | Distance ADN max pour fusionner |

---

## Le génome DNA (`DNA.java`)

Chaque organisme porte 8 gènes :

| Gène | Plage | Rôle | Impact métabolique |
|------|-------|------|--------------------|
| `speed` | 0.2 – 8.0 | Pas/tick | Indirect |
| `visionRadius` | 1 – 30 | Rayon de détection | Indirect |
| `metabolism` | 0.3 – 5.0 | Coût de base/tick | **Direct** |
| `aggression` | 0.0 – 1.0 | Comportement agressif | Indirect |
| `socialRadius` | 0 – 20 | Réservé | — |
| `reproEnergy` | 40 – 110 | Énergie min pour se reproduire | Direct |
| `size` | 0.15 – 0.48 | Taille + coût maintenance | **Direct** |
| `diet` | 0–3 | 0=omnivore, 1=herbivore, 2=carnivore, 3=cannibal | — |

**Coût total/tick :**
```
métabolisme = dna.metabolism               (0.3 à 5.0)
            + pénalité_régulateur          (0 à 0.3)
            + (dna.size − 0.15) × 0.08    (0 à ~0.026)
            + solitude                     (0.5 si aucun voisin dans r=3)
            + vieillissement               (0.15 × ((age−start)/start)²)
```

**Mutation du régime alimentaire** : mute avec probabilité `rate × 2`
(correction d'un bug où il mutait systématiquement à 100 %).

---

## Le régulateur homéostatique (`EcosystemRegulator.java`)

Toutes les **50 steps**, le régulateur observe les populations et intervient.

### Seuils d'urgence

| Population | Seuil critique | Injection |
|------------|---------------|-----------|
| Plantes    | < 15 % du départ | +10 % plantes |
| Organismes | < 10 % du départ | +10 % organismes (ADN muté) |
| Nutriments | < 5 % du départ  | +15 % nutriments |

### Paramètres adaptatifs (appris entre sessions)

| Paramètre | Borne | Déclencheur |
|-----------|-------|-------------|
| `plantPhotosynthesisBonus` (0–0.5) | Booste gain photosynthèse | Plantes < 50 % |
| `plantReproductionBonus` (0–0.02) | Bonus reproduction plantes | Plantes < 50 % |
| `organismMetabolismPenalty` (0–0.3) | Augmente coût/tick organismes | Moyenne EMA > 150 % |
| `nutrientSpawnRate` (0–0.05) | Spawn spontané de nutriments | Nutriments < 30 % |

**Taux d'apprentissage** : `lr = 0.002` — ajustements lents pour éviter les oscillations.

**Persistance** : `~/.pixellife/ecosystem_params.json`.
Au rechargement : 70 % anciens + 30 % défauts.

---

## Guide de réglage rapide

### "Les organismes meurent trop vite"
Dans le panneau **RÈGLES BIOLOGIQUES** du frontend :
- Augmenter `Énergie départ` (défaut : 10)
- Réduire `Coût repro.` (défaut : 30)
- Augmenter `Début pénalité` (défaut : 100)

Ou dans `DNA.java` (défauts génomiques) :
```java
return new DNA(1.0f, 5.0f, 0.5f, ...); // metabolism réduit à 0.5
```

### "Les plantes disparaissent"
Dans le panneau **RÈGLES BIOLOGIQUES** :
- Augmenter `Photosynthèse` (défaut : 0.20)
- Réduire `Morsure base` (défaut : 18)

Ou dans `GameConstants.java` :
```java
float REGULATION_CRITICAL_PLANT = 0.25f; // injection plus tôt
```

### "Trop de nutriments s'accumulent"
Dans le panneau **RÈGLES BIOLOGIQUES** :
- Augmenter `Dégradation` (défaut : 0.06 → essayer 0.12)

### "L'évolution va trop vite"
- Réduire `Mutation` dans la section CONFIGURATION (défaut : 0.05)

### "Les organismes ne se reproduisent jamais"
- Réduire `Coût repro.` (défaut : 30)
- Réduire `Début pénalité` — si les organismes meurent avant de se reproduire
- Dans `DNA.java` : réduire `REPR_MIN` (seuil minimal de reproEnergy)

### "Simulation trop stable"
- Augmenter `Mutation` (0.10–0.15)
- Utiliser le preset `competitive` (pression maximale)
- Modifier dans `GameConstants.java` :
```java
long REGULATION_INTERVAL = 200L; // régulateur moins fréquent
```

---

## Fichier de persistance

`~/.pixellife/ecosystem_params.json` contient les paramètres appris entre sessions.
**Supprimer ce fichier** pour repartir de zéro après un changement majeur de règles.

```json
{
  "plantPhotosynthesisBonus": 0.12,
  "organismMetabolismPenalty": 0.05,
  "nutrientSpawnRate": 0.008,
  "plantReproductionBonus": 0.004,
  "regulationCount": 47
}
```

---

## Suppression de `Constant.java`

Le fichier `Constant.java` a été supprimé. Toutes ses constantes ont été
fusionnées dans `GameConstants.java` avec des noms explicites.
Si votre IDE signale des imports manquants sur `Constant.*`,
remplacez-les par `GameConstants.*`.