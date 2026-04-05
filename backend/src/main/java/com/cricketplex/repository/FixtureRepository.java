package com.cricketplex.repository;

import com.cricketplex.entity.Fixture;
import com.cricketplex.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FixtureRepository extends JpaRepository<Fixture, UUID> {

    List<Fixture> findByLeagueIdOrderByRoundAscMatchNumberAsc(UUID leagueId);

    List<Fixture> findByLeagueId(UUID leagueId);

    long countByLeagueId(UUID leagueId);

    void deleteByLeagueId(UUID leagueId);

    List<Fixture> findByHomeTeamAndMatchDateGreaterThanEqualOrderByMatchDateAsc(Team homeTeam, LocalDate date);

    @Query("SELECT f FROM Fixture f WHERE (f.homeTeam = :team OR f.awayTeam = :team) ORDER BY f.matchDate ASC, f.round ASC")
    List<Fixture> findAllByTeamOrderByMatchDate(Team team);

    List<Fixture> findByStatusAndMatchDateLessThanEqual(String status, LocalDate date);

    @Query("SELECT f FROM Fixture f LEFT JOIN FETCH f.league WHERE f.status = :status AND f.matchDate <= :date")
    List<Fixture> findScheduledWithLeague(String status, LocalDate date);
}
