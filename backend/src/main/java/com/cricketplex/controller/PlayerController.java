package com.cricketplex.controller;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import com.cricketplex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/player")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerRepository playerRepository;
    private final BattingScorecardRepository battingScorecardRepository;
    private final BowlingScorecardRepository bowlingScorecardRepository;
    private final TrainingLogRepository trainingLogRepository;

    @GetMapping("/{playerId}")
    public ResponseEntity<?> getPlayerProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID playerId) {

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));

        Map<String, Object> resp = new LinkedHashMap<>();

        // ─── Basic Info ───
        resp.put("id", player.getId());
        resp.put("firstName", player.getFirstName());
        resp.put("lastName", player.getLastName());
        resp.put("country", player.getCountry());
        resp.put("nationality", player.getNationality());
        resp.put("role", player.getRole());
        resp.put("age", player.getAge());
        resp.put("ageDays", player.getAgeDays());
        resp.put("batHand", player.getBatHand());
        resp.put("bowlHand", player.getBowlHand());
        resp.put("bowlType", player.getBowlType());
        resp.put("batAggression", player.getBatAggression());
        resp.put("bowlAggression", player.getBowlAggression());
        resp.put("wage", player.getWage());
        resp.put("rating", player.getRating());
        resp.put("teamName", player.getTeam() != null ? player.getTeam().getTeamName() : "Free Agent");
        resp.put("teamId", player.getTeam() != null ? player.getTeam().getId() : null);

        // ─── Skills ───
        Map<String, Integer> skills = new LinkedHashMap<>();
        skills.put("batRating", (int) player.getBatRating());
        skills.put("bowlRating", (int) player.getBowlRating());
        skills.put("keeperRating", (int) player.getKeeperRating());
        skills.put("fldRating", (int) player.getFldRating());
        skills.put("experience", player.getExperience());
        skills.put("stamina", (int) player.getStamina());
        skills.put("fitness", player.getFitness());
        skills.put("confidence", (int) player.getConfidence());
        resp.put("skills", skills);

        // ─── Fetch all completed scorecards ───
        List<BattingScorecard> allBat = battingScorecardRepository.findByPlayerCompleted(playerId);
        List<BowlingScorecard> allBowl = bowlingScorecardRepository.findByPlayerCompleted(playerId);
        List<BattingScorecard> allFielding = battingScorecardRepository.findFieldingByPlayerCompleted(playerId);

        // ─── Build stats & last5 per format × matchType ───
        // Categorize: format (T20/ODI/FC) × matchType (LEAGUE/FRIENDLY)
        Map<String, List<BattingScorecard>> batByKey = new LinkedHashMap<>();
        Map<String, List<BowlingScorecard>> bowlByKey = new LinkedHashMap<>();
        Map<String, List<BattingScorecard>> fldByKey = new LinkedHashMap<>();

        for (BattingScorecard bs : allBat) {
            Fixture f = bs.getInnings().getMatchResult().getFixture();
            String format = resolveFormat(f);
            String type = f.getMatchType();
            String key = format + "_" + type;
            batByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(bs);
        }
        for (BowlingScorecard bs : allBowl) {
            Fixture f = bs.getInnings().getMatchResult().getFixture();
            String format = resolveFormat(f);
            String type = f.getMatchType();
            String key = format + "_" + type;
            bowlByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(bs);
        }
        for (BattingScorecard bs : allFielding) {
            Fixture f = bs.getInnings().getMatchResult().getFixture();
            String format = resolveFormat(f);
            String type = f.getMatchType();
            String key = format + "_" + type;
            fldByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(bs);
        }

        // Build stats for each format-type combo
        Map<String, Object> stats = new LinkedHashMap<>();
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(batByKey.keySet());
        allKeys.addAll(bowlByKey.keySet());
        allKeys.addAll(fldByKey.keySet());

        for (String key : allKeys) {
            String[] parts = key.split("_", 2);
            String format = parts[0];
            String type = parts[1];
            List<BattingScorecard> bats = batByKey.getOrDefault(key, List.of());
            List<BowlingScorecard> bowls = bowlByKey.getOrDefault(key, List.of());
            List<BattingScorecard> flds = fldByKey.getOrDefault(key, List.of());

            Map<String, Object> section = new LinkedHashMap<>();
            section.put("format", format);
            section.put("matchType", type);
            section.put("batting", buildBattingStats(bats));
            section.put("bowling", buildBowlingStats(bowls));
            section.put("fielding", buildFieldingStats(flds));
            section.put("last5", buildLast5(bats, bowls));
            stats.put(key, section);
        }
        resp.put("stats", stats);

        List<TrainingLog> trainingLogs = trainingLogRepository.findTop50ByPlayerIdOrderByTrainedAtDesc(playerId);
        resp.put("trainingHistory", mapTrainingLogs(trainingLogs));

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{playerId}/training-history")
    public ResponseEntity<?> getPlayerTrainingHistory(@PathVariable UUID playerId) {
        if (!playerRepository.existsById(playerId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Player not found"));
        }
        List<TrainingLog> trainingLogs = trainingLogRepository.findTop50ByPlayerIdOrderByTrainedAtDesc(playerId);
        return ResponseEntity.ok(mapTrainingLogs(trainingLogs));
    }

    private List<Map<String, Object>> mapTrainingLogs(List<TrainingLog> trainingLogs) {
        List<Map<String, Object>> trainingHistory = new ArrayList<>();
        for (TrainingLog log : trainingLogs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", log.getId());
            m.put("trainingType", log.getTrainingType());
            m.put("skill", log.getSkill());
            m.put("oldValue", log.getOldValue());
            m.put("newValue", log.getNewValue());
            m.put("change", log.getChange());
            m.put("trainedAt", log.getTrainedAt() != null ? log.getTrainedAt().toString() : null);
            trainingHistory.add(m);
        }
        return trainingHistory;
    }

    private String resolveFormat(Fixture f) {
        if (f.getLeague() != null) return f.getLeague().getFormat();
        return f.getFormat() != null ? f.getFormat() : "T20";
    }

    private Map<String, Object> buildBattingStats(List<BattingScorecard> cards) {
        Map<String, Object> s = new LinkedHashMap<>();
        if (cards.isEmpty()) {
            s.put("matches", 0);
            return s;
        }

        // Deduplicate by matchResult (a player might bat in 2 innings of same FC match)
        Set<UUID> matchIds = new LinkedHashSet<>();
        int totalRuns = 0, totalBalls = 0, totalFours = 0, totalSixes = 0;
        int highest = 0;
        boolean highestNotOut = false;
        int fifties = 0, hundreds = 0;
        int innings = 0, notOuts = 0;

        for (BattingScorecard bc : cards) {
            matchIds.add(bc.getInnings().getMatchResult().getId());
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

        s.put("matches", matchIds.size());
        s.put("innings", innings);
        s.put("runs", totalRuns);
        s.put("balls", totalBalls);
        s.put("highest", highest + (highestNotOut ? "*" : ""));
        s.put("average", avg);
        s.put("strikeRate", sr);
        s.put("fifties", fifties);
        s.put("hundreds", hundreds);
        s.put("fours", totalFours);
        s.put("sixes", totalSixes);
        s.put("notOuts", notOuts);
        return s;
    }

    private Map<String, Object> buildBowlingStats(List<BowlingScorecard> cards) {
        Map<String, Object> s = new LinkedHashMap<>();
        if (cards.isEmpty()) {
            s.put("matches", 0);
            return s;
        }

        Set<UUID> matchIds = new LinkedHashSet<>();
        int totalBalls = 0;
        int totalRuns = 0, totalWickets = 0, totalMaidens = 0;
        int bestWickets = 0, bestRuns = Integer.MAX_VALUE;
        int fiveWickets = 0, threeWickets = 0;

        for (BowlingScorecard bc : cards) {
            matchIds.add(bc.getInnings().getMatchResult().getId());
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

        // Convert total balls back to overs format
        double economy = totalBalls > 0 ? Math.round(totalRuns * 600.0 / totalBalls) / 100.0 : 0;
        double avg = totalWickets > 0 ? Math.round(totalRuns * 100.0 / totalWickets) / 100.0 : 0;
        double sr = totalWickets > 0 ? Math.round(totalBalls * 100.0 / totalWickets) / 100.0 : 0;

        s.put("matches", matchIds.size());
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

    private Map<String, Object> buildFieldingStats(List<BattingScorecard> cards) {
        Map<String, Object> s = new LinkedHashMap<>();
        if (cards.isEmpty()) {
            s.put("matches", 0);
            return s;
        }

        Set<UUID> matchIds = new LinkedHashSet<>();
        int catches = 0, stumpings = 0, runOuts = 0, caughtBehind = 0;

        for (BattingScorecard bc : cards) {
            matchIds.add(bc.getInnings().getMatchResult().getId());
            String dt = bc.getDismissalType();
            if (dt == null) continue;
            switch (dt) {
                case "CAUGHT" -> catches++;
                case "STUMPED" -> stumpings++;
                case "RUN_OUT" -> runOuts++;
                case "CAUGHT_BEHIND" -> caughtBehind++;
            }
        }

        s.put("matches", matchIds.size());
        s.put("catches", catches + caughtBehind);
        s.put("stumpings", stumpings);
        s.put("runOuts", runOuts);
        s.put("total", catches + caughtBehind + stumpings + runOuts);
        return s;
    }

    private List<Map<String, Object>> buildLast5(List<BattingScorecard> bats, List<BowlingScorecard> bowls) {
        // Group by matchResult id, keep order from bats (already sorted by matchDate DESC)
        Map<UUID, Map<String, Object>> matchMap = new LinkedHashMap<>();

        for (BattingScorecard bc : bats) {
            UUID mrId = bc.getInnings().getMatchResult().getId();
            Map<String, Object> entry = matchMap.computeIfAbsent(mrId, k -> {
                Fixture f = bc.getInnings().getMatchResult().getFixture();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fixtureId", f.getId());
                m.put("date", f.getMatchDate() != null ? f.getMatchDate().toString() : null);
                m.put("vs", getOpponent(f, bc.getPlayer()));
                m.put("batInnings", new ArrayList<Map<String, Object>>());
                m.put("bowlInnings", new ArrayList<Map<String, Object>>());
                return m;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> batList = (List<Map<String, Object>>) entry.get("batInnings");
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("runs", bc.getRunsScored());
            b.put("balls", bc.getBallsFaced());
            b.put("fours", bc.getFours());
            b.put("sixes", bc.getSixes());
            b.put("notOut", bc.getDismissalType() == null || bc.getDismissalType().isEmpty());
            batList.add(b);
        }

        for (BowlingScorecard bc : bowls) {
            UUID mrId = bc.getInnings().getMatchResult().getId();
            Map<String, Object> entry = matchMap.computeIfAbsent(mrId, k -> {
                Fixture f = bc.getInnings().getMatchResult().getFixture();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fixtureId", f.getId());
                m.put("date", f.getMatchDate() != null ? f.getMatchDate().toString() : null);
                m.put("vs", getOpponent(f, bc.getPlayer()));
                m.put("batInnings", new ArrayList<Map<String, Object>>());
                m.put("bowlInnings", new ArrayList<Map<String, Object>>());
                return m;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bowlList = (List<Map<String, Object>>) entry.get("bowlInnings");
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("overs", bc.getOvers());
            b.put("runs", bc.getRunsConceded());
            b.put("wickets", bc.getWickets());
            b.put("maidens", bc.getMaidens());
            b.put("economy", bc.getEconomy());
            bowlList.add(b);
        }

        // Return only last 5 matches
        return matchMap.values().stream().limit(5).toList();
    }

    private String getOpponent(Fixture f, Player p) {
        if (p.getTeam() == null) return "Unknown";
        if (f.getHomeTeam().getId().equals(p.getTeam().getId())) {
            return f.getAwayTeam().getTeamName();
        }
        return f.getHomeTeam().getTeamName();
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
