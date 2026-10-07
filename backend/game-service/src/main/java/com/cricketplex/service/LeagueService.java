package com.cricketplex.service;

import com.cricketplex.entity.Fixture;
import com.cricketplex.entity.League;
import com.cricketplex.entity.LeagueTeam;
import com.cricketplex.entity.Team;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.LeagueRepository;
import com.cricketplex.repository.LeagueTeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeagueService {

    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final FixtureRepository fixtureRepository;
    @Lazy
    private final BotTeamService botTeamService;
    private final FixtureService fixtureService;
    @Lazy
    private final MatchEngine matchEngine;

    /**
     * Get all leagues grouped by country, then by division.
     */
    public Map<String, Object> getLeagueStats() {
        List<String> countries = leagueRepository.findDistinctCountries();
        List<Map<String, Object>> countryData = new ArrayList<>();

        long totalLeagues = 0;
        for (String country : countries) {
            List<League> leagues = leagueRepository.findByCountryIgnoreCaseOrderByDivisionAscLeagueNumberAsc(country);
            totalLeagues += leagues.size();

            Map<Integer, List<String>> divMap = new LinkedHashMap<>();
            for (League l : leagues) {
                divMap.computeIfAbsent(l.getDivision(), k -> new ArrayList<>())
                      .add(l.getDivision() + "." + l.getLeagueNumber());
            }

            Integer maxDiv = leagueRepository.findMaxDivision(country);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("country", country);
            entry.put("totalLeagues", leagues.size());
            entry.put("maxDivision", maxDiv != null ? maxDiv : 0);
            entry.put("divisions", divMap);
            countryData.add(entry);
        }

        return Map.of(
                "totalCountries", countries.size(),
                "totalLeagues", totalLeagues,
                "countries", countryData
        );
    }

    /**
     * Get leagues for a specific country, optionally filtered by format and/or season.
     */
    public Map<String, Object> getLeaguesByCountry(String country, String format, Integer season) {
        List<League> leagues;
        if (format != null) {
            leagues = leagueRepository.findByCountryIgnoreCaseAndFormatOrderByDivisionAscLeagueNumberAsc(country, format);
        } else {
            leagues = leagueRepository.findByCountryIgnoreCaseOrderByDivisionAscLeagueNumberAsc(country);
        }

        if (season != null) {
            leagues = leagues.stream()
                    .filter(l -> fixtureRepository.existsByLeagueIdAndSeason(l.getId(), season)
                            || leagueTeamRepository.countByLeagueIdAndSeason(l.getId(), season) > 0)
                    .toList();
        }

        Map<Integer, List<Map<String, Object>>> divMap = new LinkedHashMap<>();
        for (League l : leagues) {
            divMap.computeIfAbsent(l.getDivision(), k -> new ArrayList<>())
                  .add(Map.of(
                          "id", l.getId(),
                          "leagueId", l.getDivision() + "." + l.getLeagueNumber(),
                          "division", l.getDivision(),
                          "leagueNumber", l.getLeagueNumber(),
                          "season", season != null ? season : l.getSeason(),
                          "format", l.getFormat(),
                          "matchStartTime", l.getMatchStartTime()
                  ));
        }

        Integer maxDiv = format != null
                ? leagueRepository.findMaxDivisionForFormat(country, format)
                : leagueRepository.findMaxDivision(country);

        return Map.of(
                "country", country,
                "maxDivision", maxDiv != null ? maxDiv : 0,
                "totalLeagues", leagues.size(),
                "divisions", divMap,
                "seasons", fixtureRepository.findDistinctSeasonsByCountry(country)
        );
    }

    /**
     * Admin: Create the next league in a division for a country.
     * Creates the league for ALL 3 formats (T20, ODI, FC) at the same division/leagueNumber,
     * then auto-fills each with 8 bot teams.
     */
    @Transactional
    public Map<String, Object> createLeague(String country, String format, int division) {
        if (country == null || country.isBlank()) {
            throw new IllegalArgumentException("Country is required");
        }
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Format is required");
        }
        if (division < 3) {
            throw new IllegalArgumentException("Divisions 1 and 2 are auto-created. Only division 3+ can be added.");
        }

        // Ensure previous division exists and is complete before creating leagues in a new division
        Integer maxDiv = leagueRepository.findMaxDivisionForFormat(country, format);
        if (maxDiv == null) maxDiv = 0;

        if (division > maxDiv + 1) {
            throw new IllegalArgumentException("Cannot skip divisions. Complete division " + (maxDiv + 1) + " first for " + format + ".");
        }

        // Calculate next league number in this division
        Integer maxNum = leagueRepository.findMaxLeagueNumberForFormat(country, format, division);
        int nextNum = (maxNum == null) ? 1 : maxNum + 1;

        // Max leagues per division: div N = 2^(N-1)  →  div3=4, div4=8, div5=16...
        int maxAllowed = (int) Math.pow(2, division - 1);
        if (nextNum > maxAllowed) {
            throw new IllegalArgumentException(
                    "Division " + division + " already has maximum " + maxAllowed + " " + format + " leagues for " + country + ".");
        }

        // Create the league for all 3 formats at the same div/leagueNumber
        String[] allFormats = {"T20", "ODI", "FC"};
        List<League> createdLeagues = new ArrayList<>();

        for (String fmt : allFormats) {
            // Check if this format already has this league
            Integer existingNum = leagueRepository.findMaxLeagueNumberForFormat(country, fmt, division);
            if (existingNum != null && existingNum >= nextNum) continue;

            League league = League.builder()
                    .country(country.trim())
                    .format(fmt)
                    .division(division)
                    .leagueNumber(nextNum)
                    .matchStartTime(FixtureService.getMatchStartTime(country.trim()))
                    .build();
            leagueRepository.save(league);
            createdLeagues.add(league);
        }

        // Fill each new league with bot teams
        for (League league : createdLeagues) {
            botTeamService.fillLeagueWithBots(league);
        }

        // Generate round-robin fixtures for each new league
        for (League league : createdLeagues) {
            fixtureService.generateFixtures(league);
        }

        // Auto-simulate rounds that have already been played in Div 1 of the same country
        int matchesSimulated = 0;
        for (League league : createdLeagues) {
            matchesSimulated += catchUpRounds(league);
        }

        return Map.of(
                "created", "All formats " + division + "." + nextNum,
                "country", country,
                "format", "T20, ODI, FC",
                "division", division,
                "leagueNumber", nextNum,
                "maxForDivision", maxAllowed,
                "leaguesCreated", createdLeagues.size(),
                "teamsFilled", createdLeagues.size() * 8,
                "matchesSimulated", matchesSimulated
        );
    }

    /**
     * Auto-simulate rounds in a newly created league to match Div 1 progress.
     * Finds the Div 1 league for the same country/format, counts how many rounds
     * are completed there, then simulates those same rounds in the new league.
     *
     * @return number of matches simulated
     */
    private int catchUpRounds(League newLeague) {
        // Find Div 1 league for this country/format
        List<League> sameCountryFormat = leagueRepository
                .findByCountryIgnoreCaseAndFormatOrderByDivisionAscLeagueNumberAsc(
                        newLeague.getCountry(), newLeague.getFormat());
        League div1 = sameCountryFormat.stream()
                .filter(l -> l.getDivision() == 1)
                .findFirst().orElse(null);
        if (div1 == null) return 0;

        // Count completed rounds in Div 1
        List<Fixture> div1Fixtures = fixtureRepository
                .findByLeagueIdOrderByRoundAscMatchNumberAsc(div1.getId());
        int completedRounds = 0;
        for (int r = 1; r <= 14; r++) {
            final int round = r;
            boolean allDone = div1Fixtures.stream()
                    .filter(f -> f.getRound() == round)
                    .allMatch(f -> "COMPLETED".equals(f.getStatus()));
            if (allDone && div1Fixtures.stream().anyMatch(f -> f.getRound() == round)) {
                completedRounds = round;
            } else {
                break;
            }
        }

        if (completedRounds == 0) return 0;

        // Simulate those rounds in the new league
        List<Fixture> newFixtures = fixtureRepository
                .findByLeagueIdOrderByRoundAscMatchNumberAsc(newLeague.getId());
        int simulated = 0;
        for (Fixture f : newFixtures) {
            if (f.getRound() > completedRounds) break;
            if (!"SCHEDULED".equals(f.getStatus())) continue;
            try {
                matchEngine.simulateMatch(f.getId());
                simulated++;
            } catch (Exception e) {
                log.warn("Failed to auto-simulate fixture {} in league {}.{}: {}",
                        f.getId(), newLeague.getDivision(), newLeague.getLeagueNumber(), e.getMessage());
            }
        }

        log.info("Auto-simulated {} matches ({} rounds) for new {} {} {}.{} season {}",
                simulated, completedRounds, newLeague.getCountry(), newLeague.getFormat(),
                newLeague.getDivision(), newLeague.getLeagueNumber(), newLeague.getSeason());
        return simulated;
    }

    /**
     * Delete a league (and its counterparts in all 3 formats at same div/leagueNumber).
     * Human teams are relocated to another league in the same country/format (replacing a bot).
     * If any human team cannot be relocated, the deletion is rejected.
     * Bot teams from the deleted league become unassigned (free).
     */
    @Transactional
    public Map<String, Object> deleteLeague(UUID leagueId) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found"));

        String country = league.getCountry();
        int division = league.getDivision();
        int leagueNumber = league.getLeagueNumber();

        // Only allow deleting the highest-numbered league in the division
        List<League> sameDivLeagues = leagueRepository
                .findByCountryIgnoreCaseAndFormatOrderByDivisionAscLeagueNumberAsc(country, league.getFormat());
        int maxLeagueNum = 0;
        for (League l : sameDivLeagues) {
            if (l.getDivision() == division && l.getLeagueNumber() > maxLeagueNum) {
                maxLeagueNum = l.getLeagueNumber();
            }
        }
        if (leagueNumber < maxLeagueNum) {
            throw new IllegalArgumentException(
                    "Cannot delete league " + division + "." + leagueNumber +
                    " — delete " + division + "." + maxLeagueNum + " first.");
        }

        // Find all 3 format counterparts at the same div/leagueNumber
        String[] allFormats = {"T20", "ODI", "FC"};
        List<League> toDelete = new ArrayList<>();
        for (String fmt : allFormats) {
            List<League> leagues = leagueRepository
                    .findByCountryIgnoreCaseAndFormatOrderByDivisionAscLeagueNumberAsc(country, fmt);
            for (League l : leagues) {
                if (l.getDivision() == division && l.getLeagueNumber() == leagueNumber) {
                    toDelete.add(l);
                    break;
                }
            }
        }

        // First pass: check all human teams can be relocated
        // Collect relocation plan: humanTeam -> (format -> targetLeagueTeam entry to replace)
        Map<UUID, Map<String, LeagueTeam>> relocationPlan = new LinkedHashMap<>();
        for (League lg : toDelete) {
            List<LeagueTeam> entries = leagueTeamRepository.findByLeagueId(lg.getId());
            for (LeagueTeam entry : entries) {
                Team team = entry.getTeam();
                if (Boolean.TRUE.equals(team.getIsBot())) continue;

                // Find another league in the same country/format (excluding leagues being deleted)
                // that has a bot team we can replace
                LeagueTeam botSlot = findBotSlotForHuman(lg.getFormat(), country, toDelete);
                if (botSlot == null) {
                    throw new IllegalArgumentException(
                            "Cannot delete league: human team '" + team.getTeamName() +
                            "' cannot be relocated in " + lg.getFormat() + " — no league with a bot slot available.");
                }

                relocationPlan
                    .computeIfAbsent(team.getId(), k -> new LinkedHashMap<>())
                    .put(lg.getFormat(), botSlot);
            }
        }

        // Second pass: execute relocations
        for (League lg : toDelete) {
            List<LeagueTeam> entries = leagueTeamRepository.findByLeagueId(lg.getId());
            for (LeagueTeam entry : entries) {
                Team team = entry.getTeam();
                if (Boolean.TRUE.equals(team.getIsBot())) continue;

                Map<String, LeagueTeam> formatPlan = relocationPlan.get(team.getId());
                if (formatPlan != null && formatPlan.containsKey(lg.getFormat())) {
                    LeagueTeam botSlot = formatPlan.get(lg.getFormat());
                    UUID oldBotId = botSlot.getTeam().getId();
                    // Replace the bot entry with the human team
                    botSlot.setTeam(team);
                    leagueTeamRepository.save(botSlot);
                    // Update fixtures in the target league
                    fixtureService.swapTeamInFixtures(botSlot.getLeague().getId(), oldBotId, team.getId());
                }
            }
        }

        // Third pass: delete fixtures, league_teams entries, and the leagues themselves
        int deletedLeagues = 0;
        for (League lg : toDelete) {
            fixtureService.deleteFixturesForLeague(lg.getId());
            leagueTeamRepository.deleteByLeagueId(lg.getId());
            leagueRepository.delete(lg);
            deletedLeagues++;
        }

        return Map.of(
                "deleted", division + "." + leagueNumber,
                "country", country,
                "formatsDeleted", deletedLeagues,
                "humanTeamsRelocated", relocationPlan.size()
        );
    }

    /**
     * Find a bot team slot in another league (same country/format, not in the set being deleted)
     * that a human team can be placed into.
     * Prefers lower-tier divisions first: Div 3 → Div 2 → Div 1.
     */
    private LeagueTeam findBotSlotForHuman(String format, String country, List<League> excludeLeagues) {
        Set<UUID> excludeIds = new HashSet<>();
        for (League l : excludeLeagues) excludeIds.add(l.getId());

        List<League> candidates = new ArrayList<>(leagueRepository
                .findByCountryIgnoreCaseAndFormatOrderByDivisionAscLeagueNumberAsc(country, format));
        // Reverse so we check higher divisions (bottom tier) first: 3 → 2 → 1
        Collections.reverse(candidates);

        // Prefer bots NOT in an active match; fall back to any bot if all are mid-match
        LeagueTeam fallback = null;
        for (League candidate : candidates) {
            if (excludeIds.contains(candidate.getId())) continue;

            List<LeagueTeam> entries = leagueTeamRepository.findByLeagueId(candidate.getId());
            for (LeagueTeam entry : entries) {
                if (Boolean.TRUE.equals(entry.getTeam().getIsBot())) {
                    if (!fixtureService.hasActiveMatch(candidate.getId(), entry.getTeam().getId())) {
                        return entry;  // best case: idle bot
                    }
                    if (fallback == null) {
                        fallback = entry;  // remember first active-match bot as fallback
                    }
                }
            }
        }
        return fallback;  // null if no bots at all, or a mid-match bot (deferred swap handles it)
    }
}
