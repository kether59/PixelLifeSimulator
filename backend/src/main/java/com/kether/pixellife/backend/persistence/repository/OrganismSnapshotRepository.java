package com.kether.pixellife.backend.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.kether.pixellife.backend.persistence.entity.OrganismSnapshot;

import java.util.List;

@Repository
public interface OrganismSnapshotRepository extends JpaRepository<OrganismSnapshot, Long> {

    List<OrganismSnapshot> findBySimulationIdAndStepOrderByEntityId(long simulationId, long step);

    List<OrganismSnapshot> findBySimulationIdAndEntityIdOrderByStep(long simulationId, long entityId);

    void deleteBySimulationId(long simulationId);
}
