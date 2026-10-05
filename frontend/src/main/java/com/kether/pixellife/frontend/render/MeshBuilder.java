package com.kether.pixellife.frontend.render;

import lombok.Getter;


import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30C.glGenerateMipmap;
import static org.lwjgl.stb.STBImage.*;

/**
 * Constructeur de formes 3D pré-calculées pour le renderer PixelLife.
 * <p>
 * Toutes les formes sont centrées en (0,0,0) et dimensionnées pour une cellule unitaire.
 * Le caller applique glTranslatef + glScalef avant d'appeler la méthode de dessin.
 * <p>
 * Formes disponibles :
 * - sphère  → Organism (adapte le rayon à dna.size())
 * - cylindre plat → Plant (disque vert au sol)
 * - octaèdre → Nutriment (cristal doré flottant)
 * <p>
 * Mode immédiat GL_11 — compatible avec l'existant.
 * Migration vers VBO/instanced recommandée en phase 2.
 */
public final class MeshBuilder {

    private MeshBuilder() {
    }

    @Getter
    private static int seaweedTexture = -1;

    public static void loadTextures() {
        seaweedTexture = loadTexture("/textures/seaweed.png");
        if (seaweedTexture > 0) {
            System.out.println("✅ Seaweed texture LOADED...");
        } else {
            System.err.println("❌ Failed to load seaweed texture.");
        }
    }

    private static int loadTexture(String resourcePath) {
        try (java.io.InputStream is = MeshBuilder.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("❌ Impossible de trouver la ressource : " + resourcePath);
                return -1;
            }

            // Lire les bytes de l'stream
            byte[] bytes = is.readAllBytes();
            java.nio.ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.flip();

            int[] width = new int[1];
            int[] height = new int[1];
            int[] channels = new int[1];

            // Charger l'image depuis la mémoire grâce à STB
            java.nio.ByteBuffer image = stbi_load_from_memory(buffer, width, height, channels, 4);
            org.lwjgl.system.MemoryUtil.memFree(buffer);

            if (image == null) {
                System.err.println("❌ STB failed from memory: " + stbi_failure_reason());
                return -1;
            }

            int textureID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureID);

            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width[0], height[0], 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
            glGenerateMipmap(GL_TEXTURE_2D);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glShadeModel(GL_SMOOTH);
            glHint(GL_PERSPECTIVE_CORRECTION_HINT, GL_NICEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            stbi_image_free(image);
            return textureID;
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du chargement de la texture : " + e.getMessage());
            return -1;
        }
    }

    // ==================== IMPROVED TERRAIN ====================

    public static void drawTerrainGround(int gridW, int gridH, int gridDepth) {
        float maxH = gridDepth * 0.35f;

        glDisable(GL_DEPTH_TEST);
        glBegin(GL_TRIANGLES);

        for (int x = 0; x < gridW; x++) {
            for (int z = 0; z < gridH; z++) {
                float h00 = perlinNoise(x, z) * maxH;
                float h10 = perlinNoise(x + 1, z) * maxH;
                float h01 = perlinNoise(x, z + 1) * maxH;
                float h11 = perlinNoise(x + 1, z + 1) * maxH;

                float[] c00 = terrainColor(h00);
                float[] c10 = terrainColor(h10);
                float[] c01 = terrainColor(h01);
                float[] c11 = terrainColor(h11);

                drawTerrainTriangle(x, h00, z, c00, x + 1, h10, z, c10, x, h01, z + 1, c01);
                drawTerrainTriangle(x + 1, h10, z, c10, x + 1, h11, z + 1, c11, x, h01, z + 1, c01);
            }
        }
        glEnd();
        glEnable(GL_DEPTH_TEST);
    }

    private static void drawTerrainTriangle(float x0, float y0, float z0, float[] c0,
                                            float x1, float y1, float z1, float[] c1,
                                            float x2, float y2, float z2, float[] c2) {
        float[] n = cross(x1 - x0, y1 - y0, z1 - z0, x2 - x0, y2 - y0, z2 - z0);
        float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (len > 0) {
            n[0] /= len; n[1] /= len; n[2] /= len;
        } else {
            n[0] = 0; n[1] = 1; n[2] = 0;
        }

        glNormal3f(n[0], n[1], n[2]);
        glColor3f(c0[0], c0[1], c0[2]); glVertex3f(x0, y0, z0);
        glColor3f(c1[0], c1[1], c1[2]); glVertex3f(x1, y1, z1);
        glColor3f(c2[0], c2[1], c2[2]); glVertex3f(x2, y2, z2);
    }

    private static float[] terrainColor(float altitude) {
        if (altitude < 0.4f) {
            return new float[]{0.35f, 0.28f, 0.18f};           // dark sand
        } else if (altitude < 1.2f) {
            return new float[]{0.45f, 0.38f, 0.25f};           // brown
        } else {
            return new float[]{0.55f, 0.48f, 0.35f};           // lighter earth
        }
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
        float g00 = hash2(xi, yi) * 2 - 1;
        float g10 = hash2(xi + 1, yi) * 2 - 1;
        float g01 = hash2(xi, yi + 1) * 2 - 1;
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
        int h = 31 * (31 * 73856093 ^ (x * 73856093)) ^ (y * 19349663);
        return ((h ^ (h >> 16)) & 0x7fffffff) / 2.147483647f;
    }

    /**
     * Interpolation lisse (courbe S) pour des transitions fluides.
     */
    private static float smoothstep(float t) {
        return t * t * (3 - 2 * t);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    static float[] cross(float ax, float ay, float az, float bx, float by, float bz) {
        return new float[]{ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
    }

}