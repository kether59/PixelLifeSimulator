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
 * Déclenché par {@link ApplicationReadyEvent} (Tomcat prêt, routes enregistrées).
 * Le process frontend est lancé dans un virtual thread — non bloquant pour Spring.
 *
 * Activation dans application.properties :
 * <pre>
 *   pixellife.frontend.auto-launch=true
 * </pre>
 *
 * Ou via profil Maven/Spring :
 * <pre>
 *   mvn spring-boot:run -Dspring-boot.run.profiles=with-frontend
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
            log.debug("Lancement automatique du frontend désactivé (pixellife.frontend.auto-launch=false)");
            return;
        }

        Thread.ofVirtual()
                .name("frontend-launcher")
                .start(this::launchFrontend);
    }

    /**
     * Résout le chemin du jar frontend.
     * Priorité : propriété explicite > recherche automatique dans ../frontend/target/.
     */
    private Path resolveFrontendJar() {
        if (props.jarPath() != null && !props.jarPath().isBlank()) {
            Path explicit = Path.of(props.jarPath());
            if (Files.exists(explicit)) return explicit;
            log.warn("Jar frontend introuvable au chemin configuré : {}", explicit);
        }

        // Recherche automatique relative au répertoire courant
        Path[] candidates = {
                Path.of("../frontend/target/frontend-1.0-SNAPSHOT.jar"),
                Path.of("frontend/target/frontend-1.0-SNAPSHOT.jar"),
                Path.of("./frontend-1.0-SNAPSHOT.jar")
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                log.info("Jar frontend trouvé automatiquement : {}", candidate.toAbsolutePath());
                return candidate.toAbsolutePath();
            }
        }

        return null;
    }

    /**
     * Retourne l'id de la simulation à passer au frontend.
     * Si autoStartSimulation=true, démarre une nouvelle simulation et retourne son id.
     */
    private long resolveSimulationId() {
        if (props.autoStartSimulation()) {
            log.info("Démarrage automatique d'une simulation pour le frontend...");
            var response = simulationService.startSimulation(SimulationConfig.defaults());
            log.info("Simulation #{} démarrée pour le frontend", response.simulationId());
            return response.simulationId();
        }

        if (props.simulationId() > 0) {
            return props.simulationId();
        }

              var simulations = simulationService.listSimulations();
        if (!simulations.isEmpty()) {
            long lastId = simulations.getFirst().id();
            log.info("Utilisation de la dernière simulation en base : #{}", lastId);
            return lastId;
        }

        log.info("Aucune simulation existante, création automatique...");
        var response = simulationService.startSimulation(SimulationConfig.defaults());
        return response.simulationId();
    }

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
            ProcessBuilder pb = new ProcessBuilder(command);

            // Redirige stderr vers stdout pour capturer tous les logs du frontend
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // logs from frontend to SLF4J
            Thread.ofVirtual()
                    .name("frontend-stdout")
                    .start(() -> pipeOutput(process));

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("Frontend terminé avec le code de sortie {}", exitCode);
            } else {
                log.info("Frontend fermé proprement");
            }

        } catch (IOException e) {
            log.error("Impossible de lancer le frontend : {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Thread launcher interrompu");
        }
    }

    private List<String> buildCommand(Path jarPath, long simId) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ProcessHandle.current().info().command().orElse("java"));
        cmd.add("-jar");

        if (props.jvmArgs() != null && !props.jvmArgs().isBlank()) {
            for (String arg : props.jvmArgs().split("\\s+")) {
                if (!arg.isBlank()) cmd.add(arg);
            }
        }

        cmd.add(jarPath.toString());
        cmd.add(props.backendUrl());
        cmd.add(String.valueOf(simId));
        return cmd;
    }

    private void pipeOutput(Process process) {
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[frontend] {}", line);
            }
        } catch (IOException e) {
            log.debug("Pipe frontend fermé : {}", e.getMessage());
        }
    }
}