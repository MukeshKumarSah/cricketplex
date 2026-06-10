package com.cricketplex.repository;

import com.cricketplex.entity.TrainingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrainingLogRepository extends JpaRepository<TrainingLog, UUID> {
    List<TrainingLog> findByTeamIdOrderByTrainedAtDesc(UUID teamId);
    List<TrainingLog> findTop50ByPlayerIdOrderByTrainedAtDesc(UUID playerId);
}
