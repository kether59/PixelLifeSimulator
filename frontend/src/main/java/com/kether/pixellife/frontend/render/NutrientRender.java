package com.kether.pixellife.frontend.render;

import static com.kether.pixellife.frontend.render.MeshBuilder.cross;
import static com.kether.pixellife.frontend.render.SlimeRender.drawSphere;
import static org.lwjgl.opengl.GL11.*;

/**
 * Rendu des nutriments — orbe lumineux compact v2.
 *
 * Réduction du rayon du halo externe (1.6× → 1.25×) et de son alpha (0.45 → 0.25)
 * pour éviter de saturer l'écran quand les nutriments sont nombreux.
 */
public class NutrientRender {

    /**
     * Orbe lumineux animé pour un nutriment.
     * Le halo externe est volontairement réduit pour rester lisible en grand nombre.
     *
     * @param radius rayon du noyau central
     * @param time   temps courant (animation)
     * @param color  couleur RGB [r,g,b]
     * @param seed   graine pour variation de phase
     */
    public static void drawGlowingOrb(float radius, float time, float[] color, long seed) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        float pulse = (float) Math.sin(time * 4.5f + seed * 0.01f) * 0.06f + 1.0f;

        // Noyau opaque
        glColor3f(color[0], color[1], color[2]);
        drawSphere(radius * pulse, 8, 10);

        // Halo externe — réduit de 1.6× à 1.25× et alpha 0.45 → 0.22
        glColor4f(
                Math.min(1f, color[0] * 1.3f),
                Math.min(1f, color[1] * 1.4f),
                Math.min(1f, color[2] * 1.1f),
                0.22f
        );
        glPushMatrix();
        glScalef(1.25f, 1.15f, 1.25f);
        drawSphere(radius, 6, 8);
        glPopMatrix();

        // Éclat central chaud (plus petit qu'avant)
        glColor4f(1.0f, 0.95f, 0.7f, 0.55f);
        drawSphere(radius * 0.40f, 6, 8);

        glDisable(GL_BLEND);
    }

    /** Octaèdre pour état de chargement. */
    public static void drawOctahedron(float r) {
        float[][] v = {
                {0,r,0},{r,0,0},{0,0,r},{-r,0,0},{0,0,-r},{0,-r,0}
        };
        int[][] f = {{0,1,2},{0,2,3},{0,3,4},{0,4,1},{5,2,1},{5,3,2},{5,4,3},{5,1,4}};
        glBegin(GL_TRIANGLES);
        for (int[] fi : f) {
            float[] a = v[fi[0]], b = v[fi[1]], c = v[fi[2]];
            float[] n = cross(b[0]-a[0],b[1]-a[1],b[2]-a[2], c[0]-a[0],c[1]-a[1],c[2]-a[2]);
            float l = (float)Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);
            glNormal3f(n[0]/l,n[1]/l,n[2]/l);
            glVertex3f(a[0],a[1],a[2]); glVertex3f(b[0],b[1],b[2]); glVertex3f(c[0],c[1],c[2]);
        }
        glEnd();
    }
}