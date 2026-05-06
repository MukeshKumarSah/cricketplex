package com.cricketplex.repository;

import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    Optional<Team> findByOwner(User owner);

    Boolean existsByOwner(User owner);

    Boolean existsByTeamName(String teamName);

    List<Team> findByTeamNameContainingIgnoreCase(String teamName);

    List<Team> findBySimSessionId(UUID simSessionId);

    List<Team> findByCountryIgnoreCaseAndIsBotTrue(String country);

    long countByCountryIgnoreCaseAndIsBot(String country, boolean isBot);

    List<Team> findByIsBotTrue();

    List<Team> findByIsBotFalse();

    long countByIsBot(boolean isBot);
}
