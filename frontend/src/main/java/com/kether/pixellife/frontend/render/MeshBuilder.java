package com.kether.pixellife.frontend.render;

import static org.lwjgl.opengl.GL11.*;

/**
 * Constructeur de formes 3D pré-calculées pour le renderer PixelLife.
 *
 * Toutes les formes sont centrées en (0,0,0) et dimensionnées pour une cellule unitaire.
 * Le caller applique glTranslatef + glScalef avant d'appeler la méthode de dessin.
 *
 * Formes disponibles :
 *   - sphère  → Organism (adapte le rayon à dna.size())
 *   - cylindre plat → Plant (disque vert au sol)
 *   - octaèdre → Nutriment (cristal doré flottant)
 *
 * Mode immédiat GL_11 — compatible avec l'existant.
 * Migration vers VBO/instanced recommandée en phase 2.
 */
public final class MeshBuilder {

    private MeshBuilder() {}

    // ─── Sphère (Organism) ────────────────────────────────────────────────────

    /**
     * Dessine une sphère UV centrée en (0,0,0) de rayon {@code r}.
     *
     * @param r       rayon
     * @param stacks  subdivisions verticales (8–16)
     * @param slices  subdivisions horizontales (8–16)
     */
    public static void drawSphere(float r, int stacks, int slices) {
        for (int i = 0; i < stacks; i++) {
            float lat0 = (float) Math.PI * (-0.5f + (float) i       / stacks);
            float lat1 = (float) Math.PI * (-0.5f + (float)(i + 1) / stacks);

            float z0  = (float) Math.sin(lat0);
            float z1  = (float) Math.sin(lat1);
            float zr0 = (float) Math.cos(lat0);
            float zr1 = (float) Math.cos(lat1);

            glBegin(GL_TRIANGLE_STRIP);
            for (int j = 0; j <= slices; j++) {
                float lng = 2f * (float) Math.PI * (float) j / slices;
                float x   = (float) Math.cos(lng);
                float y   = (float) Math.sin(lng);

                glNormal3f(x * zr0, y * zr0, z0);
                glVertex3f(r * x * zr0, r * y * zr0, r * z0);

                glNormal3f(x * zr1, y * zr1, z1);
                glVertex3f(r * x * zr1, r * y * zr1, r * z1);
            }
            glEnd();
        }
    }

    // ─── Cylindre plat (Plant) ────────────────────────────────────────────────

    /**
     * Dessine un cylindre bas (disque épais) centré à y=0.
     * Représente une plante vue de dessus et de côté.
     *
     * @param radius rayon du cylindre (en unités-grille)
     * @param height hauteur du cylindre
     * @param slices nombre de segments
     */
    public static void drawCylinder(float radius, float height, int slices) {
        float halfH = height / 2f;

        // Disque supérieur (normal +Y)
        glBegin(GL_TRIANGLE_FAN);
        glNormal3f(0f, 1f, 0f);
        glVertex3f(0f, halfH, 0f);
        for (int i = 0; i <= slices; i++) {
            float a = 2f * (float) Math.PI * i / slices;
            glVertex3f(radius * (float) Math.cos(a), halfH, radius * (float) Math.sin(a));
        }
        glEnd();

        // Disque inférieur (normal -Y)
        glBegin(GL_TRIANGLE_FAN);
        glNormal3f(0f, -1f, 0f);
        glVertex3f(0f, -halfH, 0f);
        for (int i = slices; i >= 0; i--) {
            float a = 2f * (float) Math.PI * i / slices;
            glVertex3f(radius * (float) Math.cos(a), -halfH, radius * (float) Math.sin(a));
        }
        glEnd();

        // Paroi latérale
        glBegin(GL_TRIANGLE_STRIP);
        for (int i = 0; i <= slices; i++) {
            float a = 2f * (float) Math.PI * i / slices;
            float nx = (float) Math.cos(a);
            float nz = (float) Math.sin(a);
            glNormal3f(nx, 0f, nz);
            glVertex3f(radius * nx, -halfH, radius * nz);
            glVertex3f(radius * nx,  halfH, radius * nz);
        }
        glEnd();
    }

    // ─── Octaèdre (Nutrient) ─────────────────────────────────────────────────

    /**
     * Dessine un octaèdre régulier centré en (0,0,0) de rayon {@code r}.
     * Représente un nutriment — cristal flottant doré.
     * 8 faces triangulaires, rendu rapide.
     *
     * @param r rayon (du centre au sommet)
     */
    public static void drawOctahedron(float r) {
        float[][] vertices = {
                { 0,  r,  0},   // top
                { r,  0,  0},   // +X
                { 0,  0,  r},   // +Z
                {-r,  0,  0},   // -X
                { 0,  0, -r},   // -Z
                { 0, -r,  0}    // bottom
        };

        // 8 faces de l'octaèdre
        int[][] faces = {
                {0, 1, 2}, {0, 2, 3}, {0, 3, 4}, {0, 4, 1}, // hémisphère haut
                {5, 2, 1}, {5, 3, 2}, {5, 4, 3}, {5, 1, 4}  // hémisphère bas
        };

        glBegin(GL_TRIANGLES);
        for (int[] f : faces) {
            float[] a = vertices[f[0]];
            float[] b = vertices[f[1]];
            float[] c = vertices[f[2]];

            // Normale de la face
            float[] n = cross(
                    b[0]-a[0], b[1]-a[1], b[2]-a[2],
                    c[0]-a[0], c[1]-a[1], c[2]-a[2]
            );
            float len = (float) Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);
            glNormal3f(n[0]/len, n[1]/len, n[2]/len);

            glVertex3f(a[0], a[1], a[2]);
            glVertex3f(b[0], b[1], b[2]);
            glVertex3f(c[0], c[1], c[2]);
        }
        glEnd();
    }

    // ─── Grille de fond 3D ────────────────────────────────────────────────────

    /**
     * Dessine la grille de fond dans le plan XZ (y=0).
     *
     * @param gridW largeur de la grille
     * @param gridH hauteur de la grille
     */
    public static void drawGroundGrid(int gridW, int gridH) {
        glLineWidth(0.4f);
        glBegin(GL_LINES);
        for (int x = 0; x <= gridW; x++) {
            glVertex3f(x, 0f, 0f);
            glVertex3f(x, 0f, gridH);
        }
        for (int z = 0; z <= gridH; z++) {
            glVertex3f(0f, 0f, z);
            glVertex3f(gridW, 0f, z);
        }
        glEnd();
    }

    /**
     * Dessine un plan de sol plein (quad) pour le reflet ambiant.
     */
    public static void drawGroundPlane(int gridW, int gridH) {
        glBegin(GL_QUADS);
        glNormal3f(0f, 1f, 0f);
        glVertex3f(0f,    0f, 0f);
        glVertex3f(gridW, 0f, 0f);
        glVertex3f(gridW, 0f, gridH);
        glVertex3f(0f,    0f, gridH);
        glEnd();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static float[] cross(float ax, float ay, float az,
                                 float bx, float by, float bz) {
        return new float[]{
                ay*bz - az*by,
                az*bx - ax*bz,
                ax*by - ay*bx
        };
    }
}