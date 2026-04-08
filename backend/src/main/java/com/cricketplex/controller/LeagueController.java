package com.cricketplex.controller;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import com.cricketplex.service.FixtureService;
import com.cricketplex.service.MatchEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api/leagues")
@RequiredArgsConstructor
public class LeagueController {

    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final FixtureRepository fixtureRepository;
    private final MatchResultRepository matchResultRepository;
    private final BallEventRepository ballEventRepository;
    private final FixtureService fixtureService;
    private final MatchEngine matchEngine;

    @GetMapping("/{id}")
    public ResponseEntity<?> getLeagueDetail(@PathVariable UUID id) {
        Optional<League> opt = leagueRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        League league = opt.get();
        List<LeagueTeam> leagueTeams = leagueTeamRepository.findByLeagueId(id);

        // Build standings from completed match results
        List<Fixture> fixtures = fixtureRepository.findByLeagueIdOrderByRoundAscMatchNumberAsc(id);
        Map<UUID, int[]> stats = new LinkedHashMap<>(); // [played, won, lost, tied, points]
        // For FC: [runsScored, wicketsLost, runsConceded, wicketsTaken]
        // For T20/ODI: [runsScored, oversPlayed, runsConceded, oversBowled]
        Map<UUID, double[]> nrrData = new LinkedHashMap<>();
        boolean isFC = "FC".equals(league.getFormat());

        for (LeagueTeam lt : leagueTeams) {
            stats.put(lt.getTeam().getId(), new int[5]);
            nrrData.put(lt.getTeam().getId(), new double[4]);
        }

        for (Fixture f : fixtures) {
            if (!"COMPLETED".equals(f.getStatus())) continue;
            Optional<MatchResult> mrOpt = matchResultRepository.findByFixtureId(f.getId());
            if (mrOpt.isEmpty()) continue;
            MatchResult mr = mrOpt.get();

            // Derive team IDs from innings (handles bot→human swaps where
            // fixture still references old bot but innings reference new team)
            List<Innings> innList = mr.getInningsList();
            if (innList.isEmpty()) continue;
            UUID teamA = innList.get(0).getBattingTeam().getId();
            UUID teamB = innList.get(0).getBowlingTeam().getId();

            int[] homeStats = stats.get(teamA);
            int[] awayStats = stats.get(teamB);
            if (homeStats == null || awayStats == null) continue;

            homeStats[0]++;
            awayStats[0]++;

            // Accumulate data from innings
            for (Innings inn : mr.getInningsList()) {
                UUID batTeamId = inn.getBattingTeam().getId();
                UUID bowlTeamId = inn.getBowlingTeam().getId();
                int runs = inn.getTotalRuns() != null ? inn.getTotalRuns() : 0;
                int wickets = inn.getTotalWickets() != null ? inn.getTotalWickets() : 0;

                double[] batNrr = nrrData.get(batTeamId);
                double[] bowlNrr = nrrData.get(bowlTeamId);

                if (isFC) {
                    // FC quotient: batting avg / bowling avg
                    if (batNrr != null) { batNrr[0] += runs; batNrr[1] += wickets; }
                    if (bowlNrr != null) { bowlNrr[2] += runs; bowlNrr[3] += wickets; }
                } else {
                    // T20/ODI NRR: runs per over scored - runs per over conceded
                    double overs;
                    if (Boolean.TRUE.equals(inn.getAllOut())) {
                        overs = getMaxOvers(league.getFormat());
                    } else {
                        overs = oversToDecimal(inn.getTotalOvers() != null ? inn.getTotalOvers() : 0.0);
                    }
                    if (batNrr != null) { batNrr[0] += runs; batNrr[1] += overs; }
                    if (bowlNrr != null) { bowlNrr[2] += runs; bowlNrr[3] += overs; }
                }
            }

            if ("TIE".equals(mr.getResultType())) {
                homeStats[3]++;
                awayStats[3]++;
                homeStats[4] += 1;
                awayStats[4] += 1;
            } else if (mr.getWinner() != null) {
                UUID winnerId = mr.getWinner().getId();
                if (winnerId.equals(teamA)) {
                    homeStats[1]++;
                    awayStats[2]++;
                    homeStats[4] += 2;
                } else if (winnerId.equals(teamB)) {
                    awayStats[1]++;
                    homeStats[2]++;
                    awayStats[4] += 2;
                }
            }
        }

        List<Map<String, Object>> standings = new ArrayList<>();
        for (LeagueTeam lt : leagueTeams) {
            Team team = lt.getTeam();
            int[] s = stats.get(team.getId());
            double[] n = nrrData.get(team.getId());
            double tiebreaker = 0.0;
            if (isFC) {
                // Quotient = batting average / bowling average
                double batAvg = n[1] > 0 ? n[0] / n[1] : 0.0;
                double bowlAvg = n[3] > 0 ? n[2] / n[3] : 0.0;
                tiebreaker = bowlAvg > 0 ? batAvg / bowlAvg : 0.0;
            } else {
                if (n[1] > 0 && n[3] > 0) {
                    tiebreaker = (n[0] / n[1]) - (n[2] / n[3]);
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("teamId", team.getId());
            row.put("teamName", team.getTeamName());
            row.put("teamProfilePicUrl", team.getTeamProfilePicUrl());
            row.put("isBot", team.getIsBot());
            row.put("played", s[0]);
            row.put("won", s[1]);
            row.put("lost", s[2]);
            row.put("tied", s[3]);
            row.put("points", s[4]);
            row.put("nrr", Math.round(tiebreaker * 1000.0) / 1000.0);
            standings.add(row);
        }

        // Sort by points DESC, then tiebreaker DESC, then wins DESC
        standings.sort((a, b) -> {
            int cmp = Integer.compare((int) b.get("points"), (int) a.get("points"));
            if (cmp != 0) return cmp;
            cmp = Double.compare((double) b.get("nrr"), (double) a.get("nrr"));
            if (cmp != 0) return cmp;
            return Integer.compare((int) b.get("won"), (int) a.get("won"));
        });

        // Assign positions after sorting
        for (int i = 0; i < standings.size(); i++) {
            standings.get(i).put("position", i + 1);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", league.getId());
        result.put("country", league.getCountry());
        result.put("format", league.getFormat());
        result.put("division", league.getDivision());
        result.put("leagueNumber", league.getLeagueNumber());
        result.put("leagueId", league.getDivision() + "." + league.getLeagueNumber());
        result.put("season", league.getSeason());
        result.put("totalTeams", leagueTeams.size());
        result.put("matchStartTimeUtc", league.getMatchStartTime());
        result.put("standings", standings);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/fixtures")
    public ResponseEntity<?> getLeagueFixtures(@PathVariable UUID id) {
        Optional<League> opt = leagueRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        League league = opt.get();

        // Eagerly simulate any overdue SCHEDULED fixtures so they appear as live
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        String startTimeStr = league.getMatchStartTime() != null ? league.getMatchStartTime() : "14:00";
        LocalTime matchTime = LocalTime.parse(startTimeStr);
        List<Fixture> leagueFixtures = fixtureRepository.findByLeagueIdOrderByRoundAscMatchNumberAsc(id);
        for (Fixture f : leagueFixtures) {
            if (!"SCHEDULED".equals(f.getStatus())) continue;
            LocalDateTime matchStart = LocalDateTime.of(f.getMatchDate(), matchTime);
            if (nowUtc.isBefore(matchStart)) continue;
            if (matchResultRepository.existsByFixtureId(f.getId())) continue;
            try {
                matchEngine.simulateMatch(f.getId());
                f.setStatus("IN_PROGRESS");
                fixtureRepository.save(f);
            } catch (Exception ignored) { }
        }

        List<Map<String, Object>> fixtures = fixtureService.getFixturesGroupedByRound(league);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leagueId", league.getDivision() + "." + league.getLeagueNumber());
        result.put("format", league.getFormat());
        result.put("totalRounds", fixtures.size());
        result.put("rounds", fixtures);
        return ResponseEntity.ok(result);
    }

    // ── League Stats (Batting / Bowling / Fielding) ──

    @GetMapping("/{id}/stats")
    public ResponseEntity<?> getLeagueStats(@PathVariable UUID id) {
        Optional<League> opt = leagueRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        League league = opt.get();
        List<Fixture> fixtures = fixtureRepository.findByLeagueIdOrderByRoundAscMatchNumberAsc(id);

        // Collect all completed match results
        List<MatchResult> completedResults = new ArrayList<>();
        for (Fixture f : fixtures) {
            if (!"COMPLETED".equals(f.getStatus())) continue;
            matchResultRepository.findByFixtureId(f.getId()).ifPresent(completedResults::add);
        }

        if (completedResults.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("batting", List.of());
            empty.put("bowling", List.of());
            empty.put("fielding", List.of());
            return ResponseEntity.ok(empty);
        }

        // Track batting stats per player
        Map<UUID, BatAgg> batMap = new LinkedHashMap<>();
        // Track bowling stats per player
        Map<UUID, BowlAgg> bowlMap = new LinkedHashMap<>();
        // Track fielding stats per player
        Map<UUID, FieldAgg> fieldMap = new LinkedHashMap<>();
        // Track matches per player per team
        Map<UUID, Set<UUID>> playerMatches = new LinkedHashMap<>();
        // Track dot balls per batsman across innings
        Map<UUID, Integer> batsmanDots = new LinkedHashMap<>();

        for (MatchResult mr : completedResults) {
            UUID matchId = mr.getFixture().getId();
            for (Innings inn : mr.getInningsList()) {
                // Count dot balls per batsman from ball events
                List<BallEvent> events = ballEventRepository
                        .findByInningsIdOrderByOverNumberAscBallNumberAsc(inn.getId());
                for (BallEvent be : events) {
                    if (be.getRuns() == 0 && !be.getIsWide() && !be.getIsNoBall()
                            && !be.getIsBye() && !be.getIsLegBye()) {
                        batsmanDots.merge(be.getBatsman().getId(), 1, Integer::sum);
                    }
                }

                // Batting
                for (BattingScorecard bc : inn.getBattingCards()) {
                    UUID pid = bc.getPlayer().getId();
                    playerMatches.computeIfAbsent(pid, k -> new HashSet<>()).add(matchId);
                    BatAgg agg = batMap.computeIfAbsent(pid, k -> new BatAgg(bc.getPlayer()));
                    agg.innings++;
                    agg.runs += bc.getRunsScored();
                    agg.balls += bc.getBallsFaced();
                    agg.fours += bc.getFours();
                    agg.sixes += bc.getSixes();
                    if (bc.getDismissalType() == null) agg.notOuts++;
                    if (bc.getRunsScored() >= 100) agg.hundreds++;
                    if (bc.getRunsScored() >= 50 && bc.getRunsScored() < 100) agg.fifties++;
                    if (bc.getRunsScored() == 0 && bc.getDismissalType() != null) agg.ducks++;
                    if (bc.getRunsScored() > agg.highScore ||
                        (bc.getRunsScored() == agg.highScore && bc.getDismissalType() == null && !agg.highScoreNotOut)) {
                        agg.highScore = bc.getRunsScored();
                        agg.highScoreNotOut = bc.getDismissalType() == null;
                    }
                }

                // Bowling
                for (BowlingScorecard bwc : inn.getBowlingCards()) {
                    UUID pid = bwc.getPlayer().getId();
                    playerMatches.computeIfAbsent(pid, k -> new HashSet<>()).add(matchId);
                    BowlAgg agg = bowlMap.computeIfAbsent(pid, k -> new BowlAgg(bwc.getPlayer()));
                    agg.innings++;
                    agg.overs += bwc.getOvers();
                    agg.maidens += bwc.getMaidens();
                    agg.runs += bwc.getRunsConceded();
                    agg.wickets += bwc.getWickets();
                    if (bwc.getWickets() >= 3) agg.threeWI++;
                    if (bwc.getWickets() >= 5) agg.fiveWI++;
                    // Track best bowling (most wickets, least runs)
                    if (bwc.getWickets() > agg.bestWickets ||
                        (bwc.getWickets() == agg.bestWickets && bwc.getRunsConceded() < agg.bestRuns)) {
                        agg.bestWickets = bwc.getWickets();
                        agg.bestRuns = bwc.getRunsConceded();
                    }
                }

                // Fielding from batting scorecards (fielder + dismissalType)
                for (BattingScorecard bc : inn.getBattingCards()) {
                    if (bc.getDismissalType() == null) continue;
                    String dismissal = bc.getDismissalType().toUpperCase();

                    if (bc.getFielder() != null) {
                        UUID fid = bc.getFielder().getId();
                        playerMatches.computeIfAbsent(fid, k -> new HashSet<>()).add(matchId);
                        FieldAgg fagg = fieldMap.computeIfAbsent(fid, k -> new FieldAgg(bc.getFielder()));

                        if ("CAUGHT".equals(dismissal) || "C&B".equals(dismissal)) {
                            fagg.fielderCatches++;
                        } else if ("CAUGHT_BEHIND".equals(dismissal)) {
                            fagg.keeperCatches++;
                        } else if ("STUMPED".equals(dismissal)) {
                            fagg.keeperStumpings++;
                        } else if ("RUN_OUT".equals(dismissal) || "RUNOUT".equals(dismissal)) {
                            fagg.runouts++;
                        }
                    }
                }
            }
        }

        // Build batting response sorted by runs DESC
        List<Map<String, Object>> batting = new ArrayList<>();
        for (BatAgg agg : batMap.values()) {
            int matches = playerMatches.getOrDefault(agg.player.getId(), Set.of()).size();
            int dismissals = agg.innings - agg.notOuts;
            double avg = dismissals > 0 ? (double) agg.runs / dismissals : (agg.runs > 0 ? agg.runs : 0);
            double sr = agg.balls > 0 ? (double) agg.runs / agg.balls * 100.0 : 0;
            int dots = batsmanDots.getOrDefault(agg.player.getId(), 0);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("playerId", agg.player.getId());
            row.put("playerName", agg.player.getFirstName() + " " + agg.player.getLastName());
            row.put("teamName", agg.player.getTeam().getTeamName());
            row.put("teamId", agg.player.getTeam().getId());
            row.put("batHand", agg.player.getBatHand());
            row.put("matches", matches);
            row.put("innings", agg.innings);
            row.put("notOuts", agg.notOuts);
            row.put("runs", agg.runs);
            row.put("balls", agg.balls);
            row.put("highScore", agg.highScore + (agg.highScoreNotOut ? "*" : ""));
            row.put("strikeRate", Math.round(sr * 100.0) / 100.0);
            row.put("average", Math.round(avg * 100.0) / 100.0);
            row.put("hundreds", agg.hundreds);
            row.put("fifties", agg.fifties);
            row.put("fours", agg.fours);
            row.put("sixes", agg.sixes);
            row.put("ducks", agg.ducks);
            row.put("dots", dots);
            batting.add(row);
        }
        batting.sort((a, b) -> Integer.compare((int) b.get("runs"), (int) a.get("runs")));

        // Build bowling response sorted by wickets DESC
        List<Map<String, Object>> bowling = new ArrayList<>();
        for (BowlAgg agg : bowlMap.values()) {
            if (agg.innings == 0) continue;
            int matches = playerMatches.getOrDefault(agg.player.getId(), Set.of()).size();
            int totalBalls = oversToTotalBalls(agg.overs);
            double economy = totalBalls > 0 ? agg.runs / (totalBalls / 6.0) : 0;
            double avg = agg.wickets > 0 ? (double) agg.runs / agg.wickets : 0;
            double sr = agg.wickets > 0 ? (double) totalBalls / agg.wickets : 0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("playerId", agg.player.getId());
            row.put("playerName", agg.player.getFirstName() + " " + agg.player.getLastName());
            row.put("teamName", agg.player.getTeam().getTeamName());
            row.put("teamId", agg.player.getTeam().getId());
            row.put("bowlType", formatBowlType(agg.player));
            row.put("matches", matches);
            row.put("innings", agg.innings);
            row.put("balls", totalBalls);
            row.put("maidens", agg.maidens);
            row.put("runs", agg.runs);
            row.put("wickets", agg.wickets);
            row.put("bestBowling", agg.bestWickets + "/" + agg.bestRuns);
            row.put("average", agg.wickets > 0 ? Math.round(avg * 100.0) / 100.0 : 0);
            row.put("strikeRate", agg.wickets > 0 ? Math.round(sr * 100.0) / 100.0 : 0);
            row.put("economy", Math.round(economy * 100.0) / 100.0);
            row.put("threeWI", agg.threeWI);
            row.put("fiveWI", agg.fiveWI);
            bowling.add(row);
        }
        bowling.sort((a, b) -> Integer.compare((int) b.get("wickets"), (int) a.get("wickets")));

        // Build fielding response sorted by total dismissals DESC
        List<Map<String, Object>> fielding = new ArrayList<>();
        for (FieldAgg agg : fieldMap.values()) {
            int matches = playerMatches.getOrDefault(agg.player.getId(), Set.of()).size();
            int total = agg.fielderCatches + agg.keeperCatches + agg.keeperStumpings + agg.runouts;
            if (total == 0) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("playerId", agg.player.getId());
            row.put("playerName", agg.player.getFirstName() + " " + agg.player.getLastName());
            row.put("teamName", agg.player.getTeam().getTeamName());
            row.put("teamId", agg.player.getTeam().getId());
            row.put("matches", matches);
            row.put("fielderCatches", agg.fielderCatches);
            row.put("keeperCatches", agg.keeperCatches);
            row.put("stumpings", agg.keeperStumpings);
            row.put("runouts", agg.runouts);
            row.put("total", total);
            fielding.add(row);
        }
        fielding.sort((a, b) -> Integer.compare((int) b.get("total"), (int) a.get("total")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batting", batting);
        result.put("bowling", bowling);
        result.put("fielding", fielding);
        return ResponseEntity.ok(result);
    }

    // ── Aggregation helpers ──

    private static class BatAgg {
        Player player;
        int innings, notOuts, runs, balls, fours, sixes, hundreds, fifties, ducks, dots;
        int highScore = -1;
        boolean highScoreNotOut;
        BatAgg(Player p) { this.player = p; }
    }

    private static class BowlAgg {
        Player player;
        int innings, maidens, runs, wickets, threeWI, fiveWI;
        double overs;
        int bestWickets = 0, bestRuns = Integer.MAX_VALUE;
        BowlAgg(Player p) { this.player = p; }
    }

    private static class FieldAgg {
        Player player;
        int fielderCatches, keeperCatches, keeperStumpings, runouts;
        FieldAgg(Player p) { this.player = p; }
    }

    private double oversToDecimal(double overs) {
        int fullOvers = (int) overs;
        int extraBalls = (int) Math.round((overs - fullOvers) * 10);
        return fullOvers + extraBalls / 6.0;
    }

    private double getMaxOvers(String format) {
        if (format == null) return 20;
        return switch (format) {
            case "ODI" -> 50;
            case "FC" -> 90; // per innings
            default -> 20;  // T20
        };
    }

    private int oversToTotalBalls(double overs) {
        int fullOvers = (int) overs;
        int extraBalls = (int) Math.round((overs - fullOvers) * 10);
        return fullOvers * 6 + extraBalls;
    }

    private String formatBowlType(Player p) {
        if (p.getBowlHand() == null || p.getBowlType() == null) return "-";
        String hand = "RH".equals(p.getBowlHand()) ? "R" : "L";
        return hand + p.getBowlType();
    }
}
