package com.kether.pixellife.frontend.ui;

import com.kether.pixellife.frontend.client.BackendClient;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Panneau de contrôle PixelLife — style steampunk.
 *
 * <h3>Sections</h3>
 * <ul>
 *   <li>LCD — métriques temps réel (step, populations)</li>
 *   <li>SIMULATION — démarrer / pause / stop</li>
 *   <li>VITESSE — délai entre ticks</li>
 *   <li>CONFIGURATION — grille, durée, graine, mutation</li>
 *   <li>ENTITÉS INITIALES — comptages au démarrage</li>
 *   <li>RÈGLES BIOLOGIQUES — paramètres fins des espèces (collapsible)</li>
 * </ul>
 */
public class ControlPanel extends JPanel {

    // ─── Palette ──────────────────────────────────────────────────────────────
    private static final Color METAL_DARK   = new Color(0x1A1712);
    private static final Color METAL_PLATE  = new Color(0x3A3228);
    private static final Color COPPER       = new Color(0xB87333);
    private static final Color COPPER_LIGHT = new Color(0xD4956A);
    private static final Color BRASS_LIGHT  = new Color(0xE8C96A);
    private static final Color LCD_GREEN    = new Color(0x39FF14);
    private static final Color LCD_AMBER    = new Color(0xFFB000);
    private static final Color LCD_RED      = new Color(0xFF3030);
    private static final Color LCD_BG       = new Color(0x0A140A);
    private static final Color RIVET        = new Color(0x8A7A60);
    private static final Color BORDER_METAL = new Color(0x5A4A38);

    private static final Font LCD_FONT   = new Font("Courier New", Font.BOLD, 22);
    private static final Font LABEL_FONT = new Font("Courier New", Font.BOLD, 10);
    private static final Font BTN_FONT   = new Font("Courier New", Font.BOLD, 11);

    // ─── Config grille ────────────────────────────────────────────────────────
    private JSpinner spinWidth, spinHeight, spinMaxSteps, spinSeed;
    private JSpinner spinPlantCount, spinNutrientCount, spinOrganismCount;
    private JSpinner spinMutationRate;
    private JLabel   lblMaxStepsHint;

    // ─── Contrôles ────────────────────────────────────────────────────────────
    private SteamButton  btnStart, btnStop, btnRandomize;
    private ToggleSwitch togglePause;
    private JSlider      speedSlider;
    private JLabel       lblSpeed;

    // ─── LCD ──────────────────────────────────────────────────────────────────
    private JLabel lcdStep, lcdStatus, lcdOrgs, lcdPlants, lcdNuts;

    // ─── Règles biologiques ───────────────────────────────────────────────────
    private BiologicalConfigPanel bioPanel;

    // ─── État ─────────────────────────────────────────────────────────────────
    private final BackendClient  client;
    private volatile long        currentSimId = -1;
    private volatile boolean     simRunning   = false;
    private final Consumer<Long> onSimStarted;

    private static final RandomGenerator RNG = RandomGeneratorFactory.getDefault().create();

    // ─── Construction ─────────────────────────────────────────────────────────

    public ControlPanel(BackendClient client, Consumer<Long> onSimStarted) {
        this.client       = client;
        this.onSimStarted = onSimStarted;

        setBackground(METAL_DARK);
        setPreferredSize(new Dimension(300, 1040));
        setBorder(new MetalBorder());
        setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));

        content.add(buildHeader());
        content.add(vgap(8));
        content.add(buildLCDBlock());
        content.add(vgap(8));
        content.add(buildControlBlock());
        content.add(vgap(8));
        content.add(buildSpeedBlock());
        content.add(vgap(8));
        content.add(buildConfigBlock());
        content.add(vgap(8));
        content.add(buildCountBlock());
        content.add(vgap(8));
        content.add(buildBioBlock());
        content.add(vgap(10));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setUI(new SteamScrollBarUI());
        add(scroll, BorderLayout.CENTER);

        syncButtons();
    }

    // ─── Header ───────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel p = platePanel(new BorderLayout(8, 0));
        p.setBorder(new CompoundBorder(new RivetBorder(), new EmptyBorder(8, 12, 8, 12)));
        JLabel title = new JLabel("PIXEL LIFE 3D");
        title.setFont(new Font("Courier New", Font.BOLD, 18));
        title.setForeground(BRASS_LIGHT);
        lcdStatus = lcdLabel("IDLE", LCD_AMBER);
        lcdStatus.setFont(new Font("Courier New", Font.BOLD, 13));
        p.add(title,               BorderLayout.WEST);
        p.add(lcdCell(lcdStatus, 80, 28), BorderLayout.EAST);
        return p;
    }

    // ─── LCD block ────────────────────────────────────────────────────────────

    private JPanel buildLCDBlock() {
        JPanel plate = platePanel(new GridLayout(2, 2, 6, 6));
        plate.setBorder(new CompoundBorder(new RivetBorder(), new EmptyBorder(8, 8, 8, 8)));
        lcdStep   = lcdLabel("—",  LCD_GREEN);
        lcdOrgs   = lcdLabel("0",  LCD_GREEN);
        lcdPlants = lcdLabel("0",  LCD_GREEN);
        lcdNuts   = lcdLabel("0",  LCD_AMBER);
        plate.add(namedLCD("STEP",      lcdStep));
        plate.add(namedLCD("ORGANISMS", lcdOrgs));
        plate.add(namedLCD("PLANTS",    lcdPlants));
        plate.add(namedLCD("NUTRIENTS", lcdNuts));
        return plate;
    }

    private JPanel namedLCD(String name, JLabel lcd) {
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.setOpaque(false);
        JLabel lbl = new JLabel(name);
        lbl.setFont(LABEL_FONT);
        lbl.setForeground(COPPER_LIGHT);
        p.add(lbl,               BorderLayout.NORTH);
        p.add(lcdCell(lcd,-1,38),BorderLayout.CENTER);
        return p;
    }

    // ─── Contrôles ────────────────────────────────────────────────────────────

    private JPanel buildControlBlock() {
        JPanel plate = platePanel(null);
        plate.setLayout(new BoxLayout(plate, BoxLayout.Y_AXIS));
        plate.setBorder(new CompoundBorder(new RivetBorder(), new EmptyBorder(8, 8, 8, 8)));
        sectionLabel(plate, "SIMULATION");

        JPanel row1 = opaqueRow(new GridLayout(1, 2, 8, 0));
        btnStart     = new SteamButton("▶ DÉMARRER",  new Color(0x2A5C2A), LCD_GREEN);
        btnRandomize = new SteamButton("⚄ ALÉATOIRE", new Color(0x5C4A10), LCD_AMBER);
        row1.add(btnStart); row1.add(btnRandomize);
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        plate.add(row1);
        plate.add(vgap(8));

        JPanel row2 = opaqueRow(new GridLayout(1, 2, 8, 0));
        togglePause = new ToggleSwitch("PAUSE");
        btnStop     = new SteamButton("■ STOP", new Color(0x5C1010), LCD_RED);
        togglePause.addItemListener(e -> {
            if (currentSimId < 0) return;
            if (togglePause.isSelected()) {
                client.sendPause(currentSimId);
                lcdStatus.setText("PAUSED"); lcdStatus.setForeground(LCD_AMBER);
            } else {
                client.sendResume(currentSimId);
                client.sendSetSpeed(currentSimId, speedSlider.getValue());
                lcdStatus.setText("RUNNING"); lcdStatus.setForeground(LCD_GREEN);
            }
        });
        row2.add(togglePause); row2.add(btnStop);
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        plate.add(row2);

        btnStart.addActionListener(e -> onStart());
        btnStop.addActionListener(e -> onStop());
        btnRandomize.addActionListener(e -> randomizeConfig());
        return plate;
    }

    // ─── Vitesse ──────────────────────────────────────────────────────────────

    private JPanel buildSpeedBlock() {
        JPanel plate = platePanel(null);
        plate.setLayout(new BoxLayout(plate, BoxLayout.Y_AXIS));
        plate.setBorder(new CompoundBorder(new RivetBorder(), new EmptyBorder(8, 8, 8, 8)));
        sectionLabel(plate, "VITESSE  (0 = max)");

        speedSlider = new JSlider(0, 500, 100);
        speedSlider.setOpaque(false);
        speedSlider.setForeground(COPPER);
        speedSlider.setEnabled(false);
        speedSlider.setUI(new SteamSliderUI(speedSlider));

        lblSpeed = new JLabel("100 ms/tick");
        lblSpeed.setFont(LABEL_FONT);
        lblSpeed.setForeground(LCD_GREEN);
        lblSpeed.setAlignmentX(LEFT_ALIGNMENT);

        speedSlider.addChangeListener((ChangeEvent ev) -> {
            int v = speedSlider.getValue();
            lblSpeed.setText(v == 0 ? "MAX" : v + " ms/tick");
            if (currentSimId >= 0 && simRunning) client.sendSetSpeed(currentSimId, v);
        });

        JPanel axisRow = opaqueRow(new BorderLayout(4, 0));
        axisRow.add(miniLabel("rapide"), BorderLayout.WEST);
        axisRow.add(speedSlider,          BorderLayout.CENTER);
        axisRow.add(miniLabel("lent"),    BorderLayout.EAST);
        axisRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        plate.add(axisRow);
        plate.add(vgap(4));
        plate.add(lblSpeed);
        return plate;
    }

    // ─── Config grille ────────────────────────────────────────────────────────

    private JPanel buildConfigBlock() {
        JPanel plate = platePanel(null);
        plate.setLayout(new BoxLayout(plate, BoxLayout.Y_AXIS));
        plate.setBorder(new CompoundBorder(new RivetBorder(), new EmptyBorder(8, 8, 8, 8)));
        sectionLabel(plate, "CONFIGURATION");

        spinWidth    = intSpin(80, 10, 500, 10);
        spinHeight   = intSpin(80, 10, 500, 10);
        spinSeed     = intSpin((int)(System.currentTimeMillis() % 100000), 0, 999999, 1);
        spinMaxSteps = new JSpinner(new SpinnerNumberModel(0, 0, 200000, 500));
        spinMutationRate = new JSpinner(new SpinnerNumberModel(0.05, 0.00, 0.50, 0.01));

        lblMaxStepsHint = new JLabel("∞ INFINI");
        lblMaxStepsHint.setFont(new Font("Courier New", Font.BOLD, 9));
        lblMaxStepsHint.setForeground(LCD_AMBER);
        lblMaxStepsHint.setAlignmentX(LEFT_ALIGNMENT);
        spinMaxSteps.addChangeListener(e -> {
            int v = (int) spinMaxSteps.getValue();
            lblMaxStepsHint.setText(v == 0 ? "∞ INFINI" : "steps max : " + v);
            lblMaxStepsHint.setForeground(v == 0 ? LCD_AMBER : COPPER_LIGHT);
        });

        plate.add(steamField("Largeur",   spinWidth));    plate.add(vgap(4));
        plate.add(steamField("Hauteur",   spinHeight));   plate.add(vgap(4));
        plate.add(steamField("Max steps", spinMaxSteps)); plate.add(vgap(2));
        plate.add(lblMaxStepsHint);                       plate.add(vgap(4));
        plate.add(steamField("Graine",    spinSeed));     plate.add(vgap(4));
        plate.add(steamField("Mutation",  spinMutationRate));
        return plate;
    }

    // ─── Entités initiales ────────────────────────────────────────────────────

    private JPanel buildCountBlock() {
        JPanel plate = platePanel(null);
        plate.setLayout(new BoxLayout(plate, BoxLayout.Y_AXIS));
        plate.setBorder(new CompoundBorder(new RivetBorder(), new EmptyBorder(8, 8, 8, 8)));
        sectionLabel(plate, "ENTITÉS INITIALES");

        spinPlantCount    = intSpin(600, 0, 50000, 50);
        spinNutrientCount = intSpin(300, 0, 50000, 50);
        spinOrganismCount = intSpin(40,  1, 5000,  5);

        plate.add(countField("Plantes",    new Color(0x20AA44), spinPlantCount));    plate.add(vgap(4));
        plate.add(countField("Nutriments", new Color(0xDD9910), spinNutrientCount)); plate.add(vgap(4));
        plate.add(countField("Organismes", new Color(0x3388FF), spinOrganismCount));

        JLabel warn = new JLabel("");
        warn.setFont(new Font("Courier New", Font.BOLD, 9));
        warn.setForeground(LCD_RED);
        warn.setAlignmentX(LEFT_ALIGNMENT);
        plate.add(vgap(4));
        plate.add(warn);

        javax.swing.event.ChangeListener wl = e -> {
            int total = (int)spinWidth.getValue() * (int)spinHeight.getValue();
            int used  = (int)spinPlantCount.getValue()
                    + (int)spinNutrientCount.getValue()
                    + (int)spinOrganismCount.getValue();
            warn.setText(used > total
                    ? "⚠ %d entités > %d cellules".formatted(used, total) : "");
        };
        spinPlantCount.addChangeListener(wl);
        spinNutrientCount.addChangeListener(wl);
        spinOrganismCount.addChangeListener(wl);
        spinWidth.addChangeListener(wl);
        spinHeight.addChangeListener(wl);
        return plate;
    }

    // ─── Règles biologiques (collapsible) ─────────────────────────────────────

    private JPanel buildBioBlock() {
        JPanel plate = platePanel(null);
        plate.setLayout(new BoxLayout(plate, BoxLayout.Y_AXIS));
        plate.setBorder(new CompoundBorder(new RivetBorder(), new EmptyBorder(8, 8, 8, 8)));

        // En-tête cliquable
        JPanel header = opaqueRow(new BorderLayout(8, 0));
        JButton toggleBtn = new SteamButton("▸ RÈGLES BIOLOGIQUES", new Color(0x1C2A3A), LCD_AMBER);
        toggleBtn.setAlignmentX(LEFT_ALIGNMENT);
        toggleBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        header.add(toggleBtn, BorderLayout.CENTER);

        // Bouton reset
        JButton resetBtn = new SteamButton("↺", new Color(0x2A2A2A), COPPER_LIGHT);
        resetBtn.setPreferredSize(new Dimension(36, 36));
        resetBtn.setMaximumSize(new Dimension(36, 36));
        header.add(resetBtn, BorderLayout.EAST);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        // Panneau biologique (caché par défaut)
        bioPanel = new BiologicalConfigPanel();
        bioPanel.setVisible(false);

        toggleBtn.addActionListener(e -> {
            boolean visible = !bioPanel.isVisible();
            bioPanel.setVisible(visible);
            toggleBtn.setText((visible ? "▾ " : "▸ ") + "RÈGLES BIOLOGIQUES");
            revalidate(); repaint();
        });

        resetBtn.addActionListener(e -> bioPanel.resetToDefaults());

        plate.add(header);
        plate.add(vgap(4));
        plate.add(bioPanel);
        return plate;
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    private void onStart() {
        long simId = client.sendStartWithConfig(buildJson());
        if (simId < 0) {
            JOptionPane.showMessageDialog(this,
                    "Impossible de démarrer.\nVérifiez que le backend est lancé.",
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }
        connectToSim(simId);
    }

    public void connectToSim(long simId) {
        currentSimId = simId;
        simRunning   = true;
        client.sendSetSpeed(simId, speedSlider.getValue());
        lcdStatus.setText("RUNNING"); lcdStatus.setForeground(LCD_GREEN);
        syncButtons();
        onSimStarted.accept(simId);
    }

    private void onStop() {
        if (currentSimId < 0) return;
        int ok = JOptionPane.showConfirmDialog(this,
                "Arrêter la simulation #" + currentSimId + " ?",
                "Confirmation", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        client.sendStop(currentSimId);
        simRunning = false;
        lcdStatus.setText("STOPPED"); lcdStatus.setForeground(LCD_RED);
        syncButtons();
    }

    private void randomizeConfig() {
        spinWidth.setValue(   30 + RNG.nextInt(100));
        spinHeight.setValue(  30 + RNG.nextInt(100));
        spinMaxSteps.setValue(RNG.nextBoolean() ? 0 : 1000 + RNG.nextInt(9000));
        spinSeed.setValue(RNG.nextInt(999999));

        int w = (int)spinWidth.getValue(), h = (int)spinHeight.getValue();
        int cells = w * h;
        spinPlantCount   .setValue(Math.max(10, (int)(cells * (0.05f + RNG.nextFloat() * 0.15f))));
        spinNutrientCount.setValue(Math.max(5,  (int)(cells * (0.02f + RNG.nextFloat() * 0.08f))));
        spinOrganismCount.setValue(Math.max(5,  (int)(cells * (0.003f + RNG.nextFloat() * 0.01f))));
        spinMutationRate .setValue(Math.round((0.01f + RNG.nextFloat() * 0.15f) * 100) / 100.0);
        speedSlider.setValue(RNG.nextInt(301));
    }

    // ─── LCD update ───────────────────────────────────────────────────────────

    public void updateStats(long step, int organisms, int plants, int nutrients) {
        SwingUtilities.invokeLater(() -> {
            boolean infinite = ((int) spinMaxSteps.getValue()) == 0;
            lcdStep.setText(infinite ? step + "∞" : String.valueOf(step));
            lcdOrgs.setText(String.valueOf(organisms));
            lcdPlants.setText(String.valueOf(plants));
            lcdNuts.setText(String.valueOf(nutrients));
        });
    }

    public void notifySimulationEnded() {
        SwingUtilities.invokeLater(() -> {
            simRunning = false;
            lcdStatus.setText("FINISHED"); lcdStatus.setForeground(new Color(0x888888));
            syncButtons();
        });
    }

    // ─── Sérialisation JSON ───────────────────────────────────────────────────

    private String buildJson() {
        return String.format(Locale.US,
                "{\"config\":{\"width\":%d,\"height\":%d," +
                        "\"plantCount\":%d,\"nutrientCount\":%d,\"organismCount\":%d," +
                        "\"maxSteps\":%d,\"seed\":%d,\"mutationRate\":%.2f,%s}}",
                (int)spinWidth.getValue(),        (int)spinHeight.getValue(),
                (int)spinPlantCount.getValue(),   (int)spinNutrientCount.getValue(),
                (int)spinOrganismCount.getValue(),
                (int)spinMaxSteps.getValue(),     (int)spinSeed.getValue(),
                ((Number)spinMutationRate.getValue()).doubleValue(),
                bioPanel.toJson());
    }

    // ─── Sync boutons ─────────────────────────────────────────────────────────

    private void syncButtons() {
        SwingUtilities.invokeLater(() -> {
            btnStart.setEnabled(!simRunning);
            btnRandomize.setEnabled(!simRunning);
            togglePause.setEnabled(simRunning);
            btnStop.setEnabled(simRunning);
            speedSlider.setEnabled(simRunning);
            if (!simRunning) togglePause.setSelected(false);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers UI
    // ═══════════════════════════════════════════════════════════════════════════

    private JPanel platePanel(LayoutManager lm) {
        JPanel p = lm != null ? new JPanel(lm) : new JPanel();
        p.setBackground(METAL_PLATE);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return p;
    }

    private JPanel opaqueRow(LayoutManager lm) {
        JPanel p = lm != null ? new JPanel(lm) : new JPanel();
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private void sectionLabel(JPanel parent, String text) {
        JLabel l = new JLabel("— " + text + " —");
        l.setFont(new Font("Courier New", Font.BOLD, 9));
        l.setForeground(COPPER);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 6, 0));
        parent.add(l);
    }

    private JLabel lcdLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(LCD_FONT);
        l.setForeground(color);
        l.setBackground(LCD_BG);
        l.setOpaque(true);
        l.setBorder(new EmptyBorder(2, 6, 2, 6));
        return l;
    }

    private JPanel lcdCell(JLabel content, int w, int h) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(LCD_BG);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_METAL, 2),
                BorderFactory.createLineBorder(new Color(0x050805), 1)));
        p.add(content, BorderLayout.CENTER);
        p.setPreferredSize(new Dimension(w > 0 ? w : 120, h));
        return p;
    }

    private JPanel steamField(String label, JSpinner spinner) {
        JPanel row = opaqueRow(new BorderLayout(8, 0));
        JLabel lbl = new JLabel(label);
        lbl.setFont(LABEL_FONT);
        lbl.setForeground(COPPER_LIGHT);
        lbl.setPreferredSize(new Dimension(72, 24));
        styleSteamSpinner(spinner);
        row.add(lbl,     BorderLayout.WEST);
        row.add(spinner, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return row;
    }

    private JPanel countField(String label, Color accent, JSpinner spinner) {
        JPanel row = opaqueRow(new BorderLayout(8, 0));
        JPanel left = opaqueRow(new BorderLayout(4, 0));
        JLabel dot = new JLabel("●");
        dot.setForeground(accent);
        dot.setFont(new Font("Dialog", Font.PLAIN, 12));
        JLabel lbl = new JLabel(label);
        lbl.setFont(LABEL_FONT);
        lbl.setForeground(COPPER_LIGHT);
        left.add(dot, BorderLayout.WEST);
        left.add(lbl, BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(88, 24));
        styleSteamSpinner(spinner);
        row.add(left,    BorderLayout.WEST);
        row.add(spinner, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return row;
    }

    private void styleSteamSpinner(JSpinner s) {
        s.setBackground(LCD_BG);
        s.setBorder(BorderFactory.createLineBorder(BORDER_METAL, 1));
        if (s.getEditor() instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setBackground(LCD_BG);
            de.getTextField().setForeground(LCD_GREEN);
            de.getTextField().setFont(new Font("Courier New", Font.BOLD, 12));
            de.getTextField().setCaretColor(LCD_GREEN);
            de.getTextField().setBorder(new EmptyBorder(2, 4, 2, 4));
        }
    }

    private JLabel miniLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Courier New", Font.PLAIN, 9));
        l.setForeground(COPPER);
        return l;
    }

    private Component vgap(int h) { return Box.createVerticalStrut(h); }

    private JSpinner intSpin(int v, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(v, min, max, step));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Composants custom steampunk
    // ═══════════════════════════════════════════════════════════════════════════

    private static class SteamButton extends JButton {
        private final Color baseBg, fgColor;
        SteamButton(String text, Color bg, Color fg) {
            super(text); this.baseBg = bg; this.fgColor = fg;
            setFont(BTN_FONT); setForeground(fg); setBackground(bg);
            setFocusPainted(false); setBorderPainted(false); setOpaque(false);
            setPreferredSize(new Dimension(0, 40));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) repaint(); }
                @Override public void mouseExited(MouseEvent e)  { repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hover = getModel().isRollover() && isEnabled();
            Color bg = hover ? baseBg.brighter() : baseBg;
            g2.setColor(bg.darker()); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2.setColor(bg);          g2.fillRoundRect(1, 1, getWidth()-2, getHeight()-3, 5, 5);
            g2.setColor(BORDER_METAL);g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 6, 6);
            drawRivet(g2, 5, 5); drawRivet(g2, getWidth()-7, 5);
            drawRivet(g2, 5, getHeight()-7); drawRivet(g2, getWidth()-7, getHeight()-7);
            g2.setFont(getFont()); g2.setColor(isEnabled() ? fgColor : fgColor.darker().darker());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (getWidth()-fm.stringWidth(getText()))/2,
                    (getHeight()+fm.getAscent()-fm.getDescent())/2);
            g2.dispose();
        }
        private void drawRivet(Graphics2D g2, int x, int y) {
            g2.setColor(RIVET); g2.fillOval(x-2, y-2, 5, 5);
            g2.setColor(BORDER_METAL); g2.drawOval(x-2, y-2, 4, 4);
            g2.setColor(new Color(255,255,255,80)); g2.fillOval(x-1, y-2, 2, 2);
        }
        @Override public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, 40);
        }
    }

    private static class ToggleSwitch extends JToggleButton {
        ToggleSwitch(String label) {
            super(label);
            setFont(BTN_FONT); setForeground(COPPER_LIGHT);
            setFocusPainted(false); setBorderPainted(false); setOpaque(false);
            setPreferredSize(new Dimension(120, 46));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = isSelected(); int w = getWidth(), h = getHeight();
            g2.setColor(METAL_PLATE); g2.fillRoundRect(0, 0, w, h, 8, 8);
            g2.setColor(BORDER_METAL); g2.drawRoundRect(0, 0, w-1, h-1, 8, 8);
            int sw = 32, sh = 18, sx = 8, sy = (h-sh)/2;
            g2.setColor(METAL_DARK); g2.fillRoundRect(sx, sy, sw, sh, 4, 4);
            g2.setColor(BORDER_METAL); g2.drawRoundRect(sx, sy, sw, sh, 4, 4);
            int lw = 12, lh = sh-4, lx = on ? sx+sw-lw-3 : sx+3;
            g2.setColor(on ? LCD_AMBER : new Color(0x555555));
            g2.fillRoundRect(lx, sy+2, lw, lh, 3, 3);
            g2.setColor(new Color(255,255,255,60)); g2.fillRoundRect(lx+1, sy+2, lw/2, lh/2, 2, 2);
            drawRivet(g2, sx-5, h/2); drawRivet(g2, sx+sw+5, h/2);
            g2.setFont(getFont());
            g2.setColor(isEnabled() ? (on ? LCD_AMBER : COPPER) : BORDER_METAL);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), sx+sw+14, (h+fm.getAscent()-fm.getDescent())/2);
            g2.dispose();
        }
        private void drawRivet(Graphics2D g2, int x, int y) {
            g2.setColor(RIVET); g2.fillOval(x-3, y-3, 6, 6);
            g2.setColor(BORDER_METAL); g2.drawOval(x-3, y-3, 5, 5);
        }
        @Override public Dimension getPreferredSize() {
            return new Dimension(getParent() != null ? getParent().getWidth()-80 : 120, 46);
        }
    }

    private static class RivetBorder extends AbstractBorder {
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BORDER_METAL.darker()); g2.drawRoundRect(x, y, w-1, h-1, 6, 6);
            g2.setColor(BORDER_METAL);          g2.drawRoundRect(x+1, y+1, w-3, h-3, 5, 5);
            drawRivet(g2, x+6, y+6); drawRivet(g2, x+w-7, y+6);
            drawRivet(g2, x+6, y+h-7); drawRivet(g2, x+w-7, y+h-7);
            g2.dispose();
        }
        private void drawRivet(Graphics2D g2, int cx, int cy) {
            g2.setColor(RIVET.darker()); g2.fillOval(cx-3, cy-3, 7, 7);
            g2.setColor(RIVET); g2.fillOval(cx-2, cy-2, 5, 5);
            g2.setColor(new Color(255,255,255,90)); g2.fillOval(cx-1, cy-2, 2, 2);
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(8, 8, 8, 8); }
    }

    private static class MetalBorder extends AbstractBorder {
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(COPPER.darker()); g2.drawRect(x, y, w-1, h-1);
            g2.setColor(COPPER);          g2.drawRect(x+1, y+1, w-3, h-3);
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(2, 2, 2, 2); }
    }

    private static class SteamScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override protected void configureScrollBarColors() { thumbColor = COPPER.darker(); trackColor = METAL_DARK; }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        private JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0,0)); return b; }
    }

    private static class SteamSliderUI extends javax.swing.plaf.basic.BasicSliderUI {
        SteamSliderUI(JSlider s) { super(s); }
        @Override public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Rectangle t = trackRect;
            g2.setColor(METAL_DARK);   g2.fillRoundRect(t.x, t.y+t.height/2-3, t.width, 6, 3, 3);
            g2.setColor(BORDER_METAL); g2.drawRoundRect(t.x, t.y+t.height/2-3, t.width, 6, 3, 3);
            g2.dispose();
        }
        @Override public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle t = thumbRect;
            g2.setColor(COPPER);       g2.fillRoundRect(t.x+2, t.y+2, t.width-4, t.height-4, 4, 4);
            g2.setColor(COPPER_LIGHT); g2.fillRoundRect(t.x+3, t.y+3, t.width-6, (t.height-6)/2, 3, 3);
            g2.setColor(BORDER_METAL); g2.drawRoundRect(t.x+2, t.y+2, t.width-4, t.height-4, 4, 4);
            g2.dispose();
        }
    }
}