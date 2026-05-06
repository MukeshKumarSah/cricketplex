package com.cricketplex.controller;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import com.cricketplex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final BattingScorecardRepository battingScorecardRepository;
    private final BowlingScorecardRepository bowlingScorecardRepository;
    private final LineupPlayerRepository lineupPlayerRepository;

    @GetMapping
    public ResponseEntity<?> getTeamStats(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "T20") String format,
            @RequestParam(required = false, defaultValue = "LEAGUE") String matchType,
            @RequestParam(required = false) Integer season) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Team team = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("No team found"));

        List<Player> players = playerRepository.findByTeam(team);

        // Fetch all scorecards for team at once
        List<BattingScorecard> allBat = battingScorecardRepository.findByTeamCompleted(team.getId());
        List<BowlingScorecard> allBowl = bowlingScorecardRepository.findByTeamCompleted(team.getId());
        List<BattingScorecard> allFld = battingScorecardRepository.findFieldingByTeamCompleted(team.getId());

        // Filter by format, matchType (and optionally season for CUP)
        List<BattingScorecard> filteredBat = allBat.stream()
                .filter(bs -> {
                    Fixture f = bs.getInnings().getMatchResult().getFixture();
                    return resolveFormat(f).equals(format)
                        && f.getMatchType().equals(matchType)
                        && (season == null || Objects.equals(f.getSeason(), season));
                })
                .toList();
        List<BowlingScorecard> filteredBowl = allBowl.stream()
                .filter(bs -> {
                    Fixture f = bs.getInnings().getMatchResult().getFixture();
                    return resolveFormat(f).equals(format)
                        && f.getMatchType().equals(matchType)
                        && (season == null || Objects.equals(f.getSeason(), season));
                })
                .toList();
        List<BattingScorecard> filteredFld = allFld.stream()
                .filter(bs -> {
                    Fixture f = bs.getInnings().getMatchResult().getFixture();
                    return resolveFormat(f).equals(format)
                        && f.getMatchType().equals(matchType)
                        && (season == null || Objects.equals(f.getSeason(), season));
                })
                .toList();

        // Group by player ID
        Map<UUID, List<BattingScorecard>> batByPlayer = filteredBat.stream()
                .collect(Collectors.groupingBy(bs -> bs.getPlayer().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<BowlingScorecard>> bowlByPlayer = filteredBowl.stream()
                .collect(Collectors.groupingBy(bs -> bs.getPlayer().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<BattingScorecard>> fldByPlayer = filteredFld.stream()
                .collect(Collectors.groupingBy(bs -> bs.getFielder().getId(), LinkedHashMap::new, Collectors.toList()));

        // Build true "matches played" per player from lineup data
        Set<UUID> allFixtureIds = new LinkedHashSet<>();
        filteredBat.forEach(bs -> allFixtureIds.add(bs.getInnings().getMatchResult().getFixture().getId()));
        filteredBowl.forEach(bs -> allFixtureIds.add(bs.getInnings().getMatchResult().getFixture().getId()));
        Map<UUID, Set<UUID>> playerMatchIds = new LinkedHashMap<>();
        if (!allFixtureIds.isEmpty()) {
            lineupPlayerRepository
                    .findPlayerFixturePairsByTeamAndFixtures(team.getId(), allFixtureIds)
                    .forEach(row -> playerMatchIds
                            .computeIfAbsent((UUID) row[0], k -> new LinkedHashSet<>())
                            .add((UUID) row[1]));
        }

        // Build per-player stats
        List<Map<String, Object>> battingStats = new ArrayList<>();
        List<Map<String, Object>> bowlingStats = new ArrayList<>();
        List<Map<String, Object>> fieldingStats = new ArrayList<>();

        for (Player p : players) {
            UUID pid = p.getId();
            Map<String, Object> base = new LinkedHashMap<>();
            base.put("id", pid);
            base.put("name", p.getFirstName() + " " + p.getLastName());
            base.put("role", p.getRole());
            int totalMatches = playerMatchIds.getOrDefault(pid, Set.of()).size();

            // Batting
            List<BattingScorecard> pBat = batByPlayer.getOrDefault(pid, List.of());
            if (!pBat.isEmpty()) {
                Map<String, Object> row = new LinkedHashMap<>(base);
                row.putAll(buildBattingStats(pBat, totalMatches));
                battingStats.add(row);
            }

            // Bowling
            List<BowlingScorecard> pBowl = bowlByPlayer.getOrDefault(pid, List.of());
            if (!pBowl.isEmpty()) {
                Map<String, Object> row = new LinkedHashMap<>(base);
                row.putAll(buildBowlingStats(pBowl, totalMatches));
                bowlingStats.add(row);
            }

            // Fielding
            List<BattingScorecard> pFld = fldByPlayer.getOrDefault(pid, List.of());
            if (!pFld.isEmpty()) {
                Map<String, Object> row = new LinkedHashMap<>(base);
                row.putAll(buildFieldingStats(pFld, totalMatches));
                fieldingStats.add(row);
            }
        }

        // Sort: batting by runs DESC, bowling by wickets DESC, fielding by total DESC
        battingStats.sort((a, b) -> Integer.compare((int) b.get("runs"), (int) a.get("runs")));
        bowlingStats.sort((a, b) -> Integer.compare((int) b.get("wickets"), (int) a.get("wickets")));
        fieldingStats.sort((a, b) -> Integer.compare((int) b.get("total"), (int) a.get("total")));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("format", format);
        resp.put("matchType", matchType);
        resp.put("season", season);
        resp.put("teamName", team.getTeamName());
        resp.put("batting", battingStats);
        resp.put("bowling", bowlingStats);
        resp.put("fielding", fieldingStats);

        return ResponseEntity.ok(resp);
    }

    private String resolveFormat(Fixture f) {
        if (f.getLeague() != null) return f.getLeague().getFormat();
        return f.getFormat() != null ? f.getFormat() : "T20";
    }

    // ── Cup-wide stats (all players across a cup season) ──────────────────────

    @GetMapping("/cup")
    public ResponseEntity<?> getCupStats(
            @RequestParam int season) {

        List<BattingScorecard>  allBat = battingScorecardRepository.findByCupSeason(season);
        List<BowlingScorecard>  allBowl = bowlingScorecardRepository.findByCupSeason(season);
        List<BattingScorecard>  allFld = battingScorecardRepository.findFieldingByCupSeason(season);

        // Group by player
        Map<UUID, List<BattingScorecard>> batByPlayer = allBat.stream()
                .collect(Collectors.groupingBy(bs -> bs.getPlayer().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<BowlingScorecard>> bowlByPlayer = allBowl.stream()
                .collect(Collectors.groupingBy(bs -> bs.getPlayer().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, List<BattingScorecard>> fldByPlayer = allFld.stream()
                .collect(Collectors.groupingBy(bs -> bs.getFielder().getId(), LinkedHashMap::new, Collectors.toList()));

        // Match counts from batting + bowling fixture ids per player
        Map<UUID, Set<UUID>> playerMatchIds = new LinkedHashMap<>();
        allBat.forEach(bs -> playerMatchIds
                .computeIfAbsent(bs.getPlayer().getId(), k -> new LinkedHashSet<>())
                .add(bs.getInnings().getMatchResult().getFixture().getId()));
        allBowl.forEach(bs -> playerMatchIds
                .computeIfAbsent(bs.getPlayer().getId(), k -> new LinkedHashSet<>())
                .add(bs.getInnings().getMatchResult().getFixture().getId()));

        // Collect all distinct players using map-based O(1) lookup (not linear scan)
        Set<UUID> allPlayerIds = new LinkedHashSet<>();
        allPlayerIds.addAll(batByPlayer.keySet());
        allPlayerIds.addAll(bowlByPlayer.keySet());
        allPlayerIds.addAll(fldByPlayer.keySet());

        // Build player lookup map directly from already-loaded scorecard data
        Map<UUID, Player> playerById = new LinkedHashMap<>();
        allBat.forEach(bs  -> playerById.putIfAbsent(bs.getPlayer().getId(), bs.getPlayer()));
        allBowl.forEach(bs -> playerById.putIfAbsent(bs.getPlayer().getId(), bs.getPlayer()));

        List<Map<String, Object>> battingStats  = new ArrayList<>();
        List<Map<String, Object>> bowlingStats  = new ArrayList<>();
        List<Map<String, Object>> fieldingStats = new ArrayList<>();

        for (UUID pid : allPlayerIds) {
            Player p = playerById.get(pid);
            if (p == null) continue;

            Map<String, Object> base = new LinkedHashMap<>();
            base.put("id",       pid);
            base.put("name",     p.getFirstName() + " " + p.getLastName());
            base.put("role",     p.getRole());
            base.put("teamName", p.getTeam() != null ? p.getTeam().getTeamName() : "");
            int matches = playerMatchIds.getOrDefault(pid, Set.of()).size();

            List<BattingScorecard>  pBat  = batByPlayer.getOrDefault(pid, List.of());
            List<BowlingScorecard>  pBowl = bowlByPlayer.getOrDefault(pid, List.of());
            List<BattingScorecard>  pFld  = fldByPlayer.getOrDefault(pid, List.of());

            if (!pBat.isEmpty())  { Map<String, Object> r = new LinkedHashMap<>(base); r.putAll(buildBattingStats(pBat, matches));  battingStats.add(r); }
            if (!pBowl.isEmpty()) { Map<String, Object> r = new LinkedHashMap<>(base); r.putAll(buildBowlingStats(pBowl, matches)); bowlingStats.add(r); }
            if (!pFld.isEmpty())  { Map<String, Object> r = new LinkedHashMap<>(base); r.putAll(buildFieldingStats(pFld, matches)); fieldingStats.add(r); }
        }

        battingStats.sort((a, b)  -> Integer.compare((int) b.get("runs"),    (int) a.get("runs")));
        bowlingStats.sort((a, b)  -> Integer.compare((int) b.get("wickets"), (int) a.get("wickets")));
        fieldingStats.sort((a, b) -> Integer.compare((int) b.get("total"),   (int) a.get("total")));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("season",  season);
        resp.put("batting",  battingStats);
        resp.put("bowling",  bowlingStats);
        resp.put("fielding", fieldingStats);
        return ResponseEntity.ok(resp);
    }

    private Map<String, Object> buildBattingStats(List<BattingScorecard> cards, int matchCount) {
        Map<String, Object> s = new LinkedHashMap<>();
        int totalRuns = 0, totalBalls = 0, totalFours = 0, totalSixes = 0;
        int highest = 0;
        boolean highestNotOut = false;
        int innings = 0, notOuts = 0, fifties = 0, hundreds = 0;

        for (BattingScorecard bc : cards) {
            innings++;
            boolean isNotOut = bc.getDismissalType() == null || bc.getDismissalType().isEmpty();
            if (isNotOut) notOuts++;
            int runs = bc.getRunsScored();
            totalRuns += runs;
            totalBalls += bc.getBallsFaced();
            totalFours += bc.getFours();
            totalSixes += bc.getSixes();
            if (runs > highest || (runs == highest && isNotOut && !highestNotOut)) {
                highest = runs;
                highestNotOut = isNotOut;
            }
            if (runs >= 100) hundreds++;
            else if (runs >= 50) fifties++;
        }

        int dismissals = innings - notOuts;
        double avg = dismissals > 0 ? Math.round(totalRuns * 100.0 / dismissals) / 100.0 : totalRuns;
        double sr = totalBalls > 0 ? Math.round(totalRuns * 10000.0 / totalBalls) / 100.0 : 0;

        s.put("matches", matchCount);
        s.put("innings", innings);
        s.put("notOuts", notOuts);
        s.put("runs", totalRuns);
        s.put("highest", highest + (highestNotOut ? "*" : ""));
        s.put("average", avg);
        s.put("strikeRate", sr);
        s.put("hundreds", hundreds);
        s.put("fifties", fifties);
        s.put("fours", totalFours);
        s.put("sixes", totalSixes);
        return s;
    }

    private Map<String, Object> buildBowlingStats(List<BowlingScorecard> cards, int matchCount) {
        Map<String, Object> s = new LinkedHashMap<>();
        int totalBalls = 0, totalRuns = 0, totalWickets = 0, totalMaidens = 0;
        int bestWickets = 0, bestRuns = Integer.MAX_VALUE;
        int fiveWickets = 0, threeWickets = 0;

        for (BowlingScorecard bc : cards) {
            totalBalls += oversToBalls(bc.getOvers());
            totalRuns += bc.getRunsConceded();
            totalWickets += bc.getWickets();
            totalMaidens += bc.getMaidens();
            if (bc.getWickets() > bestWickets ||
                    (bc.getWickets() == bestWickets && bc.getRunsConceded() < bestRuns)) {
                bestWickets = bc.getWickets();
                bestRuns = bc.getRunsConceded();
            }
            if (bc.getWickets() >= 5) fiveWickets++;
            else if (bc.getWickets() >= 3) threeWickets++;
        }

        double economy = totalBalls > 0 ? Math.round(totalRuns * 600.0 / totalBalls) / 100.0 : 0;
        double avg = totalWickets > 0 ? Math.round(totalRuns * 100.0 / totalWickets) / 100.0 : 0;
        double sr = totalWickets > 0 ? Math.round(totalBalls * 100.0 / totalWickets) / 100.0 : 0;

        s.put("matches", matchCount);
        s.put("innings", cards.size());
        s.put("overs", ballsToOvers(totalBalls));
        s.put("runs", totalRuns);
        s.put("wickets", totalWickets);
        s.put("best", bestWickets + "/" + (bestRuns == Integer.MAX_VALUE ? 0 : bestRuns));
        s.put("average", avg);
        s.put("economy", economy);
        s.put("strikeRate", sr);
        s.put("maidens", totalMaidens);
        s.put("fiveWickets", fiveWickets);
        s.put("threeWickets", threeWickets);
        return s;
    }

    private Map<String, Object> buildFieldingStats(List<BattingScorecard> cards, int matchCount) {
        Map<String, Object> s = new LinkedHashMap<>();
        int catches = 0, stumpings = 0, runOuts = 0, caughtBehind = 0;

        for (BattingScorecard bc : cards) {
            String dt = bc.getDismissalType();
            if (dt == null) continue;
            switch (dt) {
                case "CAUGHT" -> catches++;
                case "STUMPED" -> stumpings++;
                case "RUN_OUT" -> runOuts++;
                case "CAUGHT_BEHIND" -> caughtBehind++;
            }
        }

        s.put("matches", matchCount);
        s.put("catches", catches + caughtBehind);
        s.put("stumpings", stumpings);
        s.put("runOuts", runOuts);
        s.put("total", catches + caughtBehind + stumpings + runOuts);
        return s;
    }

    private int oversToBalls(double overs) {
        int full = (int) overs;
        int partialBalls = (int) Math.round((overs - full) * 10);
        return full * 6 + partialBalls;
    }

    private double ballsToOvers(int balls) {
        int fullOvers = balls / 6;
        int remaining = balls % 6;
        return fullOvers + remaining / 10.0;
    }
}
