package com.cricketplex.repository;

import com.cricketplex.entity.BowlingScorecard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BowlingScorecardRepository extends JpaRepository<BowlingScorecard, UUID> {
    List<BowlingScorecard> findByInningsIdOrderByOversDesc(UUID inningsId);

    @Modifying
    @Query("DELETE FROM BowlingScorecard bs WHERE bs.innings.id IN " +
           "(SELECT i.id FROM Innings i WHERE i.matchResult.id IN " +
           "(SELECT mr.id FROM MatchResult mr WHERE mr.fixture.simSessionId = :simId))")
    void deleteBySimSessionId(@Param("simId") UUID simId);

    @Query("SELECT bs FROM BowlingScorecard bs " +
           "JOIN bs.innings i JOIN i.matchResult mr JOIN mr.fixture f " +
           "WHERE bs.player.id = :playerId AND f.status = 'COMPLETED' " +
           "ORDER BY f.matchDate DESC")
    List<BowlingScorecard> findByPlayerCompleted(@Param("playerId") UUID playerId);

    @Query("SELECT bs FROM BowlingScorecard bs " +
           "JOIN bs.innings i JOIN i.matchResult mr JOIN mr.fixture f " +
           "WHERE bs.player.team.id = :teamId AND f.status = 'COMPLETED' " +
           "ORDER BY f.matchDate DESC")
    List<BowlingScorecard> findByTeamCompleted(@Param("teamId") UUID teamId);

    @Query("SELECT bs FROM BowlingScorecard bs " +
           "JOIN FETCH bs.player p JOIN FETCH p.team " +
           "JOIN FETCH bs.innings i JOIN FETCH i.matchResult mr JOIN FETCH mr.fixture f " +
           "WHERE f.matchType = 'CUP' AND f.season = :season AND f.status = 'COMPLETED'")
    List<BowlingScorecard> findByCupSeason(@Param("season") int season);
}
