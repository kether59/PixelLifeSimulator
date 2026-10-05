package com.kether.pixellife.frontend.render;

import static org.lwjgl.opengl.GL11.*;

public class SlimeRender {
    // ─── Sphère (Organism) ────────────────────────────────────────────────────

    /**
     * Beautiful animated slime blob with wobble and subsurface glow
     */
    /**
     * Improved animated slime blob — more organic, juicy, and personality-driven
     */
    public static void drawSlimeBlob(float radius, float time, float wobble, int stacks, int slices, float[] baseColor, float alpha, float metabolism, float energyRatio, long seed) {

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        float pulse = (float) Math.sin(time * 3.8f) * 0.04f;
        float fastWobble = (float) Math.sin(time * 12.0f + seed) * wobble * 0.6f;

        // === Subsurface Glow (bigger, softer) ===
        glColor4f(baseColor[0] * 0.65f, baseColor[1] * 1.15f, baseColor[2] * 1.25f, alpha * 0.45f);
        glPushMatrix();
        glScalef(1.18f, 0.92f + pulse * 0.3f, 1.18f);
        drawSphere(radius * 1.12f, stacks - 3, slices);
        glPopMatrix();

        // === Main Body - Multiple overlapping layers ===
        for (int layer = 0; layer < 4; layer++) {
            float layerPulse = (float) Math.sin(time * (5.2f - layer * 0.8f) + layer) * 0.07f;
            float wx = 1f + (float) Math.sin(time * 4.1f + layer * 1.7f + seed) * wobble * (1.1f - layer * 0.15f);
            float wy = 0.85f + layerPulse + (energyRatio - 0.5f) * 0.12f; // squish when low energy
            float wz = 1f + (float) Math.cos(time * 3.7f + layer * 2.3f + seed) * wobble * 0.8f;

            glPushMatrix();
            glScalef(wx, wy, wz);

            float layerAlpha = alpha * (1f - layer * 0.18f);
            glColor4f(baseColor[0], baseColor[1], baseColor[2], layerAlpha);
            drawSphere(radius * (1f - layer * 0.085f), stacks, slices);
            glPopMatrix();
        }

        // === Bioluminescent Rim + Energy Highlight ===
        float energyGlow = Math.max(0.3f, energyRatio);
        glColor4f(0.9f, 1.0f, 0.95f, 0.35f * energyGlow);
        glPushMatrix();
        glScalef(1.08f, 1.06f, 1.08f);
        drawSphere(radius * 1.03f, stacks - 2, slices);
        glPopMatrix();

        // === Tiny surface details (optional noise) ===
        if (metabolism > 1.2f) {
            glColor4f(1f, 1f, 1f, 0.15f);
            glPushMatrix();
            glScalef(1.02f, 1.02f, 1.02f);
            drawSphere(radius * 1.06f, 6, 8);
            glPopMatrix();
        }
    }

    /**
     * Dessine une sphère UV centrée en (0,0,0) de rayon {@code r}.
     *
     * @param r      rayon
     * @param stacks subdivisions verticales (8–16)
     * @param slices subdivisions horizontales (8–16)
     */
    public static void drawSphere(float r, int stacks, int slices) {
        for (int i = 0; i < stacks; i++) {
            float lat0 = (float) Math.PI * (-0.5f + (float) i / stacks);
            float lat1 = (float) Math.PI * (-0.5f + (float) (i + 1) / stacks);

            float z0 = (float) Math.sin(lat0);
            float z1 = (float) Math.sin(lat1);
            float zr0 = (float) Math.cos(lat0);
            float zr1 = (float) Math.cos(lat1);

            glBegin(GL_TRIANGLE_STRIP);
            for (int j = 0; j <= slices; j++) {
                float lng = 2f * (float) Math.PI * (float) j / slices;
                float x = (float) Math.cos(lng);
                float y = (float) Math.sin(lng);

                glNormal3f(x * zr0, y * zr0, z0);
                glVertex3f(r * x * zr0, r * y * zr0, r * z0);

                glNormal3f(x * zr1, y * zr1, z1);
                glVertex3f(r * x * zr1, r * y * zr1, r * z1);
            }
            glEnd();
        }
    }


}
