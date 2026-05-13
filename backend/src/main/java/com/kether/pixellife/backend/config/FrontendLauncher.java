package com.kether.pixellife.backend.config;

import com.kether.pixellife.backend.service.SimulationService;
import com.kether.pixellife.common.model.SimulationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lance automatiquement le frontend OpenGL après le démarrage complet du backend.
 *
 * <p>Déclenché par {@link ApplicationReadyEvent} (Tomcat prêt, routes enregistrées).
 * Le process frontend est lancé dans un virtual thread — non bloquant pour Spring.</p>
 *
 * <h3>Activation</h3>
 * <pre>
 *   pixellife.frontend.auto-launch=true
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FrontendLauncher {

    private final FrontendLauncherProperties props;
    private final SimulationService simulationService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!props.autoLaunch()) {
            log.debug("Lancement automatique du frontend désactivé (auto-launch=false)");
            return;
        }
        Thread.ofVirtual().name("frontend-launcher").start(this::launchFrontend);
    }

    // ─── Résolution des ressources ────────────────────────────────────────────

    private Path resolveFrontendJar() {
        if (props.jarPath() != null && !props.jarPath().isBlank()) {
            Path explicit = Path.of(props.jarPath());
            if (Files.exists(explicit)) return explicit;
            log.warn("Jar frontend introuvable au chemin configuré : {}", explicit);
        }

        Path[] candidates = {
                Path.of("../frontend/target/frontend-1.0-SNAPSHOT.jar"),
                Path.of("frontend/target/frontend-1.0-SNAPSHOT.jar"),
                Path.of("./frontend-1.0-SNAPSHOT.jar")
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                log.info("Jar frontend trouvé : {}", candidate.toAbsolutePath());
                return candidate.toAbsolutePath();
            }
        }
        return null;
    }

    private long resolveSimulationId() {
        if (props.autoStartSimulation()) {
            var response = simulationService.startSimulation(SimulationConfig.defaults());
            log.info("Simulation #{} démarrée pour le frontend", response.simulationId());
            return response.simulationId();
        }

        if (props.simulationId() > 0) return props.simulationId();

        var simulations = simulationService.listSimulations();
        if (!simulations.isEmpty()) {
            long lastId = simulations.getFirst().id();
            log.info("Utilisation de la dernière simulation en base : #{}", lastId);
            return lastId;
        }

        log.info("Aucune simulation existante, création automatique...");
        return simulationService.startSimulation(SimulationConfig.defaults()).simulationId();
    }

    // ─── Lancement du process ─────────────────────────────────────────────────

    private void launchFrontend() {
        Path jarPath = resolveFrontendJar();
        if (jarPath == null) {
            log.error("""
                    Impossible de localiser le jar frontend.
                    Compilez-le d'abord : cd frontend && mvn package -DskipTests
                    Ou configurez : pixellife.frontend.jar-path=/chemin/vers/frontend.jar
                    """);
            return;
        }

        long simId = resolveSimulationId();
        List<String> command = buildCommand(jarPath, simId);
        log.info("Lancement du frontend : {}", String.join(" ", command));

        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            Thread.ofVirtual().name("frontend-stdout").start(() -> pipeOutput(process));

            int exitCode = process.waitFor();
            if (exitCode != 0) log.warn("Frontend terminé avec le code {}", exitCode);
            else               log.info("Frontend fermé proprement");
        } catch (IOException e) {
            log.error("Impossible de lancer le frontend : {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Thread launcher interrompu");
        }
    }

    /**
     * Construit la commande d'exécution.
     *
     * <p><b>Ordre correct :</b> {@code java [JVM_ARGS] -jar frontend.jar [APP_ARGS]}<br>
     * Bugfix : les args JVM étaient placés après {@code -jar}, ce qui les faisait
     * interpréter comme arguments d'application, ignorant {@code --enable-preview}.</p>
     */
    private List<String> buildCommand(Path jarPath, long simId) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ProcessHandle.current().info().command().orElse("java"));

        // JVM args AVANT -jar (ordre obligatoire)
        if (props.jvmArgs() != null && !props.jvmArgs().isBlank()) {
            for (String arg : props.jvmArgs().split("\\s+")) {
                if (!arg.isBlank()) cmd.add(arg);
            }
        }

        cmd.add("-jar");
        cmd.add(jarPath.toString());
        cmd.add(props.backendUrl());
        cmd.add(String.valueOf(simId));
        return cmd;
    }

    private void pipeOutput(Process process) {
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) log.info("[frontend] {}", line);
        } catch (IOException e) {
            log.debug("Pipe frontend fermé : {}", e.getMessage());
        }
    }
}