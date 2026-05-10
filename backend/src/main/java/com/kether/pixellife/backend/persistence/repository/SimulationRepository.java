package com.kether.pixellife.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.kether.pixellife.backend.persistence.entity.SimulationRecord;

import java.util.List;

@Repository
public interface SimulationRepository extends JpaRepository<SimulationRecord, Long> {

    List<SimulationRecord> findByStatusOrderByStartedAtDesc(SimulationRecord.SimulationStatus status);

    @Query("SELECT s FROM SimulationRecord s ORDER BY s.startedAt DESC")
    List<SimulationRecord> findAllOrderByStartedAtDesc();

    @Query("SELECT COUNT(s) FROM SimulationRecord s WHERE s.status = :status")
    long countByStatus(SimulationRecord.SimulationStatus status);
}
