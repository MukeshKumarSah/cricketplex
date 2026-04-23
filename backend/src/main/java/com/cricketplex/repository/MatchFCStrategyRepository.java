package com.cricketplex.repository;

import com.cricketplex.entity.MatchFCStrategy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchFCStrategyRepository extends JpaRepository<MatchFCStrategy, UUID> {
    Optional<MatchFCStrategy> findByFixtureIdAndTeamId(UUID fixtureId, UUID teamId);
    List<MatchFCStrategy> findByFixtureId(UUID fixtureId);
}
