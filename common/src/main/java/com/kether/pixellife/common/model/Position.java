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
public record Position(int x, int y, int z) {

    public Position {
        if (x < 0 || y < 0 || z < 0)
            throw new IllegalArgumentException("Position invalide: (%d, %d, %d)".formatted(x, y, z));
    }

    /** Constructeur 2D de compatibilité — z = 0. */
    public Position(int x, int y) {
        this(x, y, 0);
    }

    public Position translate(int dx, int dy) {
        return new Position(x + dx, y + dy, z);
    }

    public Position translate(int dx, int dy, int dz) {
        return new Position(x + dx, y + dy, z + dz);
    }

    public double distanceTo(Position other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        int dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Distance 2D (x,y) — pour la logique de collision/vision qui reste planaire. */
    public double distanceTo2D(Position other) {
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public boolean isWithinBounds(int width, int height) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0;
    }

    public boolean isWithinBounds(int width, int height, int depth) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth;
    }

    @Override
    public String toString() {
        return "(%d, %d, %d)".formatted(x, y, z);
    }
}