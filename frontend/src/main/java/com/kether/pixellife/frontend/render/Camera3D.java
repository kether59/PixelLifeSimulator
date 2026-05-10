package com.kether.pixellife.frontend.render;

import lombok.Getter;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Caméra 3D orbitale — v3.
 *
 * Le monde 3D est orienté :
 *   X → colonnes de la grille
 *   Y → altitude (profondeur Z du moteur)
 *   Z → lignes de la grille
 *
 * Contrôles :
 *   Clic gauche + glisser  → orbite (azimut + élévation)
 *   Clic droit  + glisser  → pan
 *   Molette                → zoom
 *   WASD                   → pan clavier
 *   T                      → recentre sur la grille
 *   F                      → toggle suivi entité
 */
public class Camera3D {

    private float targetX, targetY, targetZ;
    private float azimuth   = (float)(Math.PI / 4.0);
    private float elevation = (float)(Math.PI / 3.5);
    @Getter
    private float distance  = 60f;

    // Suivi
    private boolean trackingEnabled = false;
    private float   trackedX = 0f, trackedY = 0f, trackedZ = 0f;

    // Souris
    private boolean leftPressed = false, rightPressed = false;
    private double  lastMouseX  = 0,     lastMouseY   = 0;

    private static final float ORBIT_SENS  = 0.005f;
    private static final float PAN_SENS    = 0.05f;
    private static final float ZOOM_SENS   = 3.0f;
    private static final float KEY_SPEED   = 0.5f;
    private static final float ELEV_MIN    = 0.05f;
    private static final float ELEV_MAX    = (float)(Math.PI / 2.0 - 0.05);
    private static final float DIST_MIN    = 3f;
    private static final float DIST_MAX    = 600f;

    public Camera3D(float gridW, float gridH, float gridDepth) {
        resetToGrid(gridW, gridH, gridDepth);
    }

    public void resetToGrid(float gridW, float gridH, float gridDepth) {
        targetX  = gridW / 2f;
        targetY  = gridDepth / 2f;
        targetZ  = gridH / 2f;
        distance = Math.max(gridW, gridH) * 0.9f;
    }

    public void updateTrackedEntity(float ex, float ey, float ez) {
        trackedX = ex; trackedY = ey; trackedZ = ez;
    }

    // ─── Projection + view ────────────────────────────────────────────────────

    public void applyProjection(int w, int h) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        gluPerspective(45f, (float) w / Math.max(h, 1), 0.3f, 2000f);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    public void apply() {
        if (trackingEnabled) {
            targetX += (trackedX + 0.5f - targetX) * 0.10f;
            targetY += (trackedY        - targetY)  * 0.10f;
            targetZ += (trackedZ + 0.5f - targetZ)  * 0.10f;
        }

        float camX = targetX + distance * (float)(Math.cos(elevation) * Math.cos(azimuth));
        float camY = targetY + distance * (float)(Math.sin(elevation));
        float camZ = targetZ + distance * (float)(Math.cos(elevation) * Math.sin(azimuth));

        gluLookAt(camX, camY, camZ, targetX, targetY, targetZ, 0f, 1f, 0f);
    }

    // ─── Callbacks ────────────────────────────────────────────────────────────

    public void onMouseButton(int button, int action) {
        if (button == GLFW_MOUSE_BUTTON_LEFT)  leftPressed  = action == GLFW_PRESS;
        if (button == GLFW_MOUSE_BUTTON_RIGHT) rightPressed = action == GLFW_PRESS;
    }

    public void onMouseMove(double x, double y) {
        double dx = x - lastMouseX, dy = y - lastMouseY;
        lastMouseX = x; lastMouseY = y;

        if (leftPressed) {
            azimuth   -= (float) dx * ORBIT_SENS;
            elevation += (float) dy * ORBIT_SENS;
            elevation  = clamp(elevation, ELEV_MIN, ELEV_MAX);
        }
        if (rightPressed) {
            float sa = (float) Math.sin(azimuth), ca = (float) Math.cos(azimuth);
            float pan = PAN_SENS * distance / 60f;
            targetX += (float)(-dx * sa + dy * ca) * pan;
            targetZ += (float)( dx * ca + dy * sa) * pan;
        }
    }

    public void onScroll(double yOff) {
        distance = clamp(distance - (float) yOff * ZOOM_SENS, DIST_MIN, DIST_MAX);
    }

    public void onKeyboardPan(long window) {
        float sa    = (float) Math.sin(azimuth), ca = (float) Math.cos(azimuth);
        float speed = KEY_SPEED * distance / 60f;
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) { targetX -= ca*speed; targetZ -= sa*speed; }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) { targetX += ca*speed; targetZ += sa*speed; }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) { targetX -= sa*speed; targetZ += ca*speed; }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) { targetX += sa*speed; targetZ -= ca*speed; }
        if (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS) targetY += speed;
        if (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS) targetY -= speed;
    }

    // ─── Suivi ────────────────────────────────────────────────────────────────

    public void toggleTracking()        { trackingEnabled = !trackingEnabled; }
    public boolean isTracking()         { return trackingEnabled; }
    public void setTracking(boolean v)  { trackingEnabled = v; }

    // ─── Accesseurs ───────────────────────────────────────────────────────────

    // ─── Math GL (pas de GLU requis) ─────────────────────────────────────────

    private static void gluLookAt(float ex, float ey, float ez,
                                  float cx, float cy, float cz,
                                  float ux, float uy, float uz) {
        float fx = cx-ex, fy = cy-ey, fz = cz-ez;
        float fl = (float)Math.sqrt(fx*fx+fy*fy+fz*fz);
        fx/=fl; fy/=fl; fz/=fl;

        float sx = fy*uz-fz*uy, sy = fz*ux-fx*uz, sz = fx*uy-fy*ux;
        float sl = (float)Math.sqrt(sx*sx+sy*sy+sz*sz);
        sx/=sl; sy/=sl; sz/=sl;

        float vx = sy*fz-sz*fy, vy = sz*fx-sx*fz, vz = sx*fy-sy*fx;

        float[] m = {
                sx,  vx, -fx, 0,
                sy,  vy, -fy, 0,
                sz,  vz, -fz, 0,
                0,   0,   0,  1
        };
        glMultMatrixf(m);
        glTranslatef(-ex, -ey, -ez);
    }

    private static void gluPerspective(float fovY, float aspect, float near, float far) {
        float f = 1f / (float)Math.tan(Math.toRadians(fovY) / 2.0);
        float[] m = {
                f/aspect, 0, 0,                            0,
                0,        f, 0,                            0,
                0,        0, (far+near)/(near-far),       -1,
                0,        0, 2f*far*near/(near-far),       0
        };
        glMultMatrixf(m);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}