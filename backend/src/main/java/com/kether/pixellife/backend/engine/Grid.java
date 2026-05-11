package com.kether.pixellife.backend.engine;

import com.kether.pixellife.backend.model.Entity;
import com.kether.pixellife.common.model.GridCell;
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

    // Indexé par GridCell (int, int) pour la performance
    private final Map<GridCell, List<Entity>> spatialIndex = new ConcurrentHashMap<>();
    private final Map<Long, Entity> entitiesById = new ConcurrentHashMap<>();

    public Grid(int width, int height) {
        this.width = width;
        this.height = height;
    }

    private GridCell toCell(Position pos) {
        return new GridCell(pos.gridX(), pos.gridY());
    }

    public void addEntity(Entity entity) {
        entitiesById.put(entity.getId(), entity);
        spatialIndex.computeIfAbsent(toCell(entity.getPosition()), _ -> new ArrayList<>())
                .add(entity);
    }

    public void removeEntity(Entity entity) {
        entitiesById.remove(entity.getId());
        List<Entity> cell = spatialIndex.get(toCell(entity.getPosition()));
        if (cell != null) {
            cell.remove(entity);
            if (cell.isEmpty()) spatialIndex.remove(toCell(entity.getPosition()));
        }
    }

    /**
     * Optimisation CRITIQUE : on ne met à jour l'index spatial QUE si
     * l'entité a changé de case (partie entière de la position).
     */
    public void updatePosition(Entity entity, Position oldPos) {
        GridCell oldCell = toCell(oldPos);
        GridCell newCell = toCell(entity.getPosition());

        if (!oldCell.equals(newCell)) {
            List<Entity> list = spatialIndex.get(oldCell);
            if (list != null) {
                list.remove(entity);
                if (list.isEmpty()) spatialIndex.remove(oldCell);
            }
            spatialIndex.computeIfAbsent(newCell, _ -> new ArrayList<>()).add(entity);
        }
    }

    public List<Entity> getEntitiesAt(Position pos) {
        return Collections.unmodifiableList(
                spatialIndex.getOrDefault(toCell(pos), Collections.emptyList())
        );
    }

    public boolean isFree(Position pos) {
        GridCell cell = toCell(pos);
        return !spatialIndex.containsKey(cell) || spatialIndex.get(cell).isEmpty();
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