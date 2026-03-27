package com.cricketplex.repository;

import com.cricketplex.entity.League;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LeagueRepository extends JpaRepository<League, UUID> {

    // All leagues for a country (stats overview)
    List<League> findByCountryIgnoreCaseOrderByDivisionAscLeagueNumberAsc(String country);

    // Format-filtered
    List<League> findByCountryIgnoreCaseAndFormatOrderByDivisionAscLeagueNumberAsc(String country, String format);

    // Season-filtered
    List<League> findByCountryIgnoreCaseAndSeasonOrderByDivisionAscLeagueNumberAsc(String country, int season);

    // Format + Season filtered
    List<League> findByCountryIgnoreCaseAndFormatAndSeasonOrderByDivisionAscLeagueNumberAsc(String country, String format, int season);

    @Query("SELECT DISTINCT l.season FROM League l WHERE LOWER(l.country) = LOWER(:country) ORDER BY l.season")
    List<Integer> findDistinctSeasonsByCountry(String country);

    @Query("SELECT DISTINCT l.country FROM League l ORDER BY l.country")
    List<String> findDistinctCountries();

    // Max division across all formats (stats)
    @Query("SELECT MAX(l.division) FROM League l WHERE LOWER(l.country) = LOWER(:country)")
    Integer findMaxDivision(String country);

    // Max division for a specific format (create/detail)
    @Query("SELECT MAX(l.division) FROM League l WHERE LOWER(l.country) = LOWER(:country) AND l.format = :format")
    Integer findMaxDivisionForFormat(String country, String format);

    // Max league number for a specific format+division (create)
    @Query("SELECT MAX(l.leagueNumber) FROM League l WHERE LOWER(l.country) = LOWER(:country) AND l.format = :format AND l.division = :division")
    Integer findMaxLeagueNumberForFormat(String country, String format, int division);

    @Query("SELECT l FROM League l WHERE LOWER(l.country) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY l.country, l.format, l.division, l.leagueNumber")
    List<League> searchByCountry(String q);

    @Query("SELECT COALESCE(MAX(l.season), 1) FROM League l")
    int findMaxSeason();
}
