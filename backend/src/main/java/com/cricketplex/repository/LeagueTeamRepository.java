package com.cricketplex.repository;

import com.cricketplex.entity.LeagueTeam;
import com.cricketplex.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LeagueTeamRepository extends JpaRepository<LeagueTeam, UUID> {

    List<LeagueTeam> findByLeagueId(UUID leagueId);

    List<LeagueTeam> findByLeagueIdAndSeason(UUID leagueId, Integer season);

    List<LeagueTeam> findByTeamId(UUID teamId);

    List<LeagueTeam> findByTeamIdAndSeason(UUID teamId, Integer season);

    long countByLeagueId(UUID leagueId);

    long countByLeagueIdAndSeason(UUID leagueId, Integer season);

    boolean existsByLeagueIdAndTeamId(UUID leagueId, UUID teamId);

    boolean existsByLeagueIdAndTeamIdAndSeason(UUID leagueId, UUID teamId, Integer season);

    @Query("SELECT t FROM Team t WHERE t.isBot = true AND LOWER(t.country) = LOWER(:country) " +
           "AND t.id NOT IN (SELECT lt.team.id FROM LeagueTeam lt WHERE lt.league.format = :format)")
    List<Team> findUnassignedBotsByCountryAndFormat(String country, String format);

    @Query("SELECT lt FROM LeagueTeam lt WHERE lt.league.country = :country AND lt.league.format = :format AND lt.team.id = :teamId")
    List<LeagueTeam> findByCountryFormatAndTeam(String country, String format, UUID teamId);

    void deleteByLeagueId(UUID leagueId);

    void deleteByLeagueIdAndSeason(UUID leagueId, Integer season);
}
