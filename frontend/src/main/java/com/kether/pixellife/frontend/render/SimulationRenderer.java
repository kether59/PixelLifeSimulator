package com.kether.pixellife.frontend.render;

import com.kether.pixellife.common.dto.SimulationDtos.EntitySnapshot;
import com.kether.pixellife.common.dto.SimulationDtos.GridSnapshot;
import com.kether.pixellife.common.model.DNA;
import com.kether.pixellife.common.model.Gender;
import com.kether.pixellife.frontend.client.BackendClient;
import com.kether.pixellife.frontend.ui.ControlPanel;
import lombok.Setter;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Renderer 3D OpenGL — v4.
 *
 * Nouveautés par rapport à v3 :
 *  - Nutriments : position flottante continue (floatX/Y/Z) → animation fluide sans saccades
 *  - Plantes    : cylindre dont la hauteur grandit (renderHeight [0..1])
 *                 rayon variable (renderRadius) selon la maturité
 *  - Organismes : métabolisme pénalisé visible via teinte légèrement désaturée
 *  - Éclairage  : second point de lumière bleuté pour donner un aspect aquatique
 *  - Grille de sol + parois 3D pour matérialiser le volume
 *
 * Contrôles identiques à v3 :
 *   Souris G    orbite     | Souris D  pan
 *   Molette     zoom       | WASD      pan horizontal
 *   Q / E       haut/bas   | T         recentre
 *   TAB         entité suiv| F         suivi entité
 *   ESPACE/R    pause/reprise | ESC    ferme
 */
public class SimulationRenderer {

    private static final int    WIN_W = 900;
    private static final int    WIN_H = 900;
    private static final String TITLE = "PixelLife 3D";

    // Qualité des formes
    private static final int SPHERE_STACKS = 10;
    private static final int SPHERE_SLICES = 12;
    private static final int PLANT_SLICES  = 8;

    // Couleurs de base
    private static final float[] C_GROUND   = {0.04f, 0.05f, 0.07f};
    private static final float[] C_GRID     = {0.11f, 0.13f, 0.17f};
    private static final float[] C_SELECT   = {1.00f, 0.90f, 0.10f};

    // Plante : gradient de vert selon maturité
    private static final float[] C_PLANT_YOUNG = {0.25f, 0.70f, 0.20f}; // vert vif — jeune
    private static final float[] C_PLANT_OLD   = {0.10f, 0.45f, 0.08f}; // vert foncé — mature

    // Nutriment : doré chaud
    private static final float[] C_NUTRIENT = {0.95f, 0.76f, 0.10f};

    // ─── État ─────────────────────────────────────────────────────────────────
    private final BackendClient client;
    private final AtomicLong    simulationId    = new AtomicLong(-1L);
    private final AtomicReference<GridSnapshot> currentSnapshot = new AtomicReference<>();
    @Setter
    private ControlPanel controlPanel;

    private long     window;
    private Camera3D camera;
    private int      gridWidth  = 50;
    private int      gridHeight = 50;
    private int      gridDepth  = 16;
    private long     lastStatStep = -1;
    private long     selectedEntityId = -1;

    // ─── Interpolation de mouvement ───────────────────────────────────────────
    /** Positions {floatX, floatY, floatZ} au snapshot précédent, par entityId. */
    private final java.util.concurrent.ConcurrentHashMap<Long, float[]> prevPositions
            = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long lastSnapshotTime  = 0;
    private static final long FETCH_INTERVAL_MS = 80;

    // ─── Construction ─────────────────────────────────────────────────────────

    public SimulationRenderer(BackendClient client) { this.client = client; }

    public void switchToSimulation(long id) {
        simulationId.set(id);
        currentSnapshot.set(null);
    }

    // ─── Boucle ───────────────────────────────────────────────────────────────

    public void run() { init(); startFetchThread(); loop(); cleanup(); }

    // ─── Init ─────────────────────────────────────────────────────────────────

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW init failed");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE,   GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES,   4);

        window = glfwCreateWindow(WIN_W, WIN_H, TITLE, NULL, NULL);
        if (window == NULL) throw new RuntimeException("Fenêtre GLFW impossible");

        camera = new Camera3D(gridWidth, gridHeight, gridDepth);

        glfwSetKeyCallback(window, (win, key, scan, action, mods) -> {
            long sid = simulationId.get();
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) glfwSetWindowShouldClose(win, true);
            if (key == GLFW_KEY_T      && action == GLFW_RELEASE) camera.resetToGrid(gridWidth, gridHeight, gridDepth);
            if (key == GLFW_KEY_F      && action == GLFW_RELEASE) camera.toggleTracking();
            if (key == GLFW_KEY_TAB    && action == GLFW_RELEASE) selectNextEntity();
            if (sid >= 0) {
                if (key == GLFW_KEY_SPACE && action == GLFW_RELEASE) client.sendPause(sid);
                if (key == GLFW_KEY_R     && action == GLFW_RELEASE) client.sendResume(sid);
            }
        });

        glfwSetMouseButtonCallback(window, (w, btn, act, mods) -> camera.onMouseButton(btn, act));
        glfwSetCursorPosCallback  (window, (w, x, y)          -> camera.onMouseMove(x, y));
        glfwSetScrollCallback     (window, (w, xo, yo)        -> camera.onScroll(yo));

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();
        setupGL();
    }

    private void setupGL() {
        glClearColor(0.02f, 0.03f, 0.05f, 1f);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_LIGHTING);
        glEnable(GL_LIGHT0);
        glEnable(GL_LIGHT1);   // second point de lumière (aquatique)
        glEnable(GL_COLOR_MATERIAL);
        glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);

        // Lumière principale solaire
        glLightfv(GL_LIGHT0, GL_POSITION, new float[]{ 0.5f,  1.0f, 0.3f, 0.0f});
        glLightfv(GL_LIGHT0, GL_DIFFUSE,  new float[]{ 0.85f, 0.82f, 0.75f, 1f});
        glLightfv(GL_LIGHT0, GL_AMBIENT,  new float[]{ 0.20f, 0.22f, 0.30f, 1f});

        // Lumière secondaire bleue (du fond — impression aquatique)
        glLightfv(GL_LIGHT1, GL_POSITION, new float[]{ 0.0f, -0.5f, 1.0f, 0.0f});
        glLightfv(GL_LIGHT1, GL_DIFFUSE,  new float[]{ 0.05f, 0.10f, 0.25f, 1f});
        glLightfv(GL_LIGHT1, GL_AMBIENT,  new float[]{ 0.00f, 0.00f, 0.00f, 1f});

        glEnable(GL_NORMALIZE);
        glShadeModel(GL_SMOOTH);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    // ─── Boucle de rendu ─────────────────────────────────────────────────────

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            int[] fw = {0}, fh = {0};
            glfwGetFramebufferSize(window, fw, fh);
            glViewport(0, 0, fw[0], fh[0]);

            camera.applyProjection(fw[0], fh[0]);
            camera.onKeyboardPan(window);

            // Suivi d'entité (position interpolée)
            if (camera.isTracking() && selectedEntityId >= 0) {
                GridSnapshot snap = currentSnapshot.get();
                if (snap != null) snap.entities().stream()
                        .filter(e -> e.id() == selectedEntityId).findFirst()
                        .ifPresent(e -> camera.updateTrackedEntity(
                                lerp(e.floatX(), prevPositions.get(e.id()), 0),
                                e.floatZ() * zScale(),
                                lerp(e.floatY(), prevPositions.get(e.id()), 1)));
            }

            camera.apply();
            renderFrame();
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    // ─── Frame principale ─────────────────────────────────────────────────────

    private void renderFrame() {
        long sid = simulationId.get();
        if (sid < 0) { renderIdle(); return; }

        GridSnapshot snap = currentSnapshot.get();
        if (snap == null) { renderWaiting(); return; }

        gridWidth  = snap.width();
        gridHeight = snap.height();
        gridDepth  = Math.max(1, snap.depth());

        renderGround();
        renderEntities(snap.entities());
        renderBoundingBox();
        updateTitle(snap);
        pushStats(snap);
    }

    /**
     * Facteur d'échelle Z : convertit les niveaux discrets en unités monde.
     * On mappe [0..depth] sur [0..min(W,H)*0.5] pour que la hauteur soit proportionnelle.
     */
    private float zScale() {
      return Math.min(gridWidth, gridHeight) * 0.5f / Math.max(gridDepth, 1);
    }

    // ─── Sol ──────────────────────────────────────────────────────────────────

    private void renderGround() {
        glDisable(GL_LIGHTING);
        glColor3f(C_GROUND[0], C_GROUND[1], C_GROUND[2]);
        MeshBuilder.drawGroundPlane(gridWidth, gridHeight);
        if (camera.getDistance() < 150f && gridWidth <= 120) {
            glColor4f(C_GRID[0], C_GRID[1], C_GRID[2], 0.7f);
            MeshBuilder.drawGroundGrid(gridWidth, gridHeight);
        }
        glEnable(GL_LIGHTING);
    }

    /** Parois semi-transparentes pour visualiser le volume 3D. */
    private void renderBoundingBox() {
        if (gridDepth <= 1) return;
        float h = gridDepth * zScale();

        glDisable(GL_LIGHTING);
        glDisable(GL_DEPTH_TEST);  // Désactiver le test de profondeur pour les parois transparentes
        glColor4f(0.10f, 0.14f, 0.22f, 0.12f);
        glBegin(GL_QUADS);
        // Face avant (y = gridHeight)
        glVertex3f(0,         0, gridHeight); glVertex3f(gridWidth, 0, gridHeight);
        glVertex3f(gridWidth, h, gridHeight); glVertex3f(0,         h, gridHeight);
        // Face gauche (x = 0)
        glVertex3f(0, 0, 0); glVertex3f(0, 0, gridHeight);
        glVertex3f(0, h, gridHeight); glVertex3f(0, h, 0);
        // Face droite (x = gridWidth)
        glVertex3f(gridWidth, 0, 0); glVertex3f(gridWidth, h, 0);
        glVertex3f(gridWidth, h, gridHeight); glVertex3f(gridWidth, 0, gridHeight);
        // Face arrière (y = 0)
        glVertex3f(0,         0, 0); glVertex3f(0,         h, 0);
        glVertex3f(gridWidth, h, 0); glVertex3f(gridWidth, 0, 0);
        glEnd();
        glEnable(GL_DEPTH_TEST);  // Réactiver pour les arêtes

        // Arêtes
        glColor4f(C_GRID[0]+0.05f, C_GRID[1]+0.05f, C_GRID[2]+0.08f, 0.5f);
        glLineWidth(0.6f);
        glBegin(GL_LINES);
        // Piliers verticaux
        float[][] corners = {{0,0},{gridWidth,0},{0,gridHeight},{gridWidth,gridHeight}};
        for (float[] c : corners) {
            glVertex3f(c[0], 0, c[1]); glVertex3f(c[0], h, c[1]);
        }
        // Toit
        glVertex3f(0,h,0); glVertex3f(gridWidth,h,0);
        glVertex3f(0,h,gridHeight); glVertex3f(gridWidth,h,gridHeight);
        glVertex3f(0,h,0); glVertex3f(0,h,gridHeight);
        glVertex3f(gridWidth,h,0); glVertex3f(gridWidth,h,gridHeight);
        glEnd();
        glEnable(GL_LIGHTING);
    }

    // ─── Entités ──────────────────────────────────────────────────────────────

    private void renderEntities(List<EntitySnapshot> entities) {
        // Ordre de rendu : nutriments → plantes → organismes (par-dessus)
        for (EntitySnapshot e : entities) if ("NUTRIENT".equals(e.type())) renderEntity(e);
        for (EntitySnapshot e : entities) if ("PLANT".equals(e.type()))    renderEntity(e);

        // Organismes : regroupement visuel des entités proches en blob composite
        List<EntitySnapshot> orgs = entities.stream()
                .filter(e -> "ORGANISM".equals(e.type()))
                .toList();
        renderOrganismClusters(orgs);
    }

    private void renderEntity(EntitySnapshot e) {
        glPushMatrix();

        switch (e.type()) {

            // ── PLANTE ────────────────────────────────────────────────────────
            case "PLANT" -> {
                float renderH = e.renderHeight();
                float radius  = e.renderRadius() > 0.05f ? e.renderRadius() : 0.30f;

                float targetHeight = Math.max(1.0f, e.z() * zScale());
                float cylH = targetHeight * renderH;


                float[] color = lerpColor(C_PLANT_YOUNG, C_PLANT_OLD, renderH);


                float worldX = e.x() + 0.5f;
                float worldZ = e.y() + 0.5f;

                glTranslatef(worldX, cylH / 2f, worldZ);

                glColor3f(color[0], color[1], color[2]);

                MeshBuilder.drawCylinder(radius, cylH, PLANT_SLICES);
            }

            // ── NUTRIMENT — position flottante continue ────────────────────────
            case "NUTRIENT" -> {
                float worldX   = lerp(e.floatX(), prevPositions.get(e.id()), 0);
                float altitude = lerp(e.floatZ(), prevPositions.get(e.id()), 2) * zScale();
                float worldZ   = lerp(e.floatY(), prevPositions.get(e.id()), 1);

                float rot = (System.currentTimeMillis() % 8000) / 8000f * 360f
                        + e.id() * 47.3f;

                glTranslatef(worldX, altitude, worldZ);
                glRotatef(rot, 0.2f, 1f, 0.2f);

                float size = 0.12f + (Math.min(e.energy(), 20f) / 20f) * 0.12f;

                glColor3f(C_NUTRIENT[0], C_NUTRIENT[1], C_NUTRIENT[2]);
                MeshBuilder.drawOctahedron(size);
            }
        }

        glPopMatrix();
    }

    // ─── Couleur ADN ─────────────────────────────────────────────────────────

    private float[] dnaColor(EntitySnapshot e) {
        if (e.dna() != null && e.dna().length >= 7) {
            DNA    dna    = DNA.fromArray(e.dna());
            Gender gender = "FEMALE".equals(e.gender()) ? Gender.FEMALE : Gender.MALE;
            return dna.toColor(gender);
        }
        return "FEMALE".equals(e.gender())
                ? new float[]{1.00f, 0.40f, 0.70f}
                : new float[]{0.30f, 0.60f, 1.00f};
    }

    /** Interpolation linéaire entre deux couleurs RGB. */
    private static float[] lerpColor(float[] a, float[] b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new float[]{
                a[0] + (b[0] - a[0]) * t,
                a[1] + (b[1] - a[1]) * t,
                a[2] + (b[2] - a[2]) * t
        };
    }

    // ─── Sélection ────────────────────────────────────────────────────────────

    private void selectNextEntity() {
        GridSnapshot snap = currentSnapshot.get();
        if (snap == null) return;
        List<EntitySnapshot> orgs = snap.entities().stream()
                .filter(e -> "ORGANISM".equals(e.type()))
                .sorted(Comparator.comparingLong(EntitySnapshot::id))
                .toList();
        if (orgs.isEmpty()) { selectedEntityId = -1; return; }
        int idx = -1;
        for (int i = 0; i < orgs.size(); i++) if (orgs.get(i).id() == selectedEntityId) { idx = i; break; }
        selectedEntityId = orgs.get((idx + 1) % orgs.size()).id();
    }

    // ─── Animations d'attente ─────────────────────────────────────────────────

    private void renderIdle() {
        float t = (System.currentTimeMillis() % 3000) / 3000f;
        float s = 0.5f + 0.3f * (float) Math.sin(t * Math.PI * 2);
        glDisable(GL_LIGHTING);
        glColor3f(0.1f * s, 0.15f * s, 0.4f * s);
        glPushMatrix(); glTranslatef(gridWidth / 2f, 3f, gridHeight / 2f);
        MeshBuilder.drawSphere(3f, 8, 10);
        glPopMatrix(); glEnable(GL_LIGHTING);
        glfwSetWindowTitle(window, TITLE + " — En attente...");
    }

    private void renderWaiting() {
        float t = (System.currentTimeMillis() % 1500) / 1500f;
        float s = 0.3f + 0.2f * (float) Math.sin(t * Math.PI * 2);
        glDisable(GL_LIGHTING);
        glColor3f(s, s, s * 1.5f);
        glPushMatrix(); glTranslatef(gridWidth / 2f, 2f, gridHeight / 2f);
        glRotatef(t * 360f, 0f, 1f, 0f);
        MeshBuilder.drawOctahedron(2f);
        glPopMatrix(); glEnable(GL_LIGHTING);
        glfwSetWindowTitle(window, TITLE + " — Chargement...");
    }

    // ─── HUD titre ────────────────────────────────────────────────────────────

    private void updateTitle(GridSnapshot snap) {
        long orgs   = snap.entities().stream().filter(e -> "ORGANISM".equals(e.type())).count();
        long plants = snap.entities().stream().filter(e -> "PLANT".equals(e.type())).count();
        long nuts   = snap.entities().stream().filter(e -> "NUTRIENT".equals(e.type())).count();
        String sel = selectedEntityId >= 0 ? " [#" + selectedEntityId + "]" : "";
        String trk = camera.isTracking() ? "⊙" : "";
        glfwSetWindowTitle(window,
                "PixelLife 3D #%d | Step:%d | O:%d P:%d N:%d%s%s"
                        .formatted(snap.simulationId(), snap.step(),
                                orgs, plants, nuts, sel, trk));
    }

    // ─── Stats → ControlPanel ─────────────────────────────────────────────────

    private void pushStats(GridSnapshot snap) {
        if (controlPanel == null || snap.step() == lastStatStep) return;
        lastStatStep = snap.step();
        int orgs  = (int) snap.entities().stream().filter(e -> "ORGANISM".equals(e.type())).count();
        int plts  = (int) snap.entities().stream().filter(e -> "PLANT".equals(e.type())).count();
        int nuts  = (int) snap.entities().stream().filter(e -> "NUTRIENT".equals(e.type())).count();
        controlPanel.updateStats(snap.step(), orgs, plts, nuts);
        if (orgs == 0 && snap.step() > 0) controlPanel.notifySimulationEnded();
    }

    // ─── Fetch thread ────────────────────────────────────────────────────────

    private void startFetchThread() {
        Thread.ofVirtual().name("renderer-fetch").start(() -> {
            while (!glfwWindowShouldClose(window)) {
                long sid = simulationId.get();
                if (sid >= 0) {
                    client.fetchGridSnapshot(sid).ifPresent(newSnap -> {
                        // Mémorise les positions courantes avant de les écraser
                        GridSnapshot old = currentSnapshot.get();
                        if (old != null) {
                            old.entities().forEach(e -> prevPositions.put(
                                    e.id(), new float[]{ e.floatX(), e.floatY(), e.floatZ() }));
                        }
                        currentSnapshot.set(newSnap);
                        lastSnapshotTime = System.currentTimeMillis();
                    });
                }
                try { Thread.sleep(FETCH_INTERVAL_MS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        });
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────────

    private void cleanup() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private static final float CLUSTER_DIST = 0.75f;
    private void renderOrganismClusters(List<EntitySnapshot> organisms) {
        if (organisms.isEmpty()) return;
        Set<Long> claimed = new java.util.HashSet<>();

        for (EntitySnapshot seed : organisms) {
            if (claimed.contains(seed.id())) continue;

            List<EntitySnapshot> cluster = new ArrayList<>();
            cluster.add(seed);
            claimed.add(seed.id());

            float sx = lerp(seed.floatX(), prevPositions.get(seed.id()), 0);
            float sz = lerp(seed.floatY(), prevPositions.get(seed.id()), 1);
            float sy = seed.z() * zScale();

            for (EntitySnapshot other : organisms) {
                if (claimed.contains(other.id())) continue;
                float ox = lerp(other.floatX(), prevPositions.get(other.id()), 0);
                float oz = lerp(other.floatY(), prevPositions.get(other.id()), 1);
                float oy = other.z() * zScale();
                float dist = (float) Math.sqrt((sx-ox)*(sx-ox)+(sz-oz)*(sz-oz)+(sy-oy)*(sy-oy));
                if (dist < CLUSTER_DIST) {
                    cluster.add(other);
                    claimed.add(other.id());
                }
            }

            if (cluster.size() == 1) {
                renderSingleOrganism(cluster.get(0));
            } else {
                renderMergedCluster(cluster);
            }
        }
    }

    /** Rendu d'un organisme isolé avec interpolation de position. */
    private void renderSingleOrganism(EntitySnapshot e) {
        float[] color  = dnaColor(e);
        float   radius = e.renderRadius() > 0.05f ? e.renderRadius() : 0.30f;
        float   worldX = lerp(e.floatX(), prevPositions.get(e.id()), 0);
        float   worldZ = lerp(e.floatY(), prevPositions.get(e.id()), 1);
        float   altY   = e.z() * zScale() + radius;

        glPushMatrix();
        glTranslatef(worldX, altY, worldZ);

        if (e.id() == selectedEntityId) {
            glDisable(GL_LIGHTING);
            glColor4f(C_SELECT[0], C_SELECT[1], C_SELECT[2], 0.45f);
            MeshBuilder.drawSphere(radius * 1.40f, 6, 8);
            glEnable(GL_LIGHTING);
        }

        glColor3f(color[0], color[1], color[2]);
        MeshBuilder.drawSphere(radius, SPHERE_STACKS, SPHERE_SLICES);
        glPopMatrix();
    }

    /**
     * Rendu d'un cluster d'organismes proches comme un seul blob composite.
     *
     * Visuellement :
     *  - Sphère centrale plus grande avec la couleur moyennée du groupe
     *  - Petites sphères satellites en orbite lente (couleur individuelle)
     *  - Halo doré si un membre du cluster est sélectionné
     */
    private void renderMergedCluster(List<EntitySnapshot> cluster) {
        float avgX = 0, avgZ = 0, avgY = 0;
        float[] blended = {0f, 0f, 0f};
        float maxR = 0f;
        boolean hasSelected = false;

        for (EntitySnapshot e : cluster) {
            float[] prev = prevPositions.get(e.id());
            avgX += lerp(e.floatX(), prev, 0);
            avgZ += lerp(e.floatY(), prev, 1);
            avgY += e.z() * zScale();
            float[] c = dnaColor(e);
            blended[0] += c[0]; blended[1] += c[1]; blended[2] += c[2];
            float r = e.renderRadius() > 0.05f ? e.renderRadius() : 0.30f;
            maxR = Math.max(maxR, r);
            if (e.id() == selectedEntityId) hasSelected = true;
        }

        int   n   = cluster.size();
        avgX /= n; avgZ /= n; avgY /= n;
        blended[0] /= n; blended[1] /= n; blended[2] /= n;

        // Le blob grossit avec le nombre de membres
        float coreR  = maxR * (1f + 0.28f * Math.min(n - 1, 5));
        float altY   = avgY + coreR;

        glPushMatrix();
        glTranslatef(avgX, altY, avgZ);

        // Halo de sélection
        if (hasSelected) {
            glDisable(GL_LIGHTING);
            glColor4f(C_SELECT[0], C_SELECT[1], C_SELECT[2], 0.50f);
            MeshBuilder.drawSphere(coreR * 1.45f, 6, 8);
            glEnable(GL_LIGHTING);
        }

        // Sphère centrale (couleur moyennée)
        glColor3f(blended[0], blended[1], blended[2]);
        MeshBuilder.drawSphere(coreR, SPHERE_STACKS, SPHERE_SLICES);

        // Satellites en orbite : chaque membre supplémentaire (max 5) tourne lentement
        int satellites = Math.min(n - 1, 5);
        if (satellites > 0) {
            float orbitAngle = (System.currentTimeMillis() % 7000) / 7000f * 360f;
            float orbitR     = coreR * 0.80f;
            float satSize    = maxR * 0.52f;

            for (int i = 0; i < satellites; i++) {
                float a  = (float) Math.toRadians(orbitAngle + i * 360f / satellites);
                float sx = (float) Math.cos(a) * orbitR;
                float sz = (float) Math.sin(a) * orbitR;

                EntitySnapshot sat = cluster.get(i + 1);
                float[] sc = dnaColor(sat);

                glPushMatrix();
                glTranslatef(sx, 0f, sz);
                glColor3f(sc[0], sc[1], sc[2]);
                MeshBuilder.drawSphere(satSize, 6, 8);
                glPopMatrix();
            }
        }

        glPopMatrix();
    }

    /** Facteur d'interpolation [0..1] basé sur le temps écoulé depuis le dernier snapshot. */
    private float interpT() {
        if (lastSnapshotTime == 0) return 1f;
        return Math.min(1f, (System.currentTimeMillis() - lastSnapshotTime) / (float) FETCH_INTERVAL_MS);
    }

    /**
     * Interpolation linéaire entre la position précédente (prev[idx]) et la courante (current).
     * Retourne current si pas de position précédente connue.
     */
    private float lerp(float current, float[] prev, int idx) {
        if (prev == null || idx >= prev.length) return current;
        float t = interpT();
        return prev[idx] + (current - prev[idx]) * t;
    }
}