package com.cricketplex.repository;

import com.cricketplex.entity.LineupPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LineupPlayerRepository extends JpaRepository<LineupPlayer, UUID> {
    List<LineupPlayer> findByLineupIdOrderByBattingPositionAsc(UUID lineupId);
    void deleteByLineupId(UUID lineupId);

    @Modifying
    @Query("DELETE FROM LineupPlayer lp WHERE lp.lineup.id IN " +
           "(SELECT ml.id FROM MatchLineup ml WHERE ml.fixture.simSessionId = :simId)")
    void deleteBySimSessionId(@Param("simId") UUID simId);

    /** Returns (playerId, fixtureId) pairs for a specific team's players across the given fixtures. */
    @Query("SELECT lp.player.id, lp.lineup.fixture.id FROM LineupPlayer lp " +
           "WHERE lp.lineup.team.id = :teamId AND lp.lineup.fixture.id IN :fixtureIds")
    List<Object[]> findPlayerFixturePairsByTeamAndFixtures(
            @Param("teamId") UUID teamId,
            @Param("fixtureIds") Collection<UUID> fixtureIds);

    /** Returns (playerId, fixtureId) pairs for ALL teams' players across the given fixtures. */
    @Query("SELECT lp.player.id, lp.lineup.fixture.id FROM LineupPlayer lp " +
           "WHERE lp.lineup.fixture.id IN :fixtureIds")
    List<Object[]> findPlayerFixturePairsByFixtures(
            @Param("fixtureIds") Collection<UUID> fixtureIds);
}
