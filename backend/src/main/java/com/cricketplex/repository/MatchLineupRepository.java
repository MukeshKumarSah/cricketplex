package com.cricketplex.repository;

import com.cricketplex.entity.MatchLineup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchLineupRepository extends JpaRepository<MatchLineup, UUID> {
    Optional<MatchLineup> findByFixtureIdAndTeamId(UUID fixtureId, UUID teamId);

    @Query("SELECT ml.fixture.id FROM MatchLineup ml WHERE ml.team.id = :teamId")
    List<UUID> findFixtureIdsByTeamId(@Param("teamId") UUID teamId);
}
