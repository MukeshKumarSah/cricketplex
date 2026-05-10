package com.cricketplex.repository;

import com.cricketplex.entity.BallEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BallEventRepository extends JpaRepository<BallEvent, UUID> {
    List<BallEvent> findByInningsIdOrderByOverNumberAscBallNumberAsc(UUID inningsId);
    long countByInningsId(UUID inningsId);

    @Modifying
    @Query("DELETE FROM BallEvent be WHERE be.innings.id IN " +
           "(SELECT i.id FROM Innings i WHERE i.matchResult.id IN " +
           "(SELECT mr.id FROM MatchResult mr WHERE mr.fixture.simSessionId = :simId))")
    void deleteBySimSessionId(@Param("simId") UUID simId);
}
