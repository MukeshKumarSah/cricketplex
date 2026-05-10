package com.cricketplex.repository;

import com.cricketplex.entity.FriendlyChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendlyChallengeRepository extends JpaRepository<FriendlyChallenge, UUID> {

    List<FriendlyChallenge> findByChallengerTeamIdOrderByCreatedAtDesc(UUID teamId);

    List<FriendlyChallenge> findByChallengedTeamIdAndStatusOrderByCreatedAtDesc(UUID teamId, String status);

    @Query("SELECT fc FROM FriendlyChallenge fc WHERE (fc.challengerTeam.id = :teamId OR fc.challengedTeam.id = :teamId) ORDER BY fc.createdAt DESC")
    List<FriendlyChallenge> findAllByTeam(UUID teamId);

    Optional<FriendlyChallenge> findByFixtureId(UUID fixtureId);

    long countByChallengerTeamIdAndStatus(UUID teamId, String status);

    long countByChallengedTeamIdAndStatus(UUID teamId, String status);

    List<FriendlyChallenge> findByStatus(String status);
}
