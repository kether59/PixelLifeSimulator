package com.kether.pixellife.frontend.ui;

import com.kether.pixellife.common.model.BiologicalConfig;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.Locale;

/**
 * Panneau de configuration des règles biologiques — style steampunk.
 *
 * <p>Expose les paramètres biologiques clés de chaque espèce sous forme de spinners,
 * permettant de les ajuster avant chaque lancement de simulation.</p>
 *
 * <p>Les valeurs par défaut sont issues de {@link BiologicalConfig#defaults()}.
 * Utilisez {@link #toBiologicalConfig()} pour récupérer la configuration courante,
 * ou {@link #toJson()} pour l'inclure directement dans la requête REST.</p>
 */
public class BiologicalConfigPanel extends JPanel {

    // ─── Palette (identique à ControlPanel) ──────────────────────────────────
    private static final Color METAL_PLATE  = new Color(0x3A3228);
    private static final Color COPPER       = new Color(0xB87333);
    private static final Color COPPER_LIGHT = new Color(0xD4956A);
    private static final Color LCD_GREEN    = new Color(0x39FF14);
    private static final Color LCD_BG       = new Color(0x0A140A);
    private static final Color BORDER_METAL = new Color(0x5A4A38);
    private static final Color RIVET_COLOR  = new Color(0x8A7A60);
    private static final Font  LABEL_FONT   = new Font("Courier New", Font.BOLD, 10);

    // ─── Spinners — Plantes ───────────────────────────────────────────────────
    private final JSpinner spinPlantEnergyMax;
    private final JSpinner spinPlantMetabolism;
    private final JSpinner spinPlantMaxHeight;
    private final JSpinner spinPlantBaseBite;
    private final JSpinner spinPlantPhotoBase;

    // ─── Spinners — Organismes ────────────────────────────────────────────────
    private final JSpinner spinOrgEnergyMax;
    private final JSpinner spinOrgEnergyStart;
    private final JSpinner spinOrgReproCost;
    private final JSpinner spinOrgMaxAge;
    private final JSpinner spinOrgAgePenaltyStart;

    // ─── Spinners — Nutriments ────────────────────────────────────────────────
    private final JSpinner spinNutDriftSpeed;
    private final JSpinner spinNutMetabolism;
    private final JSpinner spinNutDrag;

    // ─── Construction ─────────────────────────────────────────────────────────

    public BiologicalConfigPanel() {
        BiologicalConfig def = BiologicalConfig.defaults();

        spinPlantEnergyMax     = floatSpin(def.plantEnergyMax(),          50,   500,  10);
        spinPlantMetabolism    = floatSpin(def.plantMetabolism(),          0.01, 1.0,  0.01);
        spinPlantMaxHeight     = floatSpin(def.plantMaxHeight(),           4,    32,   1);
        spinPlantBaseBite      = floatSpin(def.plantBaseBite(),            1,    50,   1);
        spinPlantPhotoBase     = floatSpin(def.plantPhotosynthesisBase(),  0.05, 1.0,  0.05);

        spinOrgEnergyMax       = floatSpin(def.organismEnergyMax(),        50,   500,  10);
        spinOrgEnergyStart     = floatSpin(def.organismEnergyStart(),      1,    50,   1);
        spinOrgReproCost       = floatSpin(def.organismReproCost(),        5,    100,  5);
        spinOrgMaxAge          = intSpin  (def.organismMaxAge(),           50,   1000, 50);
        spinOrgAgePenaltyStart = intSpin  (def.organismAgePenaltyStart(),  20,   500,  10);

        spinNutDriftSpeed      = floatSpin(def.nutrientDriftSpeed(),       0.01, 1.0,  0.01);
        spinNutMetabolism      = floatSpin(def.nutrientMetabolism(),       0.01, 0.5,  0.01);
        spinNutDrag            = floatSpin(def.nutrientDrag(),             0.50, 0.99, 0.01);

        setBackground(METAL_PLATE);
        setAlignmentX(LEFT_ALIGNMENT);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(4, 0, 4, 0));

        sectionLabel(this, "PLANTES");
        add(row("Énergie max",    spinPlantEnergyMax));  add(vgap(3));
        add(row("Métabolisme",    spinPlantMetabolism)); add(vgap(3));
        add(row("Hauteur max",    spinPlantMaxHeight));  add(vgap(3));
        add(row("Morsure base",   spinPlantBaseBite));   add(vgap(3));
        add(row("Photosynthèse",  spinPlantPhotoBase));  add(vgap(8));

        sectionLabel(this, "ORGANISMES");
        add(row("Énergie max",    spinOrgEnergyMax));       add(vgap(3));
        add(row("Énergie départ", spinOrgEnergyStart));     add(vgap(3));
        add(row("Coût repro.",    spinOrgReproCost));       add(vgap(3));
        add(row("Âge maximal",    spinOrgMaxAge));          add(vgap(3));
        add(row("Début pénalité", spinOrgAgePenaltyStart)); add(vgap(8));

        sectionLabel(this, "NUTRIMENTS");
        add(row("Vitesse dérive", spinNutDriftSpeed)); add(vgap(3));
        add(row("Dégradation",    spinNutMetabolism)); add(vgap(3));
        add(row("Friction",       spinNutDrag));
    }

    // ─── Lecture de la configuration ─────────────────────────────────────────

    /** Construit un {@link BiologicalConfig} depuis les valeurs courantes. */
    public BiologicalConfig toBiologicalConfig() {
        BiologicalConfig def = BiologicalConfig.defaults();
        return new BiologicalConfig(
                fv(spinPlantEnergyMax),
                fv(spinPlantMetabolism),
                fv(spinPlantMaxHeight),
                fv(spinPlantPhotoBase),
                fv(spinPlantBaseBite),
                def.plantRobustnessFactor(),        // non exposé — trop technique
                fv(spinOrgEnergyMax),
                fv(spinOrgEnergyStart),
                fv(spinOrgReproCost),
                def.organismReproCooldown(),        // non exposé
                iv(spinOrgAgePenaltyStart),
                iv(spinOrgMaxAge),
                def.organismMergeThreshold(),       // non exposé
                fv(spinNutDriftSpeed),
                def.nutrientBrownianForce(),        // non exposé
                fv(spinNutDrag),
                fv(spinNutMetabolism)
        );
    }

    /**
     * Génère le fragment JSON {@code "biologicalConfig":{...}} à insérer
     * dans le corps de la requête de démarrage.
     */
    public String toJson() {
        BiologicalConfig b = toBiologicalConfig();
        return String.format(Locale.US,
                "\"biologicalConfig\":{" +
                        "\"plantEnergyMax\":%.1f," +
                        "\"plantMetabolism\":%.3f," +
                        "\"plantMaxHeight\":%.1f," +
                        "\"plantPhotosynthesisBase\":%.3f," +
                        "\"plantBaseBite\":%.1f," +
                        "\"plantRobustnessFactor\":%.2f," +
                        "\"organismEnergyMax\":%.1f," +
                        "\"organismEnergyStart\":%.1f," +
                        "\"organismReproCost\":%.1f," +
                        "\"organismReproCooldown\":%d," +
                        "\"organismAgePenaltyStart\":%d," +
                        "\"organismMaxAge\":%d," +
                        "\"organismMergeThreshold\":%.2f," +
                        "\"nutrientDriftSpeed\":%.3f," +
                        "\"nutrientBrownianForce\":%.3f," +
                        "\"nutrientDrag\":%.3f," +
                        "\"nutrientMetabolism\":%.3f}",
                b.plantEnergyMax(), b.plantMetabolism(), b.plantMaxHeight(),
                b.plantPhotosynthesisBase(), b.plantBaseBite(), b.plantRobustnessFactor(),
                b.organismEnergyMax(), b.organismEnergyStart(), b.organismReproCost(),
                b.organismReproCooldown(), b.organismAgePenaltyStart(), b.organismMaxAge(),
                b.organismMergeThreshold(),
                b.nutrientDriftSpeed(), b.nutrientBrownianForce(),
                b.nutrientDrag(), b.nutrientMetabolism()
        );
    }

    /** Remet tous les spinners aux valeurs par défaut. */
    public void resetToDefaults() {
        BiologicalConfig def = BiologicalConfig.defaults();
        spinPlantEnergyMax    .setValue((double) def.plantEnergyMax());
        spinPlantMetabolism   .setValue((double) def.plantMetabolism());
        spinPlantMaxHeight    .setValue((double) def.plantMaxHeight());
        spinPlantBaseBite     .setValue((double) def.plantBaseBite());
        spinPlantPhotoBase    .setValue((double) def.plantPhotosynthesisBase());
        spinOrgEnergyMax      .setValue((double) def.organismEnergyMax());
        spinOrgEnergyStart    .setValue((double) def.organismEnergyStart());
        spinOrgReproCost      .setValue((double) def.organismReproCost());
        spinOrgMaxAge         .setValue(def.organismMaxAge());
        spinOrgAgePenaltyStart.setValue(def.organismAgePenaltyStart());
        spinNutDriftSpeed     .setValue((double) def.nutrientDriftSpeed());
        spinNutMetabolism     .setValue((double) def.nutrientMetabolism());
        spinNutDrag           .setValue((double) def.nutrientDrag());
    }

    // ─── Helpers UI ───────────────────────────────────────────────────────────

    private JPanel row(String label, JSpinner spinner) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(label);
        lbl.setFont(LABEL_FONT);
        lbl.setForeground(COPPER_LIGHT);
        lbl.setPreferredSize(new Dimension(95, 24));
        styleSpinner(spinner);
        p.add(lbl, BorderLayout.WEST);
        p.add(spinner, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return p;
    }

    private void sectionLabel(JPanel parent, String text) {
        JLabel l = new JLabel("— " + text + " —");
        l.setFont(new Font("Courier New", Font.BOLD, 9));
        l.setForeground(COPPER);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        parent.add(l);
    }

    private void styleSpinner(JSpinner s) {
        s.setBackground(LCD_BG);
        s.setBorder(BorderFactory.createLineBorder(BORDER_METAL, 1));
        if (s.getEditor() instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setBackground(LCD_BG);
            de.getTextField().setForeground(LCD_GREEN);
            de.getTextField().setFont(new Font("Courier New", Font.BOLD, 11));
            de.getTextField().setCaretColor(LCD_GREEN);
            de.getTextField().setBorder(new EmptyBorder(2, 4, 2, 4));
        }
    }

    private JSpinner floatSpin(double val, double min, double max, double step) {
        return new JSpinner(new SpinnerNumberModel(val, min, max, step));
    }

    private JSpinner intSpin(int val, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(val, min, max, step));
    }

    private float fv(JSpinner s) { return ((Number) s.getValue()).floatValue(); }
    private int   iv(JSpinner s) { return ((Number) s.getValue()).intValue(); }
    private Component vgap(int h) { return Box.createVerticalStrut(h); }

    // ─── Bordure rivetée (cohérente avec ControlPanel) ────────────────────────

    static class RivetBorder extends AbstractBorder {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BORDER_METAL.darker()); g2.drawRoundRect(x,   y,   w-1, h-1, 6, 6);
            g2.setColor(BORDER_METAL);          g2.drawRoundRect(x+1, y+1, w-3, h-3, 5, 5);
            drawRivet(g2, x+6, y+6); drawRivet(g2, x+w-7, y+6);
            drawRivet(g2, x+6, y+h-7); drawRivet(g2, x+w-7, y+h-7);
            g2.dispose();
        }
        private void drawRivet(Graphics2D g2, int cx, int cy) {
            g2.setColor(RIVET_COLOR.darker()); g2.fillOval(cx-3, cy-3, 7, 7);
            g2.setColor(RIVET_COLOR);          g2.fillOval(cx-2, cy-2, 5, 5);
            g2.setColor(new Color(255,255,255,90)); g2.fillOval(cx-1, cy-2, 2, 2);
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(8, 8, 8, 8); }
    }
}