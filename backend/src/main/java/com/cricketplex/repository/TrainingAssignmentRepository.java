package com.cricketplex.repository;

import com.cricketplex.entity.TrainingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingAssignmentRepository extends JpaRepository<TrainingAssignment, UUID> {
    List<TrainingAssignment> findByTeamId(UUID teamId);
    Optional<TrainingAssignment> findByTeamIdAndPlayerId(UUID teamId, UUID playerId);
    void deleteByTeamIdAndPlayerId(UUID teamId, UUID playerId);
    long countByTeamId(UUID teamId);
}
