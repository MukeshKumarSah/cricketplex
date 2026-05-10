package com.cricketplex.repository;

import com.cricketplex.entity.BattingScorecard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BattingScorecardRepository extends JpaRepository<BattingScorecard, UUID> {
    List<BattingScorecard> findByInningsIdOrderByBattingPositionAsc(UUID inningsId);

    @Modifying
    @Query("DELETE FROM BattingScorecard bs WHERE bs.innings.id IN " +
           "(SELECT i.id FROM Innings i WHERE i.matchResult.id IN " +
           "(SELECT mr.id FROM MatchResult mr WHERE mr.fixture.simSessionId = :simId))")
    void deleteBySimSessionId(@Param("simId") UUID simId);

    @Query("SELECT bs FROM BattingScorecard bs " +
           "JOIN bs.innings i JOIN i.matchResult mr JOIN mr.fixture f " +
           "WHERE bs.player.id = :playerId AND f.status = 'COMPLETED' " +
           "ORDER BY f.matchDate DESC")
    List<BattingScorecard> findByPlayerCompleted(@Param("playerId") UUID playerId);

    @Query("SELECT bs FROM BattingScorecard bs " +
           "JOIN bs.innings i JOIN i.matchResult mr JOIN mr.fixture f " +
           "WHERE bs.fielder.id = :playerId AND f.status = 'COMPLETED' " +
           "ORDER BY f.matchDate DESC")
    List<BattingScorecard> findFieldingByPlayerCompleted(@Param("playerId") UUID playerId);

    @Query("SELECT bs FROM BattingScorecard bs " +
           "JOIN bs.innings i JOIN i.matchResult mr JOIN mr.fixture f " +
           "WHERE bs.player.team.id = :teamId AND f.status = 'COMPLETED' " +
           "ORDER BY f.matchDate DESC")
    List<BattingScorecard> findByTeamCompleted(@Param("teamId") UUID teamId);

    @Query("SELECT bs FROM BattingScorecard bs " +
           "JOIN bs.innings i JOIN i.matchResult mr JOIN mr.fixture f " +
           "WHERE bs.fielder.team.id = :teamId AND f.status = 'COMPLETED' " +
           "ORDER BY f.matchDate DESC")
    List<BattingScorecard> findFieldingByTeamCompleted(@Param("teamId") UUID teamId);

    @Query("SELECT bs FROM BattingScorecard bs " +
           "JOIN FETCH bs.player p JOIN FETCH p.team " +
           "JOIN FETCH bs.innings i JOIN FETCH i.matchResult mr JOIN FETCH mr.fixture f " +
           "WHERE f.matchType = 'CUP' AND f.season = :season AND f.status = 'COMPLETED'")
    List<BattingScorecard> findByCupSeason(@Param("season") int season);

    @Query("SELECT bs FROM BattingScorecard bs " +
           "JOIN FETCH bs.fielder fld JOIN FETCH fld.team " +
           "JOIN FETCH bs.innings i JOIN FETCH i.matchResult mr JOIN FETCH mr.fixture f " +
           "WHERE bs.fielder IS NOT NULL AND f.matchType = 'CUP' AND f.season = :season AND f.status = 'COMPLETED'")
    List<BattingScorecard> findFieldingByCupSeason(@Param("season") int season);
}
