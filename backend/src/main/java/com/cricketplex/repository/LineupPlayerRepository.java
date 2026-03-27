package com.cricketplex.repository;

import com.cricketplex.entity.LineupPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LineupPlayerRepository extends JpaRepository<LineupPlayer, UUID> {
    List<LineupPlayer> findByLineupIdOrderByBattingPositionAsc(UUID lineupId);
    void deleteByLineupId(UUID lineupId);
}
