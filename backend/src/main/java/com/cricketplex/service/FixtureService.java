package com.cricketplex.service;

import com.cricketplex.entity.Fixture;
import com.cricketplex.entity.League;
import com.cricketplex.entity.LeagueTeam;
import com.cricketplex.entity.Team;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.LeagueTeamRepository;
import com.cricketplex.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FixtureService {

    private final FixtureRepository fixtureRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final TeamRepository teamRepository;

    /** Season 1 starts on this Sunday. Each season roughly 14 weeks apart. */
    private static final LocalDate SEASON_1_START = LocalDate.of(2026, 4, 5);

    /**
     * Country → UTC match start time (HH:mm).
     * Chosen to reflect evening prime-time in each country's local timezone,
     * which naturally distributes server load across the day.
     */
    private static final Map<String, String> COUNTRY_MATCH_TIMES = Map.ofEntries(
            Map.entry("New Zealand",          "07:00"),
            Map.entry("Australia",            "09:00"),
            Map.entry("Bangladesh",           "13:00"),
            Map.entry("Nepal",                "13:15"),
            Map.entry("Sri Lanka",            "13:30"),
            Map.entry("India",                "14:30"),
            Map.entry("Pakistan",             "15:00"),
            Map.entry("Afghanistan",          "15:30"),
            Map.entry("Oman",                 "16:00"),
            Map.entry("United Arab Emirates", "16:30"),
            Map.entry("South Africa",         "17:00"),
            Map.entry("Zimbabwe",             "17:30"),
            Map.entry("Netherlands",          "18:00"),
            Map.entry("Scotland",             "18:30"),
            Map.entry("Ireland",              "19:00"),
            Map.entry("England",              "19:30"),
            Map.entry("West Indies",          "23:00"),
            Map.entry("United States",        "00:00")
    );

    /** Get the UTC match start time for a country. */
    public static String getMatchStartTime(String country) {
        return COUNTRY_MATCH_TIMES.getOrDefault(country, "14:00");
    }

    /**
     * Balanced round-robin template for 8 teams (0-indexed positions).
     * Verified constraints:
     *  - Each team plays every other exactly once in rounds 1-7
     *  - 4 home + 3 away (positions 0-3) or 3 home + 4 away (positions 4-7) in first 7
     *  - No 3 consecutive home or away for any team (including across the R7/R8 boundary)
     *  - Rounds 8-14 are reciprocal of rounds 7,6,5,4,3,2,1 (reversed order)
     */
    private static final int[][][] FIRST_HALF = {
        {{0,7}, {1,6}, {2,5}, {3,4}},  // Round 1
        {{7,4}, {5,3}, {6,2}, {0,1}},  // Round 2
        {{1,7}, {2,0}, {3,6}, {4,5}},  // Round 3
        {{7,5}, {6,4}, {0,3}, {1,2}},  // Round 4
        {{2,7}, {3,1}, {4,0}, {5,6}},  // Round 5
        {{7,6}, {0,5}, {1,4}, {2,3}},  // Round 6
        {{3,7}, {4,2}, {5,1}, {6,0}},  // Round 7
    };

    /**
     * Generate the full 14-round fixture list for a league.
     * Idempotent — does nothing if fixtures already exist.
     */
    @Transactional
    public void generateFixtures(League league) {
        if (fixtureRepository.countByLeagueId(league.getId()) > 0) return;

        List<LeagueTeam> leagueTeams = leagueTeamRepository.findByLeagueId(league.getId());
        if (leagueTeams.size() != 8) {
            log.warn("Cannot generate fixtures for league {} — has {} teams (need 8)",
                    league.getId(), leagueTeams.size());
            return;
        }

        Team[] teams = new Team[8];
        for (int i = 0; i < 8; i++) {
            teams[i] = leagueTeams.get(i).getTeam();
        }

        List<Fixture> fixtures = new ArrayList<>(56);

        // ── Rounds 1-7 (first half) ──
        for (int r = 0; r < 7; r++) {
            LocalDate matchDate = computeMatchDate(league.getFormat(), r + 1, league.getSeason());
            for (int m = 0; m < 4; m++) {
                fixtures.add(Fixture.builder()
                        .league(league)
                        .round(r + 1)
                        .matchNumber(m + 1)
                        .homeTeam(teams[FIRST_HALF[r][m][0]])
                        .awayTeam(teams[FIRST_HALF[r][m][1]])
                        .matchDate(matchDate)
                        .status("SCHEDULED")
                        .build());
            }
        }

        // ── Rounds 8-14 (reciprocal of rounds 7→1, reversed order) ──
        for (int r = 0; r < 7; r++) {
            int srcRound = 6 - r; // R8→template[6] (R7), R9→template[5] (R6), …
            LocalDate matchDate = computeMatchDate(league.getFormat(), r + 8, league.getSeason());
            for (int m = 0; m < 4; m++) {
                // Swap home/away from source round
                fixtures.add(Fixture.builder()
                        .league(league)
                        .round(r + 8)
                        .matchNumber(m + 1)
                        .homeTeam(teams[FIRST_HALF[srcRound][m][1]])
                        .awayTeam(teams[FIRST_HALF[srcRound][m][0]])
                        .matchDate(matchDate)
                        .status("SCHEDULED")
                        .build());
            }
        }

        fixtureRepository.saveAll(fixtures);
        log.info("Generated 56 fixtures for {} {} {}.{} season {}",
                league.getCountry(), league.getFormat(),
                league.getDivision(), league.getLeagueNumber(), league.getSeason());
    }

    /**
     * Compute the match date for a given format, round, and season.
     *
     * Schedule per week (season start = Sunday):
     *   T20: Sunday (odd rounds) + Thursday (even rounds)  → 7 weeks for 14 rounds
     *   ODI: Monday (odd rounds) + Friday (even rounds)   → 7 weeks for 14 rounds
     *   FC:  Tuesday, 1 round per week                    → 14 weeks for 14 rounds
     */
    private LocalDate computeMatchDate(String format, int round, int season) {
        LocalDate seasonStart = SEASON_1_START.plusWeeks((long)(season - 1) * 14);

        switch (format) {
            case "T20": {
                int week = (round + 1) / 2;         // R1,2→W1  R3,4→W2 …
                boolean first = (round % 2 == 1);   // odd = Sunday, even = Thursday
                return seasonStart.plusWeeks(week - 1).plusDays(first ? 0 : 4);
            }
            case "ODI": {
                int week = (round + 1) / 2;
                boolean first = (round % 2 == 1);   // odd = Monday, even = Friday
                return seasonStart.plusWeeks(week - 1).plusDays(first ? 1 : 5);
            }
            case "FC": {
                // 1 round per week, Tuesday start (2-day match Tue-Wed)
                return seasonStart.plusWeeks(round - 1).plusDays(2);
            }
            default:
                return seasonStart.plusWeeks(round - 1);
        }
    }

    /**
     * Replace all references to oldTeamId with newTeam in a league's fixtures.
     * Called when a bot team is replaced by a human team (or vice versa).
     */
    @Transactional
    public void swapTeamInFixtures(UUID leagueId, UUID oldTeamId, UUID newTeamId) {
        Team newTeam = teamRepository.findById(newTeamId).orElse(null);
        if (newTeam == null) return;

        List<Fixture> fixtures = fixtureRepository.findByLeagueId(leagueId);
        boolean changed = false;
        for (Fixture f : fixtures) {
            if (f.getHomeTeam().getId().equals(oldTeamId)) {
                f.setHomeTeam(newTeam);
                changed = true;
            }
            if (f.getAwayTeam().getId().equals(oldTeamId)) {
                f.setAwayTeam(newTeam);
                changed = true;
            }
        }
        if (changed) fixtureRepository.saveAll(fixtures);
    }

    /**
     * Delete all fixtures for a league (used during league deletion).
     */
    @Transactional
    public void deleteFixturesForLeague(UUID leagueId) {
        fixtureRepository.deleteByLeagueId(leagueId);
    }

    /**
     * Get fixtures for a league, grouped by round.
     * Ensures fixtures exist for a league, generating them if needed (for pre-V13 leagues).
     */
    @Transactional
    public void ensureFixturesExist(League league) {
        if (fixtureRepository.countByLeagueId(league.getId()) == 0) {
            generateFixtures(league);
        }
    }

    /**
     * Lazily generates fixtures if they don't exist yet (for pre-V13 leagues).
     */
    @Transactional
    public List<Map<String, Object>> getFixturesGroupedByRound(League league) {
        ensureFixturesExist(league);

        List<Fixture> all = fixtureRepository.findByLeagueIdOrderByRoundAscMatchNumberAsc(league.getId());
        if (all.isEmpty()) return List.of();

        Map<Integer, List<Map<String, Object>>> byRound = new LinkedHashMap<>();
        Map<Integer, LocalDate> roundDates = new LinkedHashMap<>();

        for (Fixture f : all) {
            roundDates.putIfAbsent(f.getRound(), f.getMatchDate());

            Map<String, Object> match = new LinkedHashMap<>();
            match.put("id", f.getId());
            match.put("matchNumber", f.getMatchNumber());
            match.put("homeTeam", teamInfo(f.getHomeTeam()));
            match.put("awayTeam", teamInfo(f.getAwayTeam()));
            match.put("status", f.getStatus());

            byRound.computeIfAbsent(f.getRound(), k -> new ArrayList<>()).add(match);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Integer, List<Map<String, Object>>> entry : byRound.entrySet()) {
            Map<String, Object> roundData = new LinkedHashMap<>();
            roundData.put("round", entry.getKey());
            roundData.put("matchDate", roundDates.get(entry.getKey()));
            roundData.put("matchStartTimeUtc", league.getMatchStartTime());
            roundData.put("matches", entry.getValue());
            result.add(roundData);
        }
        return result;
    }

    private Map<String, Object> teamInfo(Team team) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", team.getId());
        info.put("teamName", team.getTeamName());
        info.put("teamProfilePicUrl", team.getTeamProfilePicUrl());
        info.put("isBot", team.getIsBot());
        return info;
    }
}
