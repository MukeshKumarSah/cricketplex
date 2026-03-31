package com.cricketplex.repository;

import com.cricketplex.entity.BattingScorecard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BattingScorecardRepository extends JpaRepository<BattingScorecard, UUID> {
    List<BattingScorecard> findByInningsIdOrderByBattingPositionAsc(UUID inningsId);
}
