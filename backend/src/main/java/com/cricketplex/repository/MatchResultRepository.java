package com.cricketplex.repository;

import com.cricketplex.entity.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchResultRepository extends JpaRepository<MatchResult, UUID> {
    Optional<MatchResult> findByFixtureId(UUID fixtureId);
    boolean existsByFixtureId(UUID fixtureId);

    @Query("SELECT mr FROM MatchResult mr WHERE " +
           "mr.fixture.status = 'COMPLETED' AND (" +
           "(mr.fixture.homeTeam.id = :t1 AND mr.fixture.awayTeam.id = :t2) OR " +
           "(mr.fixture.homeTeam.id = :t2 AND mr.fixture.awayTeam.id = :t1)) " +
           "ORDER BY mr.fixture.matchDate DESC")
    List<MatchResult> findBetweenTeams(@Param("t1") UUID team1Id, @Param("t2") UUID team2Id);
}
