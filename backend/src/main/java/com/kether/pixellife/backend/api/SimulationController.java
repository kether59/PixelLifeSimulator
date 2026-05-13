package com.kether.pixellife.backend.api;

import com.kether.pixellife.backend.constant.GameConstants;
import com.kether.pixellife.backend.service.SimulationService;
import com.kether.pixellife.common.dto.SimulationDtos;
import com.kether.pixellife.common.model.SimulationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API REST de gestion des simulations.
 *
 * Endpoints :
 *   POST   /api/simulations                     → démarrer une simulation
 *   GET    /api/simulations                     → lister toutes les simulations
 *   GET    /api/simulations/{id}                → détail d'une simulation
 *   GET    /api/simulations/{id}/grid           → snapshot courant de la grille
 *   POST   /api/simulations/{id}/pause          → pause
 *   POST   /api/simulations/{id}/resume         → reprise
 *   POST   /api/simulations/{id}/stop           → arrêt
 *   PATCH  /api/simulations/{id}/speed          → modifier le délai entre ticks (?delayMs=X)
 */
@RestController
@RequestMapping("/api/simulations")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SimulationController {

    private final SimulationService simulationService;

    // ─── Démarrer ─────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<SimulationDtos.SimulationStartResponse> start(
            @RequestBody(required = false) SimulationDtos.SimulationStartRequest request) {

        SimulationConfig config = (request != null && request.config() != null)
                ? request.config()
                : SimulationConfig.defaults();

        SimulationDtos.SimulationStartResponse response = simulationService.startSimulation(config);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─── Lister ───────────────────────────────────────────────────────────────

    @GetMapping
    public List<SimulationDtos.SimulationSummary> list() {
        return simulationService.listSimulations();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimulationDtos.SimulationSummary> getOne(@PathVariable long id) {
        return simulationService.getSimulation(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Snapshot grille ──────────────────────────────────────────────────────

    @GetMapping("/{id}/grid")
    public ResponseEntity<SimulationDtos.GridSnapshot> getGrid(@PathVariable long id) {
        return simulationService.getCurrentSnapshot(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Contrôle ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/pause")
    public ResponseEntity<Map<String, String>> pause(@PathVariable long id) {
        boolean ok = simulationService.pauseSimulation(id);
        return ok
                ? ResponseEntity.ok(Map.of(GameConstants.KEY_STATUS, "paused"))
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Map<String, String>> resume(@PathVariable long id) {
        boolean ok = simulationService.resumeSimulation(id);
        return ok
                ? ResponseEntity.ok(Map.of(GameConstants.KEY_STATUS, "running"))
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Map<String, String>> stop(@PathVariable long id) {
        boolean ok = simulationService.stopSimulation(id);
        return ok
                ? ResponseEntity.ok(Map.of(GameConstants.KEY_STATUS, "stopped"))
                : ResponseEntity.notFound().build();
    }

    /**
     * Modifie le délai entre ticks à chaud.
     * PATCH /api/simulations/{id}/speed?delayMs=50
     *
     * @param delayMs délai en millisecondes entre chaque tick (0 = vitesse max)
     */
    @PatchMapping("/{id}/speed")
    public ResponseEntity<Map<String, Object>> setSpeed(
            @PathVariable long id,
            @RequestParam long delayMs) {
        if (delayMs < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of(GameConstants.KEY_ERROR, "delayMs doit être >= 0"));
        }
        boolean ok = simulationService.setTickDelay(id, delayMs);
        return ok
                ? ResponseEntity.ok(Map.of(GameConstants.KEY_SIMULATION_ID, id, GameConstants.KEY_TICK_DELAY_MS, delayMs))
                : ResponseEntity.notFound().build();
    }
}