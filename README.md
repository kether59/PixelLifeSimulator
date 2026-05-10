# PixelLife Simulator

Un simulateur d'écosystème évolutif en 3D écrit en Java, utilisant Spring Boot pour le backend et OpenGL/LWJGL pour le rendu 3D.

## 🎯 Vue d'ensemble

PixelLife est un simulateur d'écosystème artificiel où des organismes évoluent par sélection naturelle dans un environnement 3D. Le système intègre :

- **Évolution génétique** : ADN à 7 gènes influençant la physiologie des organismes
- **Écosystème dynamique** : plantes, organismes et nutriments interagissent dans un cycle de vie réaliste
- **Régulation homéostatique** : système adaptatif qui maintient l'équilibre écologique
- **Rendu 3D temps réel** : visualisation OpenGL avec cylindres, sphères et octaèdres

## 🏗️ Architecture

### Modules Maven

```
pixellife-parent/
├── common/          # Modèles partagés et DTOs
├── backend/         # Moteur de simulation Spring Boot
└── frontend/        # Interface 3D LWJGL/OpenGL
```

### Classes principales

#### Backend (`com.kether.pixellife.backend`)

- **`SimulationEngine`** : Orchestrateur principal du cycle de simulation
- **`Grid`** : Structure de données spatiale 3D pour les entités
- **`EcosystemRegulator`** : Système de régulation homéostatique adaptative
- **`GridSimulationContext`** : Contexte d'exécution pour les entités

#### Modèles d'entités (`com.kether.pixellife.backend.model`)

- **`Entity`** : Classe abstraite scellée (Organism, Plant, Nutrient)
- **`Organism`** : Entités vivantes avec ADN évolutif
- **`Plant`** : Végétaux avec croissance progressive en hauteur
- **`Nutrient`** : Particules flottantes avec mouvement brownien 3D

#### Frontend (`com.kether.pixellife.frontend`)

- **`SimulationRenderer`** : Moteur de rendu OpenGL 3D
- **`ControlPanel`** : Interface de contrôle Swing
- **`BackendClient`** : Client REST pour la communication backend

## 🔄 Cycle de simulation

Chaque tick de simulation suit cette séquence :

1. **Mise à jour des entités** : Chaque entité (Organisme, Plante, Nutriment) exécute sa logique
2. **Nettoyage** : Suppression des entités mortes, génération de nutriments
3. **Régulation** : Ajustement adaptatif des paramètres écologiques (toutes les 50 ticks)
4. **Événements** : Publication des métriques et événements

## 🎮 Contrôles

### Rendu 3D
- **Souris gauche** : Orbite autour du point focal
- **Souris droite** : Translation horizontale
- **Molette** : Zoom avant/arrière
- **WASD** : Translation horizontale
- **Q/E** : Déplacement vertical
- **T** : Recentrage sur l'origine

### Panneau de contrôle
- **Start/Stop** : Contrôle de la simulation
- **Pause/Resume** : Pause temporaire
- **Speed** : Ajustement de la vitesse (0-1000ms/tick)
- **Mutation Rate** : Taux de mutation génétique (0.0-1.0)

## 🧬 Système génétique

Les organismes possèdent un ADN à 7 gènes :

```java
public record DNA(
    float speed,        // Vitesse de déplacement (0.5-2.0)
    float size,         // Taille physique (0.3-1.0)
    float vision,       // Rayon de vision (1-10)
    float metabolism,   // Métabolisme de base (0.5-1.5)
    float aggression,   // Tendance agressive (0.0-1.0)
    float reproEnergy,  // Énergie requise pour reproduction (50-150)
    float reproRadius   // Distance de recherche partenaire (1-5)
) {}
```

### Évolution
- **Mutation** : Chaque reproduction applique une mutation gaussienne
- **Croisement** : Mélange des gènes parentaux
- **Sélection** : Les organismes inefficaces meurent de faim

## 🌱 Écosystème

### Cycle de vie
```
Plantes → Photosynthèse → Production nutriments
Organismes → Consommation nutriments/plantes → Reproduction
Mort → Décomposition en nutriments
```

### Régulation homéostatique
Le système maintient l'équilibre via :
- **Injection d'urgence** : Spawn d'entités si population < 15%
- **Ajustement adaptatif** : Bonus/malus sur photosynthèse et métabolisme
- **Apprentissage** : Paramètres sauvegardés entre sessions

## 🚀 Installation & Exécution

### Prérequis
- **Java 25** (avec preview features)
- **Maven 3.9+**
- **OpenGL 3.3+** (pilotes graphiques)

### Compilation
```bash
git clone https://github.com/your-repo/pixellife.git
cd pixellife
mvn clean compile
```

### Exécution
```bash
# Backend + Frontend
mvn spring-boot:run -pl backend

# Ou séparément :
mvn spring-boot:run -pl backend &
mvn exec:java -pl frontend
```

### Configuration
Le fichier `application.properties` contient :
```properties
# Dimensions de la grille
simulation.width=50
simulation.height=50
simulation.depth=16

# Populations initiales
simulation.plants=200
simulation.organisms=50
simulation.nutrients=300

# Paramètres génétiques
simulation.mutation-rate=0.05
```

## 📊 Métriques & Observabilité

### Événements temps réel
- `StepCompleted` : Métriques par tick (populations, énergie)
- `EntityDied` : Mort d'entité avec cause
- `EntityAte` : Consommation de nourriture
- `EntityReproduced` : Reproduction avec descendants
- `EntityMerged` : Fusion génétique

### Persistance
- **Paramètres écosystème** : `~/.pixellife/ecosystem_params.json`
- **Simulations** : Base de données H2 (fichiers ou mémoire)

## 🔧 Développement

### Structure du code
```
src/main/java/
├── common/
│   ├── dto/          # Data Transfer Objects
│   ├── event/        # Événements système
│   └── model/        # Modèles partagés
├── backend/
│   ├── api/          # Contrôleurs REST
│   ├── engine/       # Moteur de simulation
│   ├── model/        # Entités du domaine
│   ├── persistence/  # Couche de données
│   └── service/      # Logique métier
└── frontend/
    ├── client/       # Client backend
    ├── render/       # Moteur de rendu 3D
    └── ui/           # Interface utilisateur
```

### Tests
```bash
mvn test                    # Tests unitaires
mvn test -Dtest=E2ETest     # Tests d'intégration
```

### Débogage
- **Logs** : Niveaux configurables (DEBUG pour métriques détaillées)
- **Visualisation** : Sélection d'entités individuelles en 3D
- **Profilage** : Métriques de performance par tick

## 🎨 Rendu 3D

### Représentations visuelles
- **Organismes** : Sphères colorées par ADN (taille variable)
- **Plantes** : Cylindres verts (hauteur croissante)
- **Nutriments** : Octaèdres dorés flottants (rotation continue)

### Optimisations
- **LOD** : Détail adaptatif selon distance
- **Culling** : Élimination des objets hors champ
- **Batch rendering** : Groupement des appels OpenGL

## 📈 Évolution du système

### v1.0 : Base 2D
- Simulation planaire basique
- Évolution génétique simple

### v2.0 : Écosystème
- Ajout plantes et nutriments
- Cycle de vie complet

### v3.0 : 3D et régulation
- Rendu 3D OpenGL
- Système de régulation adaptative

### v4.0 : Intelligence écosystémique
- Régulateur homéostatique apprenant
- Persistance des paramètres optimaux
- Mouvement brownien 3D

## 🤝 Contribution

### Processus
1. Fork du repository
2. Création d'une branche feature
3. Tests et validation
4. Pull request avec description détaillée

### Conventions
- **Code** : Java 25 avec records et sealed classes
- **Commits** : Messages descriptifs en anglais
- **Tests** : Couverture > 80% pour logique critique

## 📄 Licence

MIT License - voir fichier LICENSE pour les détails.

## 🙏 Remerciements

- **LWJGL** pour l'accès OpenGL en Java
- **Spring Boot** pour le framework backend
- **Théorie de l'évolution** pour l'inspiration scientifique

---

*PixelLife - Où la vie artificielle rencontre l'évolution computationnelle*
