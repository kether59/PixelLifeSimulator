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

    // ─── Cube (Nutrient pixelized) ───────────────────────────────────────────────

    /**
     * Dessine un cube centré en (0,0,0) de côté {@code size}.
     * Représente un nutriment pixelisé végétal.
     *
     * @param size côté du cube
     */
    public static void drawCube(float size) {
        float h = size / 2f;
        float[][] vertices = {
                {-h, -h, -h}, {-h, -h,  h}, {-h,  h, -h}, {-h,  h,  h},
                { h, -h, -h}, { h, -h,  h}, { h,  h, -h}, { h,  h,  h}
        };

        int[][] faces = {
                {0,1,3,2}, {4,6,7,5}, {0,2,6,4}, {1,5,7,3}, {0,4,5,1}, {2,3,7,6}
        };

        float[][] normals = {
                {-1,0,0}, {1,0,0}, {0,0,-1}, {0,0,1}, {0,-1,0}, {0,1,0}
        };

        glBegin(GL_QUADS);
        for (int i = 0; i < faces.length; i++) {
            glNormal3f(normals[i][0], normals[i][1], normals[i][2]);
            for (int v : faces[i]) {
                glVertex3f(vertices[v][0], vertices[v][1], vertices[v][2]);
            }
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
     * Dessine un sol désaccordé avec variation de hauteur pour plus de réalisme.
     * Utilise un bruit pseudo-aléatoire pour créer des collines et des vallées.
     * Couleur : marron graduable selon l'altitude.
     *
     * @param gridW largeur de la grille
     * @param gridH hauteur de la grille
     * @param gridDepth profondeur de la grille pour scaler les hauteurs
     */
    public static void drawTerrainGround(int gridW, int gridH, int gridDepth) {
        float maxAltitude = gridDepth * 0.35f;  // hauteur maximale des collines

        // Diviser en carreaux pour créer des variations lisses
        int resolution = 4;  // nombre de subdivisions par cellule grille

        glDisable(GL_DEPTH_TEST);  // d'abord le sol
        glBegin(GL_TRIANGLES);

        for (int xi = 0; xi < gridW; xi++) {
            for (int zi = 0; zi < gridH; zi++) {
                // Coins du carré de grille
                float x0 = xi, x1 = xi + 1;
                float z0 = zi, z1 = zi + 1;

                // Calculer l'altitude aux quatre coins avec du bruit
                float h00 = perlinNoise(x0, z0) * maxAltitude;
                float h10 = perlinNoise(x1, z0) * maxAltitude;
                float h01 = perlinNoise(x0, z1) * maxAltitude;
                float h11 = perlinNoise(x1, z1) * maxAltitude;

                // Couleurs basées sur l'altitude (marron + variation)
                float[] c00 = terrainColor(h00);
                float[] c10 = terrainColor(h10);
                float[] c01 = terrainColor(h01);
                float[] c11 = terrainColor(h11);

                // Triangle 1 : (0,0) → (1,0) → (0,1)
                drawTerrainTriangle(x0, h00, z0, c00,
                                   x1, h10, z0, c10,
                                   x0, h01, z1, c01);

                // Triangle 2 : (1,0) → (1,1) → (0,1)
                drawTerrainTriangle(x1, h10, z0, c10,
                                   x1, h11, z1, c11,
                                   x0, h01, z1, c01);
            }
        }
        glEnd();
        glEnable(GL_DEPTH_TEST);
    }

    /**
     * Dessine un triangle du terrain avec nuances de couleur par sommet.
     */
    private static void drawTerrainTriangle(float x0, float y0, float z0, float[] c0,
                                           float x1, float y1, float z1, float[] c1,
                                           float x2, float y2, float z2, float[] c2) {
        // Calculer la normale de la face
        float[] n = cross(
                x1 - x0, y1 - y0, z1 - z0,
                x2 - x0, y2 - y0, z2 - z0
        );
        float len = (float) Math.sqrt(n[0]*n[0] + n[1]*n[1] + n[2]*n[2]);
        if (len > 0) {
            n[0] /= len; n[1] /= len; n[2] /= len;
        } else {
            n[0] = 0; n[1] = 1; n[2] = 0;  // Par défaut : normal vers le haut
        }

        glNormal3f(n[0], n[1], n[2]);
        glColor3f(c0[0], c0[1], c0[2]); glVertex3f(x0, y0, z0);
        glColor3f(c1[0], c1[1], c1[2]); glVertex3f(x1, y1, z1);
        glColor3f(c2[0], c2[1], c2[2]); glVertex3f(x2, y2, z2);
    }

    /**
     * Pseudo-bruit de Perlin simplifié pour créer du terrain varié.
     * Utilise des gradients et interpolation linéaire.
     */
    private static float perlinNoise(float x, float y) {
        // Grille de bruits à base de gradients pseudo-aléatoires
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        float xf = x - xi;
        float yf = y - yi;

        // Gradients pseudo-aléatoires aux quatre coins
        float g00 = hash2(xi,     yi)     * 2 - 1;
        float g10 = hash2(xi + 1, yi)     * 2 - 1;
        float g01 = hash2(xi,     yi + 1) * 2 - 1;
        float g11 = hash2(xi + 1, yi + 1) * 2 - 1;

        // Interpolation lisse (Smoothstep)
        float u = smoothstep(xf);
        float v = smoothstep(yf);

        float n00 = g00 * xf + (g10 - g00) * xf * u;
        float n01 = g01 * xf + (g11 - g01) * xf * u;
        float result = n00 + (n01 - n00) * v;

        return Math.max(0, Math.min(1, result * 0.5f + 0.5f));
    }

    /**
     * Simple hash fonction pour générer pseudo-aléatoires à partir de coordonnées entières.
     */
    private static float hash2(int x, int y) {
        int h = 31 * (31 * 73856093 ^ (x * 73856093))
                ^ (y * 19349663);
        return ((h ^ (h >> 16)) & 0x7fffffff) / 2.147483647f;
    }

    /**
     * Interpolation lisse (courbe S) pour des transitions fluides.
     */
    private static float smoothstep(float t) {
        return t * t * (3 - 2 * t);
    }

    /**
     * Génère une couleur progressant du marron clair au marron foncé selon l'altitude.
     */
    private static float[] terrainColor(float altitude) {
        // Marron clair (base) → marron moyen → marron foncé (pics)
        if (altitude < 0.3f) {
            // Marron clair
            float t = altitude / 0.3f;
            return new float[]{
                    0.45f + t * 0.1f,  // R : 0.45 → 0.55
                    0.38f,             // G : constant
                    0.25f              // B : constant
            };
        } else if (altitude < 0.7f) {
            // Marron moyen
            float t = (altitude - 0.3f) / 0.4f;
            return new float[]{
                    0.55f + t * 0.15f,  // R : 0.55 → 0.70
                    0.38f - t * 0.08f,  // G : 0.38 → 0.30 (plus sombre)
                    0.25f - t * 0.08f   // B : 0.25 → 0.17
            };
        } else {
            // Marron foncé (pics)
            float t = (altitude - 0.7f) / 0.3f;
            return new float[]{
                    0.70f,              // R : rougeâtre
                    0.30f - t * 0.10f,  // G : sombre
                    0.17f - t * 0.07f   // B : très sombre
            };
        }
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