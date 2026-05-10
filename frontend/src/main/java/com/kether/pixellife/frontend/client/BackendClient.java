package com.kether.pixellife.frontend.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kether.pixellife.common.dto.SimulationDtos.GridSnapshot;
import com.kether.pixellife.common.dto.SimulationDtos.SimulationStartResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Client HTTP léger vers l'API REST du backend.
 * Utilise java.net.http.HttpClient (Java 11+, pas de dépendance externe).
 */
public class BackendClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public BackendClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    // ─── Snapshot ─────────────────────────────────────────────────────────────

    public Optional<GridSnapshot> fetchGridSnapshot(long simulationId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("%s/api/simulations/%d/grid".formatted(baseUrl, simulationId)))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Optional.of(mapper.readValue(response.body(), GridSnapshot.class));
            }
        } catch (Exception ex) {
            System.err.println("Backend indisponible : " + ex.getMessage());
        }
        return Optional.empty();
    }

    // ─── Démarrage avec config complète ───────────────────────────────────────

    /**
     * Démarre une simulation avec la configuration JSON fournie.
     * @param configJson corps JSON de type {@code {"config": {...}}}
     * @return l'id de la simulation créée, ou -1 en cas d'erreur
     */
    public long sendStartWithConfig(String configJson) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("%s/api/simulations".formatted(baseUrl)))
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(configJson))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                SimulationStartResponse resp = mapper.readValue(response.body(), SimulationStartResponse.class);
                return resp.simulationId();
            }
            System.err.println("Erreur démarrage simulation : HTTP " + response.statusCode());
        } catch (Exception ex) {
            System.err.println("Impossible de démarrer la simulation : " + ex.getMessage());
        }
        return -1L;
    }

    /** Démarre avec la config par défaut (50×50). */
    public long sendStart() {
        return sendStartWithConfig("{}");
    }

    // ─── Contrôle ─────────────────────────────────────────────────────────────

    public void sendPause(long simId)  { sendControl(simId, "pause");  }
    public void sendResume(long simId) { sendControl(simId, "resume"); }
    public void sendStop(long simId)   { sendControl(simId, "stop");   }

    /**
     * Modifie le délai entre ticks à chaud.
     * @param simId   id de la simulation
     * @param delayMs nouveau délai en ms (0 = vitesse maximale)
     */
    public void sendSetSpeed(long simId, long delayMs) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("%s/api/simulations/%d/speed?delayMs=%d"
                            .formatted(baseUrl, simId, delayMs)))
                    .method("PATCH", HttpRequest.BodyPublishers.noBody())
                    .build();
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ex) {
            System.err.println("Impossible de modifier la vitesse : " + ex.getMessage());
        }
    }

    // ─── Interne ──────────────────────────────────────────────────────────────

    private void sendControl(long simId, String action) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("%s/api/simulations/%d/%s".formatted(baseUrl, simId, action)))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }
}