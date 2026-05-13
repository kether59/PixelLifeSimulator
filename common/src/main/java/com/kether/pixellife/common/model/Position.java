package com.kether.pixellife.common.model;

/**
 * Position 3D dans la simulation.
 *
 * <p>La grille reste 2D pour le placement et la collision ({@code gridX()}, {@code gridY()}) ;
 * {@code z} est une propriété de hauteur qui évolue librement sans impacter le moteur spatial.</p>
 *
 * <p>Rétro-compatibilité : le constructeur {@code (x, y)} fixe {@code z = 0}.</p>
 */
public record Position(float x, float y, float z) {

    public Position {
        if (x < 0 || y < 0 || z < 0)
            throw new IllegalArgumentException(
                    "Position invalide : (%.2f, %.2f, %.2f)".formatted(x, y, z));
    }

    /** Constructeur 2D de compatibilité — z = 0. */
    public Position(float x, float y) {
        this(x, y, 0.0f);
    }

    // ─── Cellule de grille ────────────────────────────────────────────────────

    /** Coordonnée discrète X (partie entière). */
    public int gridX() { return (int) Math.floor(x); }
    /** Coordonnée discrète Y (partie entière). */
    public int gridY() { return (int) Math.floor(y); }
    /** Coordonnée discrète Z (partie entière). */
    public int gridZ() { return (int) Math.floor(z); }

    // ─── Translations ─────────────────────────────────────────────────────────

    public Position translate(float dx, float dy) {
        return new Position(x + dx, y + dy, z);
    }

    public Position translate(float dx, float dy, float dz) {
        return new Position(x + dx, y + dy, z + dz);
    }

    // ─── Distances ────────────────────────────────────────────────────────────

    /** Distance euclidienne 3D. */
    public double distanceTo(Position other) {
        float dx = this.x - other.x, dy = this.y - other.y, dz = this.z - other.z;
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    /** Distance 2D (x, y) — utilisée pour la logique de collision et de vision. */
    public double distanceTo2D(Position other) {
        float dx = this.x - other.x, dy = this.y - other.y;
        return Math.sqrt(dx*dx + dy*dy);
    }

    // ─── Limites ──────────────────────────────────────────────────────────────

    public boolean isWithinBounds(float width, float height) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0;
    }

    public boolean isWithinBounds(float width, float height, float depth) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth;
    }

    // ─── Affichage ────────────────────────────────────────────────────────────

    /** Bugfix : ancienne version utilisait {@code %d} sur des {@code float} → erreur runtime. */
    @Override
    public String toString() {
        return "(%.2f, %.2f, %.2f)".formatted(x, y, z);
    }
}