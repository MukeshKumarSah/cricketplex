package com.cricketplex.repository;

import com.cricketplex.entity.LineupPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LineupPlayerRepository extends JpaRepository<LineupPlayer, UUID> {
    List<LineupPlayer> findByLineupIdOrderByBattingPositionAsc(UUID lineupId);
    void deleteByLineupId(UUID lineupId);

    @Modifying
    @Query("DELETE FROM LineupPlayer lp WHERE lp.lineup.id IN " +
           "(SELECT ml.id FROM MatchLineup ml WHERE ml.fixture.simSessionId = :simId)")
    void deleteBySimSessionId(@Param("simId") UUID simId);
}
