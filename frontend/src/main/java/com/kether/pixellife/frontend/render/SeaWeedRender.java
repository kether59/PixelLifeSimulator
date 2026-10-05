package com.kether.pixellife.frontend.render;

import static org.lwjgl.opengl.GL11.*;

/**
 * Rendu des plantes sous forme d'algues marines animées — v2.
 *
 * Fix v2 :
 *   - Bug critique : `y = t * maxHeight * growth` causait une double réduction
 *     (maxHeight intègre déjà growth). Corrigé en `y = t * maxHeight`.
 *   - worldMaxH propagé depuis le renderer pour que les algues s'adaptent
 *     à la taille réelle du monde (par défaut ~34 unités pour grille 80×80).
 *   - Balancement (sway) et largeur proportionnels à la hauteur réelle.
 *   - Couleur du sommet virée vers le jaune-vert pour les plantes matures.
 */
public class SeaWeedRender {

    /**
     * Dessine une algue animée par texture en croix (deux plans perpendiculaires).
     *
     * @param growth   taux de croissance normalisé [0..1] (renderHeight de la plante)
     * @param time     temps courant en secondes
     * @param seed     graine de variation par entité (e.id() * constante)
     * @param worldMaxH hauteur maximale en unités monde (gridDepth * zScale * facteur)
     */
    public static void drawSeaweed(float growth, float time, long seed, float worldMaxH) {
        if (growth <= 0.01f) return;

        // ── Hauteur réelle en unités monde ───────────────────────────────────
        // growth [0..1] pilote la fraction de worldMaxH atteinte
        float maxHeight = worldMaxH * Math.max(0.04f, growth);

        // ── Balancement global — proportionnel à la hauteur ──────────────────
        float swayAmp  = maxHeight * 0.11f;  // 11% du height au sommet
        float globalSway = (float) Math.sin(time * 0.8f + seed * 0.01f) * swayAmp * 0.25f;

        // ── Setup GL ─────────────────────────────────────────────────────────
        glPushMatrix();
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);
        glShadeModel(GL_SMOOTH);
        glHint(GL_PERSPECTIVE_CORRECTION_HINT, GL_NICEST);
        glBindTexture(GL_TEXTURE_2D, MeshBuilder.getSeaweedTexture());

        // ── Deux plans croisés pour un aspect 3D ─────────────────────────────
        drawPlane(false, time, seed,       maxHeight, globalSway, swayAmp, growth);
        drawPlane(true,  time, seed + 997, maxHeight, globalSway, swayAmp, growth);

        // ── Restore ───────────────────────────────────────────────────────────
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
        glDepthMask(true);
        glPopMatrix();
    }

    // ─── Plan unique d'algue ──────────────────────────────────────────────────

    private static void drawPlane(boolean zPlane, float time, long seed,
                                  float maxHeight, float globalSway, float swayAmp,
                                  float growth) {
        int SEGS = 20;

        for (int stalk = 0; stalk < 3; stalk++) {
            float offset = (stalk - 1) * maxHeight * 0.10f;  // espace inter-tiges proportionnel
            float phase  = seed * 0.013f + stalk * 1.7f;

            glBegin(GL_TRIANGLE_STRIP);
            for (int i = 0; i <= SEGS; i++) {
                float t = (float) i / SEGS;

                // ── CORRECTION PRINCIPALE : y = t * maxHeight (plus de * growth) ──
                float y = t * maxHeight;

                // Largeur proportionnelle au height, s'effile vers le sommet
                float w = maxHeight * 0.055f * (1f - t * 0.42f);

                // Balancement composite : global lent + local rapide
                float sway = globalSway * t
                        + (float) Math.sin(t * 7.5f + time * 2.2f + phase) * swayAmp * t * 0.8f;

                // Couleur : vert vif jeune → vert-jaune mature
                float greenBase = 0.78f - t * 0.15f;
                float redBase   = 0.42f * growth + 0.05f;  // plus jaune si mature
                float alpha     = 0.88f - t * 0.22f;
                glColor4f(redBase, greenBase, 0.28f * (1f - growth * 0.5f), alpha);

                float vCoord = 1f - t;   // texture de bas (1) vers haut (0)

                if (!zPlane) {
                    float cx = offset + sway;
                    glTexCoord2f(0f, vCoord); glVertex3f(cx - w, y, 0f);
                    glTexCoord2f(1f, vCoord); glVertex3f(cx + w, y, 0f);
                } else {
                    float cz = offset + sway;
                    glTexCoord2f(0f, vCoord); glVertex3f(0f, y, cz - w);
                    glTexCoord2f(1f, vCoord); glVertex3f(0f, y, cz + w);
                }
            }
            glEnd();
        }
    }
}