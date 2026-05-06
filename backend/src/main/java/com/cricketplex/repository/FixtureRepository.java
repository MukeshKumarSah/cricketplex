package com.cricketplex.repository;

import com.cricketplex.entity.Fixture;
import com.cricketplex.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FixtureRepository extends JpaRepository<Fixture, UUID> {

    List<Fixture> findByLeagueIdOrderByRoundAscMatchNumberAsc(UUID leagueId);

    List<Fixture> findByLeagueIdAndSeasonOrderByRoundAscMatchNumberAsc(UUID leagueId, Integer season);

    List<Fixture> findByLeagueId(UUID leagueId);

    List<Fixture> findByLeagueIdAndSeason(UUID leagueId, Integer season);

    long countByLeagueId(UUID leagueId);

    long countByLeagueIdAndSeason(UUID leagueId, Integer season);

    void deleteByLeagueId(UUID leagueId);

    List<Fixture> findBySimSessionId(UUID simSessionId);

    List<Fixture> findByHomeTeamAndMatchDateGreaterThanEqualOrderByMatchDateAsc(Team homeTeam, LocalDate date);
    List<Fixture> findByHomeTeamAndStatusAndMatchDateGreaterThanEqual(Team homeTeam, String status, LocalDate date);

    @Query("SELECT f FROM Fixture f WHERE (f.homeTeam = :team OR f.awayTeam = :team) ORDER BY f.matchDate ASC, f.round ASC")
    List<Fixture> findAllByTeamOrderByMatchDate(@Param("team") Team team);

    @Query("SELECT f FROM Fixture f WHERE (f.homeTeam = :team OR f.awayTeam = :team) AND f.season = :season ORDER BY f.matchDate ASC, f.round ASC")
    List<Fixture> findAllByTeamAndSeasonOrderByMatchDate(@Param("team") Team team, @Param("season") Integer season);

    List<Fixture> findByStatusAndMatchDateLessThanEqual(String status, LocalDate date);

    @Query("SELECT f FROM Fixture f LEFT JOIN FETCH f.league WHERE f.status = :status AND f.matchDate <= :date")
    List<Fixture> findScheduledWithLeague(@Param("status") String status, @Param("date") LocalDate date);

    @Query("SELECT f FROM Fixture f LEFT JOIN FETCH f.league WHERE f.status = :status")
    List<Fixture> findByStatus(@Param("status") String status);

    long countByLeagueIdAndStatusNot(UUID leagueId, String status);

    long countByLeagueIdAndSeasonAndStatusNot(UUID leagueId, Integer season, String status);

    /** All COMPLETED league fixtures for a season — homeTeam/awayTeam eagerly loaded. */
    @Query("SELECT f FROM Fixture f JOIN FETCH f.homeTeam JOIN FETCH f.awayTeam " +
           "WHERE f.league IS NOT NULL AND f.season = :season AND f.status = 'COMPLETED'")
    List<Fixture> findAllCompletedLeagueFixturesBySeason(@Param("season") Integer season);

    /** League IDs that still have non-COMPLETED fixtures for a season. */
    @Query("SELECT DISTINCT f.league.id FROM Fixture f " +
           "WHERE f.league IS NOT NULL AND f.season = :season AND f.status <> 'COMPLETED'")
    List<UUID> findLeagueIdsWithIncompleteFixtures(@Param("season") Integer season);

    boolean existsByLeagueIdAndSeason(UUID leagueId, Integer season);

    @Query("SELECT DISTINCT f.season FROM Fixture f WHERE LOWER(f.league.country) = LOWER(:country) ORDER BY f.season")
    List<Integer> findDistinctSeasonsByCountry(@Param("country") String country);

    @Query("SELECT DISTINCT f.season FROM Fixture f WHERE f.league.id = :leagueId ORDER BY f.season")
    List<Integer> findDistinctSeasonsByLeagueId(@Param("leagueId") UUID leagueId);

    @Modifying
    @Query("DELETE FROM Fixture f WHERE f.simSessionId = :simId")
    void deleteBySimSessionId(@Param("simId") UUID simId);

    // ── Cup fixture queries ──────────────────────────────────────────────────

    List<Fixture> findByCupIdOrderByRoundAscMatchNumberAsc(UUID cupId);

    List<Fixture> findByCupIdAndRoundOrderByMatchNumberAsc(UUID cupId, Integer round);

    boolean existsByCupId(UUID cupId);

    long countByCupIdAndRoundAndStatus(UUID cupId, Integer round, String status);

    long countByCupIdAndRound(UUID cupId, Integer round);

    @Query("SELECT f FROM Fixture f WHERE f.matchType = :matchType AND f.status = :status AND f.matchDate <= :date")
    List<Fixture> findByMatchTypeAndStatusAndMatchDateLessThanEqual(
            @Param("matchType") String matchType,
            @Param("status") String status,
            @Param("date") java.time.LocalDate date);
}
