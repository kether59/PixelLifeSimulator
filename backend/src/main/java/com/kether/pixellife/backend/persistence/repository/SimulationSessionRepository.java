package com.kether.pixellife.backend.persistence.repository;

import com.kether.pixellife.backend.persistence.entity.SimulationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SimulationSessionRepository extends JpaRepository<SimulationSession, Long> {

    /** Retrouve la session liée à un SimulationRecord donné */
    Optional<SimulationSession> findBySimulationRecordId(Long simulationRecordId);

    /** Toutes les sessions triées par date de démarrage décroissante */
    @Query("SELECT s FROM SimulationSession s ORDER BY s.startedAt DESC")
    List<SimulationSession> findAllOrderByStartedAtDesc();

    /** Sessions terminées avec extinction totale */
    List<SimulationSession> findByEndReason(String endReason);
}