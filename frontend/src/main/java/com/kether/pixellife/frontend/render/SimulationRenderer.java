package com.kether.pixellife.frontend.render;

import com.kether.pixellife.common.dto.SimulationDtos.EntitySnapshot;
import com.kether.pixellife.common.dto.SimulationDtos.GridSnapshot;
import com.kether.pixellife.common.model.DNA;
import com.kether.pixellife.frontend.client.BackendClient;
import com.kether.pixellife.frontend.ui.ControlPanel;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.kether.pixellife.frontend.render.NutrientRender.drawGlowingOrb;
import static com.kether.pixellife.frontend.render.NutrientRender.drawOctahedron;
import static com.kether.pixellife.frontend.render.SeaWeedRender.drawSeaweed;
import static com.kether.pixellife.frontend.render.SlimeRender.drawSlimeBlob;
import static com.kether.pixellife.frontend.render.SlimeRender.drawSphere;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Renderer 3D OpenGL — v5.
 *
 * Corrections v5 :
 *   - Algues : transmission de worldMaxH à drawSeaweed pour une hauteur correcte
 *   - Nutriments : rendu limité si >800 (LOD basique) + orbe réduit
 *   - Organismes : inchangés (slime blob)
 */
public class SimulationRenderer {

    private static final int    WIN_W  = 900;
    private static final int    WIN_H  = 900;
    private static final String TITLE  = "PixelLife 3D";

    private static final int SPHERE_STACKS = 10;
    private static final int SPHERE_SLICES = 12;

    private static final float[] C_GROUND   = {0.04f, 0.05f, 0.07f};
    private static final float[] C_GRID     = {0.11f, 0.13f, 0.17f};
    private static final float[] C_SELECT   = {1.00f, 0.90f, 0.10f};
    private static final float[] C_NUTRIENT = {0.95f, 0.76f, 0.10f};

    private static final long  FETCH_INTERVAL_MS = 80;
    private static final float CLUSTER_DIST      = 0.75f;

    // ── État ─────────────────────────────────────────────────────────────────
    private final BackendClient  client;
    private final AtomicLong     simulationId    = new AtomicLong(-1L);
    private final AtomicReference<GridSnapshot> currentSnapshot = new AtomicReference<>();

    /** Positions {floatX, floatY, floatZ} au snapshot précédent, par entityId. */
    private final java.util.concurrent.ConcurrentHashMap<Long, float[]> prevPositions
            = new java.util.concurrent.ConcurrentHashMap<>();

    @Setter private ControlPanel controlPanel;
    @Getter @Setter private SlimeRender slimeRender;

    private long     window;
    private Camera3D camera;
    private int      gridWidth    = 50;
    private int      gridHeight   = 50;
    private int      gridDepth    = 16;
    private long     lastStatStep = -1;
    private long     selectedEntityId = -1;
    private volatile long lastSnapshotTime = 0;

    public SimulationRenderer(BackendClient client) { this.client = client; }

    public void switchToSimulation(long id) {
        simulationId.set(id);
        currentSnapshot.set(null);
    }

    // ─── Boucle principale ────────────────────────────────────────────────────

    public void run() { init(); startFetchThread(); loop(); cleanup(); }

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
        MeshBuilder.loadTextures();
        setupGL();
    }

    private void setupGL() {
        glClearColor(0.004f, 0.008f, 0.022f, 1f);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_LIGHTING);
        glEnable(GL_LIGHT0);
        glEnable(GL_LIGHT1);
        glEnable(GL_LIGHT2);
        glEnable(GL_COLOR_MATERIAL);
        glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);

        glLightfv(GL_LIGHT0, GL_POSITION, new float[]{ 0.4f,  1.0f, 0.3f, 0f});
        glLightfv(GL_LIGHT0, GL_DIFFUSE,  new float[]{ 0.82f, 0.85f, 0.78f, 1f});
        glLightfv(GL_LIGHT0, GL_AMBIENT,  new float[]{ 0.18f, 0.20f, 0.28f, 1f});

        glLightfv(GL_LIGHT1, GL_POSITION, new float[]{ 0.2f, -0.3f, 0.9f, 0f});
        glLightfv(GL_LIGHT1, GL_DIFFUSE,  new float[]{ 0.12f, 0.22f, 0.45f, 1f});

        glLightfv(GL_LIGHT2, GL_POSITION, new float[]{-0.6f, 0.7f, -0.5f, 0f});
        glLightfv(GL_LIGHT2, GL_DIFFUSE,  new float[]{ 0.28f, 0.15f, 0.42f, 1f});

        glEnable(GL_FOG);
        glFogi(GL_FOG_MODE, GL_EXP2);
        glFogf(GL_FOG_DENSITY, 0.014f);
        glFogfv(GL_FOG_COLOR, new float[]{0.005f, 0.01f, 0.025f, 1f});
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

            if (camera.isTracking() && selectedEntityId >= 0) {
                GridSnapshot snap = currentSnapshot.get();
                if (snap != null) snap.entities().stream()
                        .filter(e -> e.id() == selectedEntityId).findFirst()
                        .ifPresent(e -> camera.updateTrackedEntity(
                                lerp(e.floatX(), prevPositions.get(e.id()), 0),
                                lerp(e.floatZ(), prevPositions.get(e.id()), 2) * zScale(),
                                lerp(e.floatY(), prevPositions.get(e.id()), 1)));
            }

            camera.apply();
            renderFrame();
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    // ─── Frame ───────────────────────────────────────────────────────────────

    private void renderFrame() {
        long sid = simulationId.get();
        if (sid < 0)   { renderIdle();    return; }

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

    /** zScale : mappe [0..depth] sur [0..min(W,H)*0.5]. */
    private float zScale() {
        return Math.min(gridWidth, gridHeight) * 0.5f / Math.max(gridDepth, 1);
    }

    /** worldMaxH : hauteur max des algues en unités monde. */
    private float worldMaxH() {
        return gridDepth * zScale() * 0.80f;
    }

    // ─── Sol et parois ────────────────────────────────────────────────────────

    private void renderGround() {
        glDisable(GL_LIGHTING);
        glColor3f(C_GROUND[0], C_GROUND[1], C_GROUND[2]);
        MeshBuilder.drawTerrainGround(gridWidth, gridHeight, gridDepth);
        if (camera.getDistance() < 150f && gridWidth <= 120) {
            glColor4f(C_GRID[0], C_GRID[1], C_GRID[2], 0.7f);
            MeshBuilder.drawGroundGrid(gridWidth, gridHeight);
        }
        glEnable(GL_LIGHTING);
    }

    private void renderBoundingBox() {
        if (gridDepth <= 1) return;
        float h = gridDepth * zScale();

        glDisable(GL_LIGHTING);
        glDisable(GL_DEPTH_TEST);
        glColor4f(0.10f, 0.14f, 0.22f, 0.12f);
        glBegin(GL_QUADS);
        glVertex3f(0,0,gridHeight); glVertex3f(gridWidth,0,gridHeight);
        glVertex3f(gridWidth,h,gridHeight); glVertex3f(0,h,gridHeight);
        glVertex3f(0,0,0); glVertex3f(0,0,gridHeight);
        glVertex3f(0,h,gridHeight); glVertex3f(0,h,0);
        glVertex3f(gridWidth,0,0); glVertex3f(gridWidth,h,0);
        glVertex3f(gridWidth,h,gridHeight); glVertex3f(gridWidth,0,gridHeight);
        glVertex3f(0,0,0); glVertex3f(0,h,0);
        glVertex3f(gridWidth,h,0); glVertex3f(gridWidth,0,0);
        glEnd();
        glEnable(GL_DEPTH_TEST);

        glColor4f(C_GRID[0]+0.05f, C_GRID[1]+0.05f, C_GRID[2]+0.08f, 0.5f);
        glLineWidth(0.6f);
        glBegin(GL_LINES);
        float[][] corners = {{0,0},{gridWidth,0},{0,gridHeight},{gridWidth,gridHeight}};
        for (float[] c : corners) { glVertex3f(c[0],0,c[1]); glVertex3f(c[0],h,c[1]); }
        glVertex3f(0,h,0); glVertex3f(gridWidth,h,0);
        glVertex3f(0,h,gridHeight); glVertex3f(gridWidth,h,gridHeight);
        glVertex3f(0,h,0); glVertex3f(0,h,gridHeight);
        glVertex3f(gridWidth,h,0); glVertex3f(gridWidth,h,gridHeight);
        glEnd();
        glEnable(GL_LIGHTING);
    }

    // ─── Entités ─────────────────────────────────────────────────────────────

    private void renderEntities(List<EntitySnapshot> entities) {
        // Compter les nutriments pour LOD adaptatif
        long nutrientCount = entities.stream().filter(e -> "NUTRIENT".equals(e.type())).count();
        int  nutrientStep  = nutrientCount > 1500 ? 3 : nutrientCount > 800 ? 2 : 1;

        // Rendu : nutriments (avec pas de LOD) → plantes → organismes
        int ni = 0;
        for (EntitySnapshot e : entities) {
            if ("NUTRIENT".equals(e.type())) {
                if (ni % nutrientStep == 0) renderNutrient(e);
                ni++;
            }
        }
        for (EntitySnapshot e : entities) if ("PLANT".equals(e.type()))    renderPlant(e);

        List<EntitySnapshot> orgs = entities.stream().filter(e -> "ORGANISM".equals(e.type())).toList();
        renderOrganismClusters(orgs);
    }

    // ─── Plante (algue) ───────────────────────────────────────────────────────

    private void renderPlant(EntitySnapshot e) {
        float growth = e.renderHeight();         // [0..1]
        float worldX = e.x() + 0.5f;
        float worldZ = e.y() + 0.5f;

        // worldMaxH transmis à drawSeaweed pour que la hauteur soit cohérente avec le monde
        float maxH   = worldMaxH();

        glPushMatrix();
        glTranslatef(worldX, 0f, worldZ);
        drawSeaweed(growth, System.currentTimeMillis() / 1000f, e.id() * 1337L, maxH);
        glPopMatrix();
    }

    // ─── Nutriment ────────────────────────────────────────────────────────────

    private void renderNutrient(EntitySnapshot e) {
        float time = System.currentTimeMillis() / 1000f;
        float x    = lerp(e.floatX(), prevPositions.get(e.id()), 0);
        float y    = lerp(e.floatZ(), prevPositions.get(e.id()), 2) * zScale();
        float z    = lerp(e.floatY(), prevPositions.get(e.id()), 1);

        float bob  = (float) Math.sin(time * 2.5f + e.id() * 0.07f) * 0.06f;
        float rot  = time * 45f + e.id() * 37f;

        glPushMatrix();
        glTranslatef(x, y + bob, z);
        glRotatef(rot, 0.4f, 1f, 0.3f);

        // Taille : légèrement réduite (0.08 → 0.18 selon énergie)
        float size = 0.08f + Math.min(e.energy() / 25f, 1f) * 0.10f;
        drawGlowingOrb(size, time, C_NUTRIENT, e.id());
        glPopMatrix();
    }

    // ─── Couleur ADN ─────────────────────────────────────────────────────────

    private float[] dnaColor(EntitySnapshot e) {
        int diet = (e.dna() != null && e.dna().length >= 8) ? (int) e.dna()[7] : 0;
        boolean female = "FEMALE".equals(e.gender());
        float[] color = female
                ? new float[]{0.95f, 0.35f, 0.75f}
                : new float[]{0.35f, 0.65f, 0.98f};
        switch (diet) {
            case 1 -> { color[1] = Math.min(1f, color[1]+0.35f); if (!female) color[2] = Math.max(0f, color[2]-0.2f); }
            case 2 -> { color[0] = Math.min(1f, color[0]+0.35f); if (female)  color[2] = Math.max(0f, color[2]-0.15f); }
            case 3 -> { color[0]=Math.max(0f,color[0]-0.3f); color[1]=Math.max(0f,color[1]-0.3f); color[2]=Math.max(0f,color[2]-0.3f);
                if (female) color[0]=Math.max(0.15f,color[0]); else color[2]=Math.max(0.15f,color[2]); }
        }
        return color;
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

    // ─── Organismes ───────────────────────────────────────────────────────────

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
            float sy = lerp(seed.floatZ(), prevPositions.get(seed.id()), 2) * zScale();

            for (EntitySnapshot other : organisms) {
                if (claimed.contains(other.id())) continue;
                float ox = lerp(other.floatX(), prevPositions.get(other.id()), 0);
                float oz = lerp(other.floatY(), prevPositions.get(other.id()), 1);
                float oy = lerp(other.floatZ(), prevPositions.get(other.id()), 2) * zScale();
                float d  = (float) Math.sqrt((sx-ox)*(sx-ox)+(sz-oz)*(sz-oz)+(sy-oy)*(sy-oy));
                if (d < CLUSTER_DIST) { cluster.add(other); claimed.add(other.id()); }
            }

            if (cluster.size() == 1) renderSingleOrganism(cluster.get(0));
            else                     renderMergedCluster(cluster);
        }
    }

    private void renderSingleOrganism(EntitySnapshot e) {
        float time   = System.currentTimeMillis() / 1000f;
        float[] color = dnaColor(e);
        float radius  = Math.max(0.28f, e.renderRadius());

        float x = lerp(e.floatX(), prevPositions.get(e.id()), 0);
        float z = lerp(e.floatY(), prevPositions.get(e.id()), 1);
        float y = lerp(e.floatZ(), prevPositions.get(e.id()), 2) * zScale() + radius * 0.5f;

        DNA dna    = DNA.fromArray(e.dna());
        float wobble = 0.085f + (dna != null ? dna.speed() * 0.07f : 0f);

        glPushMatrix();
        glTranslatef(x, y, z);

        float sx = 1f + (dna != null ? dna.speed()        * 0.09f : 0f);
        float sy = 0.88f - (dna != null ? dna.metabolism() * 0.12f : 0f);
        float sz = 1f + (dna != null ? dna.visionRadius()  * 0.06f : 0f);
        glScalef(sx, sy, sz);

        if (e.id() == selectedEntityId) {
            glDisable(GL_LIGHTING);
            glColor4f(1f, 0.95f, 0.4f, 0.4f);
            drawSphere(radius * 1.65f, 8, 10);
            glEnable(GL_LIGHTING);
        }

        float energyRatio = Math.min(1f, e.energy() / 180f);
        drawSlimeBlob(radius, time, wobble, SPHERE_STACKS, SPHERE_SLICES, color, 0.94f,
                dna != null ? dna.metabolism() : 1f, energyRatio, e.id());

        glPopMatrix();
    }

    private void renderMergedCluster(List<EntitySnapshot> cluster) {
        float avgX = 0, avgZ = 0, avgY = 0;
        float[] blended = {0f, 0f, 0f};
        float maxR = 0f;
        boolean hasSelected = false;

        for (EntitySnapshot e : cluster) {
            float[] prev = prevPositions.get(e.id());
            avgX += lerp(e.floatX(), prev, 0);
            avgZ += lerp(e.floatY(), prev, 1);
            avgY += lerp(e.floatZ(), prev, 2) * zScale();
            float[] c = dnaColor(e);
            blended[0] += c[0]; blended[1] += c[1]; blended[2] += c[2];
            float r = e.renderRadius() > 0.05f ? e.renderRadius() : 0.30f;
            maxR = Math.max(maxR, r);
            if (e.id() == selectedEntityId) hasSelected = true;
        }

        int n = cluster.size();
        avgX /= n; avgZ /= n; avgY /= n;
        blended[0] /= n; blended[1] /= n; blended[2] /= n;

        float coreR = maxR * (1f + 0.28f * Math.min(n-1, 5));
        float altY  = avgY + coreR * 0.8f;

        glPushMatrix();
        glTranslatef(avgX, altY, avgZ);
        glScalef(1.2f, 0.8f, 1.2f);

        if (hasSelected) {
            glDisable(GL_LIGHTING);
            glColor4f(C_SELECT[0], C_SELECT[1], C_SELECT[2], 0.50f);
            drawSphere(coreR * 1.45f, 6, 8);
            glEnable(GL_LIGHTING);
        }

        glColor3f(blended[0], blended[1], blended[2]);
        drawSphere(coreR, SPHERE_STACKS, SPHERE_SLICES);

        int sats = Math.min(n-1, 5);
        if (sats > 0) {
            float orbitAngle = (System.currentTimeMillis() % 7000) / 7000f * 360f;
            float orbitR     = coreR * 0.80f;
            float satSize    = maxR  * 0.52f;
            for (int i = 0; i < sats; i++) {
                float a  = (float) Math.toRadians(orbitAngle + i * 360f / sats);
                float ox = (float) Math.cos(a) * orbitR;
                float oz = (float) Math.sin(a) * orbitR;
                EntitySnapshot sat = cluster.get(i+1);
                float[] sc = dnaColor(sat);
                glPushMatrix();
                glTranslatef(ox, 0f, oz);
                glColor3f(sc[0], sc[1], sc[2]);
                drawSphere(satSize, 6, 8);
                glPopMatrix();
            }
        }
        glPopMatrix();
    }

    // ─── Idle / waiting ───────────────────────────────────────────────────────

    private void renderIdle() {
        float t = (System.currentTimeMillis() % 3000) / 3000f;
        float s = 0.5f + 0.3f * (float) Math.sin(t * Math.PI * 2);
        glDisable(GL_LIGHTING);
        glColor3f(0.1f*s, 0.15f*s, 0.4f*s);
        glPushMatrix(); glTranslatef(gridWidth/2f, 3f, gridHeight/2f);
        drawSphere(3f, 8, 10); glPopMatrix();
        glEnable(GL_LIGHTING);
        glfwSetWindowTitle(window, TITLE + " — En attente...");
    }

    private void renderWaiting() {
        float t = (System.currentTimeMillis() % 1500) / 1500f;
        float s = 0.3f + 0.2f * (float) Math.sin(t * Math.PI * 2);
        glDisable(GL_LIGHTING);
        glColor3f(s, s, s*1.5f);
        glPushMatrix(); glTranslatef(gridWidth/2f, 2f, gridHeight/2f);
        glRotatef(t*360f, 0f, 1f, 0f);
        drawOctahedron(2f); glPopMatrix();
        glEnable(GL_LIGHTING);
        glfwSetWindowTitle(window, TITLE + " — Chargement...");
    }

    // ─── HUD et stats ─────────────────────────────────────────────────────────

    private void updateTitle(GridSnapshot snap) {
        long orgs   = snap.entities().stream().filter(e -> "ORGANISM".equals(e.type())).count();
        long plants = snap.entities().stream().filter(e -> "PLANT".equals(e.type())).count();
        long nuts   = snap.entities().stream().filter(e -> "NUTRIENT".equals(e.type())).count();
        String sel = selectedEntityId >= 0 ? " [#"+selectedEntityId+"]" : "";
        String trk = camera.isTracking() ? "⊙" : "";
        glfwSetWindowTitle(window,
                "PixelLife 3D #%d | Step:%d | O:%d P:%d N:%d%s%s"
                        .formatted(snap.simulationId(), snap.step(), orgs, plants, nuts, sel, trk));
    }

    private void pushStats(GridSnapshot snap) {
        if (controlPanel == null || snap.step() == lastStatStep) return;
        lastStatStep = snap.step();
        int orgs = (int) snap.entities().stream().filter(e -> "ORGANISM".equals(e.type())).count();
        int plts = (int) snap.entities().stream().filter(e -> "PLANT".equals(e.type())).count();
        int nuts = (int) snap.entities().stream().filter(e -> "NUTRIENT".equals(e.type())).count();
        controlPanel.updateStats(snap.step(), orgs, plts, nuts);
        if (orgs == 0 && snap.step() > 0) controlPanel.notifySimulationEnded();
    }

    // ─── Fetch thread ─────────────────────────────────────────────────────────

    private void startFetchThread() {
        Thread.ofVirtual().name("renderer-fetch").start(() -> {
            while (!glfwWindowShouldClose(window)) {
                long sid = simulationId.get();
                if (sid >= 0) {
                    client.fetchGridSnapshot(sid).ifPresent(newSnap -> {
                        GridSnapshot old = currentSnapshot.get();
                        if (old != null) {
                            old.entities().forEach(e -> prevPositions.put(
                                    e.id(), new float[]{e.floatX(), e.floatY(), e.floatZ()}));
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

    private void cleanup() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private float interpT() {
        if (lastSnapshotTime == 0) return 1f;
        return Math.min(1f, (System.currentTimeMillis() - lastSnapshotTime) / (float) FETCH_INTERVAL_MS);
    }

    private float lerp(float current, float[] prev, int idx) {
        if (prev == null || idx >= prev.length) return current;
        float t = interpT();
        return prev[idx] + (current - prev[idx]) * t;
    }

    private static float[] lerpColor(float[] a, float[] b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new float[]{a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t, a[2]+(b[2]-a[2])*t};
    }
}