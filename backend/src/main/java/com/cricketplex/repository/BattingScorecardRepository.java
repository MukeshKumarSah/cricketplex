package com.cricketplex.repository;

import com.cricketplex.entity.BattingScorecard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BattingScorecardRepository extends JpaRepository<BattingScorecard, UUID> {
    List<BattingScorecard> findByInningsIdOrderByBattingPositionAsc(UUID inningsId);

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
}
