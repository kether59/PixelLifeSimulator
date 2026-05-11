package com.kether.pixellife.common.model;

/**
 * Position 3D dans la simulation.
 *
 * v3 : ajout de la coordonnée z.
 *  - x : colonne (horizontal)
 *  - y : ligne   (vertical dans la grille)
 *  - z : profondeur / hauteur dans l'espace 3D
 *
 * Rétro-compatibilité : le constructeur (x, y) fixe z = 0.
 * La grille reste 2D pour le placement et la détection de collision
 * (cells indexées par (x,y) uniquement) — z est une propriété d'affichage
 * qui peut évoluer librement sans impacter le moteur de collision.
 */
public record Position(float x, float y, float z) {

    /** Coordonnées de la cellule de grille correspondante. */
    public int gridX() { return (int) Math.floor(x); }
    public int gridY() { return (int) Math.floor(y); }
    public int gridZ() { return (int) Math.floor(z); }

    public Position {
        if (x < 0 || y < 0 || z < 0)
            throw new IllegalArgumentException("Position invalide: (%d, %d, %d)".formatted(x, y, z));
    }

    /** Constructeur 2D de compatibilité — z = 0. */
    public Position(float x, float y) {
        this(x, y, 0.0f);
    }

    public Position translate(float dx, float dy) {
        return new Position(x + dx, y + dy, z);
    }

    public Position translate(float dx, float dy, float dz) {
        return new Position(x + dx, y + dy, z + dz);
    }

    public double distanceTo(Position other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        float dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Distance 2D (x,y) — pour la logique de collision/vision qui reste planaire. */
    public double distanceTo2D(Position other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public boolean isWithinBounds(float width, float height) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0;
    }

    public boolean isWithinBounds(float width, float height, float depth) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth;
    }

    @Override
    public String toString() {
        return "(%d, %d, %d)".formatted(x, y, z);
    }
}