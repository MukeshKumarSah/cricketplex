package com.cricketplex.repository;

import com.cricketplex.entity.BallEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BallEventRepository extends JpaRepository<BallEvent, UUID> {
    List<BallEvent> findByInningsIdOrderByOverNumberAscBallNumberAsc(UUID inningsId);
    long countByInningsId(UUID inningsId);
}
