# PixelLife - Diagrammes UML et Graphes de Décision

Ce dossier contient la documentation technique complète du simulateur PixelLife sous forme de diagrammes PlantUML.

## 📋 Fichiers inclus

### 1. `README.md`
Documentation complète du projet avec :
- Vue d'ensemble et architecture
- Guide d'installation et d'utilisation
- Description du système génétique
- Contrôles et interface utilisateur
- Métriques et observabilité

### 2. `architecture_uml.puml`
**Diagramme de classes UML complet** montrant :
- Toutes les classes principales organisées par packages
- Relations d'héritage, composition et dépendance
- Interfaces et implémentations
- Flux de données entre composants

### 3. `tick_decision_flow.puml`
**Graphe de décision du cycle de simulation** :
- Séquence complète d'un tick de simulation
- Logique de régulation écosystémique
- Gestion des événements et métriques

### 4. `organism_update_flow.puml`
**Logique de mise à jour des organismes** :
- Cycle de vie complet d'un organisme par tick
- Gestion de l'âge, reproduction, métabolisme
- Conditions de mort

### 5. `organism_movement_feeding_flow.puml`
**Stratégie de déplacement et alimentation** :
- Logique de choix de cible de mouvement
- Priorisation des sources de nourriture
- Algorithme de recherche de partenaires/proies

### 6. `ecosystem_regulation_flow.puml`
**Système de régulation homéostatique** :
- Déclencheurs d'injection d'urgence
- Ajustements adaptatifs des paramètres
- Apprentissage inter-sessions

## 🎨 Comment visualiser les diagrammes

### Option 1 : PlantUML Server (Recommandé)
1. Rendez-vous sur https://www.plantuml.com/plantuml
2. Copiez-collez le contenu d'un fichier `.puml`
3. Le diagramme se génère automatiquement

### Option 2 : Extension VSCode
1. Installez l'extension "PlantUML" de jebbs
2. Ouvrez un fichier `.puml`
3. Utilisez `Alt+D` pour prévisualiser

### Option 3 : CLI PlantUML
```bash
# Installation
npm install -g @plantuml/plantuml

# Génération PNG
plantuml architecture_uml.puml

# Génération SVG
plantuml -tsvg architecture_uml.puml
```

## 🏗️ Architecture représentée

### Backend (Spring Boot)
- **SimulationEngine** : Orchestrateur principal
- **Grid** : Structure spatiale 3D
- **EcosystemRegulator** : IA homéostatique
- **Entités** : Organism, Plant, Nutrient

### Frontend (LWJGL/OpenGL)
- **SimulationRenderer** : Moteur 3D
- **ControlPanel** : Interface utilisateur
- **BackendClient** : Communication REST

### Common
- **DTOs** : Échange backend/frontend
- **Events** : Système d'événements
- **Models** : ADN, Position, Configuration

## 🔄 Flux de données principaux

### Cycle de simulation (tick)
1. **Mise à jour entités** → Chaque entité exécute sa logique
2. **Nettoyage** → Suppression morts + génération nutriments
3. **Régulation** → Ajustements écosystémiques (50 ticks)
4. **Événements** → Publication métriques

### Évolution génétique
1. **Mutation** → Reproduction avec variation aléatoire
2. **Sélection** → Organismes inefficaces meurent
3. **Croisement** → Mélange gènes parentaux

### Régulation homéostatique
1. **Observation** → Comptage populations
2. **Injection** → Spawn d'urgence si < 15%
3. **Adaptation** → Ajustement paramètres (P-controller)
4. **Apprentissage** → Sauvegarde paramètres optimaux

## 🎯 Points d'attention

### Performance
- **LOD** : Détail adaptatif selon distance caméra
- **Culling** : Élimination objets hors champ
- **Batch rendering** : Optimisation OpenGL

### Équilibre écosystémique
- **Injection d'urgence** : Prévient extinction
- **Pénalités adaptatives** : Régule populations
- **Persistance** : Mémorise paramètres optimaux

### Évolution
- **Mutation gaussienne** : Variation réaliste
- **Sélection naturelle** : Fitness = survie
- **Spéciation** : Différenciation par ADN

## 🚀 Utilisation recommandée

1. **Développeurs** : Commencer par `architecture_uml.puml` pour comprendre la structure
2. **Débogage** : Utiliser `tick_decision_flow.puml` pour tracer les problèmes
3. **Optimisation** : `organism_movement_feeding_flow.puml` pour analyser les goulots
4. **Balance** : `ecosystem_regulation_flow.puml` pour ajuster les paramètres

---

*Ces diagrammes constituent la documentation technique de référence pour PixelLife.*
