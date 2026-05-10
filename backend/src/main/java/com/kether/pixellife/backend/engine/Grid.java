package com.kether.pixellife.backend.engine;

import com.kether.pixellife.backend.model.Entity;
import com.kether.pixellife.common.model.Position;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grille de simulation avec lookup spatial optimisé.
 * Utilise une HashMap de cellules pour éviter les scans O(n) sur chaque requête de voisinage.
 */
public class Grid {

    @Getter private final int width;
    @Getter private final int height;

    /** Index spatial : Position -> entités présentes */
    private final Map<Position, List<Entity>> spatialIndex = new ConcurrentHashMap<>();

    /** Toutes les entités actives */
    private final Map<Long, Entity> entitiesById = new ConcurrentHashMap<>();

    public Grid(int width, int height) {
        this.width = width;
        this.height = height;
    }

    // ─── Ajout / suppression ──────────────────────────────────────────────────

    public void addEntity(Entity entity) {
        entitiesById.put(entity.getId(), entity);
        spatialIndex.computeIfAbsent(entity.getPosition(), _ -> new ArrayList<>())
                .add(entity);
    }

    public void removeEntity(Entity entity) {
        entitiesById.remove(entity.getId());
        List<Entity> cell = spatialIndex.get(entity.getPosition());
        if (cell != null) {
            cell.remove(entity);
            if (cell.isEmpty()) spatialIndex.remove(entity.getPosition());
        }
    }

    /**
     * Met à jour l'index spatial après un déplacement.
     * Appelé par les entités elles-mêmes via SimulationContext.
     */
    public void updatePosition(Entity entity, Position oldPos) {
        List<Entity> oldCell = spatialIndex.get(oldPos);
        if (oldCell != null) {
            oldCell.remove(entity);
            if (oldCell.isEmpty()) spatialIndex.remove(oldPos);
        }
        spatialIndex.computeIfAbsent(entity.getPosition(), _ -> new ArrayList<>())
                .add(entity);
    }

    // ─── Requêtes spatiales ───────────────────────────────────────────────────

    public List<Entity> getEntitiesAt(Position pos) {
        return Collections.unmodifiableList(
                spatialIndex.getOrDefault(pos, Collections.emptyList())
        );
    }

    public List<Entity> getEntitiesInRadius(Position center, int radius) {
        List<Entity> result = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx * dx + dy * dy > radius * radius) continue;
                try {
                    Position pos = new Position(center.x() + dx, center.y() + dy);
                    if (pos.isWithinBounds(width, height)) {
                        result.addAll(getEntitiesAt(pos));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return result;
    }

    public boolean isFree(Position pos) {
        return !spatialIndex.containsKey(pos) || spatialIndex.get(pos).isEmpty();
    }

    public Optional<Position> findFreePositionNear(Position center, int maxRadius) {
        for (int r = 1; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    try {
                        Position candidate = new Position(center.x() + dx, center.y() + dy);
                        if (candidate.isWithinBounds(width, height) && isFree(candidate)) {
                            return Optional.of(candidate);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        return Optional.empty();
    }

    public Collection<Entity> getAllEntities() {
        return Collections.unmodifiableCollection(entitiesById.values());
    }

    public int entityCount() {
        return entitiesById.size();
    }

    /** Snapshot de la grille pour persistance / rendu */
    public List<Entity> snapshot() {
        return new ArrayList<>(entitiesById.values());
    }
}