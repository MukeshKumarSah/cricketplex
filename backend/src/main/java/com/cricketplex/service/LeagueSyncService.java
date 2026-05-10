package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fixes standings for teams that joined mid-season while a match was IN_PROGRESS.
 *
 * Root cause:
 *   pendingSwaps in FixtureService is in-memory. If a match completes without
 *   the swap being applied (e.g. match was finishing as the team joined, or
 *   any timing gap), the Innings records still reference the old bot team.
 *   The new human team ends up with 0 stats even though those matches count.
 *
 * Fix strategy:
 *   1. Collect all team IDs currently in the league (leagueTeams).
 *   2. Scan every COMPLETED match result's Innings for team IDs NOT in (1) -> ghost bots.
 *   3. Find the replacement teams: teams in (1) that have never appeared in any Innings -> orphans.
 *   4. Build ghost->orphan mapping using fixture context for accuracy.
 *   5. Write the corrected team refs back to Innings + MatchResult.winner/tossWinner.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeagueSyncService {

    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final FixtureRepository fixtureRepository;
    private final MatchResultRepository matchResultRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    /**
     * Fix all orphaned team swaps for a league+season, then return a summary.
     * Called by the user-facing "Sync" endpoint and admin endpoints.
     */
    @Transactional
    public Map<String, Object> fixOrphanedSwaps(UUID leagueId, int season) {
        log.info("fixOrphanedSwaps: league={} season={}", leagueId, season);

        // 1. Current teams in league
        List<LeagueTeam> leagueTeams = leagueTeamRepository.findByLeagueIdAndSeason(leagueId, season);
        Set<UUID> currentTeamIds = leagueTeams.stream()
                .map(lt -> lt.getTeam().getId())
                .collect(Collectors.toSet());

        // 2. Load all completed fixtures + their results
        List<Fixture> allFixtures = fixtureRepository
                .findByLeagueIdAndSeasonOrderByRoundAscMatchNumberAsc(leagueId, season);
        List<Fixture> completed = allFixtures.stream()
                .filter(f -> "COMPLETED".equals(f.getStatus()))
                .collect(Collectors.toList());

        if (completed.isEmpty()) {
            return result("success", 0, 0, "No completed matches yet — nothing to fix");
        }

        // Map fixtureId -> MatchResult; collect all team IDs seen in innings
        Map<UUID, MatchResult> resultMap = new LinkedHashMap<>();
        Set<UUID> seenInInnings = new HashSet<>();

        for (Fixture f : completed) {
            matchResultRepository.findByFixtureId(f.getId()).ifPresent(mr -> {
                resultMap.put(f.getId(), mr);
                for (Innings inn : mr.getInningsList()) {
                    if (inn.getBattingTeam()  != null) seenInInnings.add(inn.getBattingTeam().getId());
                    if (inn.getBowlingTeam()  != null) seenInInnings.add(inn.getBowlingTeam().getId());
                }
            });
        }

        // 3. Ghost bots: appear in innings but NOT in current league
        Set<UUID> ghostBotIds = new HashSet<>(seenInInnings);
        ghostBotIds.removeAll(currentTeamIds);

        if (ghostBotIds.isEmpty()) {
            return result("success", 0, 0,
                    "Standings are already accurate — no orphaned team references found");
        }

        // 4. Build ghost -> human mapping using round-based fixture ref analysis
        Map<UUID, UUID> swapMap = buildSwapMap(ghostBotIds, currentTeamIds, completed);
        if (swapMap.isEmpty()) {
            return result("warning", 0, ghostBotIds.size(),
                    "Could not determine correct team mapping for orphaned swaps");
        }

        // Load replacement Team entities
        Map<UUID, Team> replacementTeams = new HashMap<>();        for (UUID newId : swapMap.values()) {
            teamRepository.findById(newId).ifPresent(t -> replacementTeams.put(newId, t));
        }

        // 6. Apply swaps to every affected MatchResult + Innings
        int fixedMatches = 0;
        for (Fixture f : completed) {
            MatchResult mr = resultMap.get(f.getId());
            if (mr == null) continue;

            boolean changed = false;

            if (mr.getWinner() != null) {
                UUID rId = swapMap.get(mr.getWinner().getId());
                if (rId != null && replacementTeams.containsKey(rId)) {
                    mr.setWinner(replacementTeams.get(rId));
                    changed = true;
                }
            }
            if (mr.getTossWinner() != null) {
                UUID rId = swapMap.get(mr.getTossWinner().getId());
                if (rId != null && replacementTeams.containsKey(rId)) {
                    mr.setTossWinner(replacementTeams.get(rId));
                    changed = true;
                }
            }
            for (Innings inn : mr.getInningsList()) {
                if (inn.getBattingTeam() != null) {
                    UUID rId = swapMap.get(inn.getBattingTeam().getId());
                    if (rId != null && replacementTeams.containsKey(rId)) {
                        inn.setBattingTeam(replacementTeams.get(rId));
                        changed = true;
                    }
                }
                if (inn.getBowlingTeam() != null) {
                    UUID rId = swapMap.get(inn.getBowlingTeam().getId());
                    if (rId != null && replacementTeams.containsKey(rId)) {
                        inn.setBowlingTeam(replacementTeams.get(rId));
                        changed = true;
                    }
                }
            }

            if (changed) {
                matchResultRepository.save(mr);
                fixedMatches++;
            }
        }

        log.info("fixOrphanedSwaps done: league={} season={} — fixed {} matches, swaps={}",
                leagueId, season, fixedMatches, swapMap);

        String msg = fixedMatches > 0
                ? "Fixed " + fixedMatches + " match result(s). Standings are now up to date."
                : "Swaps mapped but no match results required changes.";
        return result("success", fixedMatches, ghostBotIds.size(), msg);
    }

    /**
     * Sync all leagues for a country+format (admin utility).
     */
    @Transactional
    public Map<String, Object> syncCountryLeagues(String country, String format) {
        int currentSeason = leagueRepository.findMaxSeason();
        List<League> leagues = leagueRepository
                .findByCountryIgnoreCaseAndFormatAndSeasonOrderByDivisionAscLeagueNumberAsc(
                        country, format, currentSeason);

        if (leagues.isEmpty()) {
            return result("error", 0, 0, "No leagues found for " + country + " " + format);
        }

        int totalFixed = 0;
        List<Map<String, Object>> perLeague = new ArrayList<>();
        for (League league : leagues) {
            Map<String, Object> r = fixOrphanedSwaps(league.getId(), currentSeason);
            int fixed = (Integer) r.getOrDefault("fixedMatches", 0);
            totalFixed += fixed;
            perLeague.add(Map.of(
                    "league", label(league),
                    "division", league.getDivision(),
                    "fixedMatches", fixed,
                    "status", r.get("status"),
                    "message", r.get("message")));
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("country", country);
        res.put("format", format);
        res.put("season", currentSeason);
        res.put("leaguesProcessed", leagues.size());
        res.put("totalFixedMatches", totalFixed);
        res.put("perLeague", perLeague);
        res.put("status", "success");
        res.put("message", "Sync complete — " + totalFixed + " match result(s) fixed across " + leagues.size() + " league(s)");
        return res;
    }

    /**
     * For each ghost bot, find which current team replaced it.
     *
     * Key insight: when a match was IN_PROGRESS at join time, swapTeamInFixtures()
     * skips updating fixture.homeTeam/awayTeam. So the COMPLETED fixture still
     * references the ghost bot in its home/away columns, not the replacement human.
     *
     * In a round-robin, every team plays exactly once per round. So for the rounds
     * where the ghost still appears in fixture refs, ALL other 7 teams also appear
     * in that round's fixture refs — EXCEPT the replacement, whose slot shows the ghost.
     *
     * Therefore: the replacement = the current league team that does NOT appear
     * in fixture refs for any round where the ghost appears in fixture refs.
     */
    private Map<UUID, UUID> buildSwapMap(Set<UUID> ghostBotIds, Set<UUID> currentTeamIds,
                                         List<Fixture> completedFixtures) {
        Map<UUID, UUID> swapMap = new HashMap<>();
        Set<UUID> assignedReplacements = new HashSet<>();

        for (UUID ghostId : ghostBotIds) {
            // Rounds where ghost still appears in fixture refs (unfixed IN_PROGRESS fixtures)
            Set<Integer> ghostRounds = completedFixtures.stream()
                    .filter(f -> (f.getHomeTeam() != null && f.getHomeTeam().getId().equals(ghostId))
                              || (f.getAwayTeam() != null && f.getAwayTeam().getId().equals(ghostId)))
                    .map(Fixture::getRound)
                    .collect(Collectors.toSet());

            if (ghostRounds.isEmpty()) {
                log.warn("Ghost bot {} not found in any completed fixture refs — cannot determine replacement", ghostId);
                continue;
            }

            // All team IDs appearing in fixture refs for those rounds
            Set<UUID> teamsInGhostRoundRefs = completedFixtures.stream()
                    .filter(f -> ghostRounds.contains(f.getRound()))
                    .flatMap(f -> {
                        Set<UUID> ids = new HashSet<>();
                        if (f.getHomeTeam() != null) ids.add(f.getHomeTeam().getId());
                        if (f.getAwayTeam() != null) ids.add(f.getAwayTeam().getId());
                        return ids.stream();
                    })
                    .collect(Collectors.toSet());

            // Replacement is the current team NOT seen in those rounds' fixture refs
            List<UUID> candidates = currentTeamIds.stream()
                    .filter(id -> !teamsInGhostRoundRefs.contains(id))
                    .filter(id -> !assignedReplacements.contains(id))
                    .collect(Collectors.toList());

            if (candidates.size() == 1) {
                swapMap.put(ghostId, candidates.get(0));
                assignedReplacements.add(candidates.get(0));
                log.info("Identified replacement: ghost={} → replacement={}", ghostId, candidates.get(0));
            } else if (candidates.isEmpty()) {
                log.warn("No replacement candidate found for ghost {} in rounds {} — all current teams appear in those round fixture refs", ghostId, ghostRounds);
            } else {
                // Multiple candidates: pick the non-bot one (the human team)
                UUID chosen = candidates.stream()
                        .map(id -> teamRepository.findById(id).orElse(null))
                        .filter(t -> t != null && !Boolean.TRUE.equals(t.getIsBot()))
                        .map(Team::getId)
                        .findFirst()
                        .orElse(candidates.get(0));
                swapMap.put(ghostId, chosen);
                assignedReplacements.add(chosen);
                log.info("Multiple candidates for ghost {} — chose non-bot {}", ghostId, chosen);
            }
        }
        return swapMap;
    }

    private static Map<String, Object> result(String status, int fixedMatches, int ghostsFound, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("fixedMatches", fixedMatches);
        m.put("ghostTeamsFound", ghostsFound);
        m.put("message", message);
        return m;
    }

    private static String label(League l) {
        return l.getCountry() + " " + l.getFormat() + " D" + l.getDivision() + " L" + l.getLeagueNumber();
    }
}
