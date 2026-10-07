package com.cricketplex.repository;

import com.cricketplex.entity.MatchLineup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchLineupRepository extends JpaRepository<MatchLineup, UUID> {
    Optional<MatchLineup> findByFixtureIdAndTeamId(UUID fixtureId, UUID teamId);

    List<MatchLineup> findByFixtureId(UUID fixtureId);

    @Query("SELECT ml.fixture.id FROM MatchLineup ml WHERE ml.team.id = :teamId")
    List<UUID> findFixtureIdsByTeamId(@Param("teamId") UUID teamId);

    @Modifying
    @Query("DELETE FROM MatchLineup ml WHERE ml.fixture.simSessionId = :simId")
    void deleteBySimSessionId(@Param("simId") UUID simId);
}
