package com.kether.pixellife.frontend;

import com.kether.pixellife.frontend.client.BackendClient;
import com.kether.pixellife.frontend.render.SimulationRenderer;
import com.kether.pixellife.frontend.ui.ControlPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Point d'entrée du frontend PixelLife.
 *
 * Arguments CLI :
 *   args[0] = URL du backend  (défaut : http://localhost:8080)
 *   args[1] = simId à afficher immédiatement (optionnel, passé par FrontendLauncher)
 *
 * Correction v3 : args[1] est maintenant lu et transmis au ControlPanel
 * via connectToSim(), ce qui déclenche switchToSimulation() dans le renderer.
 */
public class PixelLifeFrontend {

    public static void main(String[] args) {
        String backendUrl = args.length > 0 ? args[0] : "http://localhost:8080";

        // Optionnel : simId pré-démarré par FrontendLauncher (args[1])
        long initialSimId = -1;
        if (args.length > 1) {
            try { initialSimId = Long.parseLong(args[1]); }
            catch (NumberFormatException ignored) {}
        }
        final long preloadedSimId = initialSimId;

        applyDarkLookAndFeel();

        BackendClient client = new BackendClient(backendUrl);
        SimulationRenderer renderer = new SimulationRenderer(client);

        ControlPanel[] panelRef = new ControlPanel[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                ControlPanel panel = new ControlPanel(client, renderer::switchToSimulation);
                panelRef[0] = panel;
                renderer.setControlPanel(panel);

                JFrame frame = new JFrame("PixelLife — Contrôles");
                frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                frame.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosing(WindowEvent e) {
                        frame.dispose();
                        System.exit(0);
                    }
                });
                frame.getContentPane().setBackground(new Color(0x111318));
                frame.getContentPane().add(panel, BorderLayout.CENTER);
                frame.pack();
                frame.setMinimumSize(new Dimension(300, 600));
                frame.setLocation(10, 50);
                frame.setVisible(true);

                // Si un simId a été passé en argument, on s'y connecte directement
                if (preloadedSimId >= 0) {
                    panel.connectToSim(preloadedSimId);
                }
            });
        } catch (Exception ex) {
            System.err.println("Erreur init Swing : " + ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }

        renderer.run();
        System.exit(0);
    }

    private static void applyDarkLookAndFeel() {
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatDarkLaf");
            return;
        } catch (Exception ignored) {}
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            UIManager.put("Panel.background",      new Color(0x111318));
            UIManager.put("ScrollPane.background", new Color(0x111318));
            UIManager.put("Viewport.background",   new Color(0x111318));
            UIManager.put("TextField.background",  new Color(0x191C24));
            UIManager.put("TextField.foreground",  new Color(0xDDE1EC));
            UIManager.put("Spinner.background",    new Color(0x191C24));
            UIManager.put("Button.background",     new Color(0x2A2D3A));
            UIManager.put("Button.foreground",     new Color(0xDDE1EC));
            UIManager.put("Label.foreground",      new Color(0xDDE1EC));
        } catch (Exception ignored) {}
    }
}