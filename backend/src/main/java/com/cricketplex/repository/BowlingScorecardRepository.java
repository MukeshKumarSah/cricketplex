package com.cricketplex.repository;

import com.cricketplex.entity.BowlingScorecard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BowlingScorecardRepository extends JpaRepository<BowlingScorecard, UUID> {
    List<BowlingScorecard> findByInningsIdOrderByOversDesc(UUID inningsId);
}
