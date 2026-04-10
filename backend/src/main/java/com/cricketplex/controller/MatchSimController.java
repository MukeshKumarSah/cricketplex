package com.cricketplex.controller;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.MatchEngine;
import com.cricketplex.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchSimController {

    private static final int BALL_INTERVAL_SECONDS = 5;
    private static final int INNINGS_BREAK_SECONDS = 300;
    private static final int SESSION_BREAK_SECONDS = 180;  // break between sessions in FC
    private static final int SESSION_OVERS = 50;           // overs per session in FC

    private final MatchEngine matchEngine;
    private final MatchResultRepository matchResultRepository;
    private final InningsRepository inningsRepository;
    private final BallEventRepository ballEventRepository;
    private final UserRepository userRepository;
    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;
    private final MatchLineupRepository matchLineupRepository;
    private final WeatherService weatherService;

    /**
     * Compute session break positions for FC matches.
     * Returns a list (per innings) of ball-event indices where a session break occurs.
     * Tracks cumulative completed overs across all innings. Breaks only at over boundaries.
     */
    private List<List<Integer>> computeSessionBreakPositions(MatchResult result) {
        List<List<Integer>> allPositions = new ArrayList<>();
        int cumulativeOvers = 0;

        for (Innings inn : result.getInningsList()) {
            List<Integer> breaks = new ArrayList<>();
            List<BallEvent> events = ballEventRepository.findByInningsIdOrderByOverNumberAscBallNumberAsc(inn.getId());

            int lastSeenOver = 0;
            for (int j = 0; j < events.size(); j++) {
                int curOver = events.get(j).getOverNumber(); // 1-based
                if (curOver != lastSeenOver) {
                    // A new over started — the previous over just completed
                    if (lastSeenOver > 0) {
                        cumulativeOvers++;
                        if (cumulativeOvers % SESSION_OVERS == 0) {
                            breaks.add(j); // break before this ball (first ball of new over)
                        }
                    }
                    lastSeenOver = curOver;
                }
            }
            // Count the final over if it was a complete over (totalOvers is whole number)
            if (inn.getTotalOvers() != null && inn.getTotalOvers() > 0
                    && Math.abs(inn.getTotalOvers() - Math.floor(inn.getTotalOvers())) < 0.01) {
                cumulativeOvers++;
                // No break needed here — the innings is ending (innings break will follow)
            }

            allPositions.add(breaks);
        }
        return allPositions;
    }

    /**
     * Simulate a match for a given fixture.
     */
    @PostMapping("/simulate/{fixtureId}")
    public ResponseEntity<?> simulateMatch(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID fixtureId) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        try {
            MatchResult result = matchEngine.simulateMatch(fixtureId);
            return ResponseEntity.ok(buildResultResponse(result));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Set or update FC match strategy (declaration targets, follow-on choice).
     * Can be called before Day 1 or between Day 1 and Day 2.
     */
    @PostMapping("/fc-strategy/{fixtureId}")
    public ResponseEntity<?> setFCStrategy(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID fixtureId,
            @RequestBody Map<String, Object> request) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Team myTeam = teamRepository.findByOwner(user).orElse(null);
        if (myTeam == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No team found"));
        }

        Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Fixture not found"));
        }

        // Validate ownership
        boolean isHome = fixture.getHomeTeam().getId().equals(myTeam.getId());
        boolean isAway = fixture.getAwayTeam().getId().equals(myTeam.getId());
        if (!isHome && !isAway) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Not your match"));
        }

        // Allow updates when SCHEDULED or FC_DAY1_COMPLETE
        String status = fixture.getStatus();
        if (!"SCHEDULED".equals(status) && !"FC_DAY1_COMPLETE".equals(status)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Cannot update strategy now"));
        }

        if (request.containsKey("declareInn1")) {
            Object val = request.get("declareInn1");
            fixture.setFcDeclareInn1(val != null ? ((Number) val).intValue() : null);
        }
        if (request.containsKey("declareInn2Lead")) {
            Object val = request.get("declareInn2Lead");
            fixture.setFcDeclareInn2Lead(val != null ? ((Number) val).intValue() : null);
        }
        if (request.containsKey("followOn")) {
            Object val = request.get("followOn");
            fixture.setFcFollowOn(val != null ? (Boolean) val : null);
        }
        if (request.containsKey("declareInn3Lead")) {
            Object val = request.get("declareInn3Lead");
            fixture.setFcDeclareInn3Lead(val != null ? ((Number) val).intValue() : null);
        }

        fixtureRepository.save(fixture);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("declareInn1", fixture.getFcDeclareInn1());
        resp.put("declareInn2Lead", fixture.getFcDeclareInn2Lead());
        resp.put("followOn", fixture.getFcFollowOn());
        resp.put("declareInn3Lead", fixture.getFcDeclareInn3Lead());
        return ResponseEntity.ok(resp);
    }

    /**
     * Get FC match state (for between-days strategy updates).
     */
    @GetMapping("/fc-state/{fixtureId}")
    public ResponseEntity<?> getFCState(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID fixtureId) {

        Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null) {
            return ResponseEntity.ok(Map.of("found", false));
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("found", true);
        resp.put("fixtureId", fixtureId);
        resp.put("status", fixture.getStatus());
        resp.put("fcDay", fixture.getFcDay());
        resp.put("declareInn1", fixture.getFcDeclareInn1());
        resp.put("declareInn2Lead", fixture.getFcDeclareInn2Lead());
        resp.put("followOn", fixture.getFcFollowOn());
        resp.put("declareInn3Lead", fixture.getFcDeclareInn3Lead());

        // Include Day 1 innings summary if available
        Optional<MatchResult> opt = matchResultRepository.findByFixtureId(fixtureId);
        if (opt.isPresent()) {
            MatchResult mr = opt.get();
            List<Map<String, Object>> innSummaries = new ArrayList<>();
            for (Innings inn : mr.getInningsList()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("inningsNumber", inn.getInningsNumber());
                s.put("battingTeamId", inn.getBattingTeam().getId());
                s.put("battingTeamName", inn.getBattingTeam().getTeamName());
                s.put("totalRuns", inn.getTotalRuns());
                s.put("totalWickets", inn.getTotalWickets());
                s.put("totalOvers", inn.getTotalOvers());
                s.put("allOut", inn.getAllOut());
                s.put("declared", inn.getDeclared());
                s.put("interrupted", inn.getResumeState() != null);
                innSummaries.add(s);
            }
            resp.put("innings", innSummaries);
        }

        return ResponseEntity.ok(resp);
    }

    /**
     * Get match result for a fixture.
     */
    @GetMapping("/result/{fixtureId}")
    public ResponseEntity<?> getMatchResult(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID fixtureId) {

        Optional<MatchResult> opt = matchResultRepository.findByFixtureId(fixtureId);
        if (opt.isEmpty()) {
            return ResponseEntity.ok(Map.of("found", false));
        }

        MatchResult mr = opt.get();
        autoCompleteIfExpired(mr);
        return ResponseEntity.ok(buildResultResponse(mr));
    }

    // ─── Response builder ───────────────────────────────────────

    private Map<String, Object> buildResultResponse(MatchResult result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("found", true);
        resp.put("id", result.getId());
        resp.put("fixtureId", result.getFixture().getId());

        // Fixture info
        Fixture fixture = result.getFixture();
        resp.put("homeTeamName", fixture.getHomeTeam().getTeamName());
        resp.put("homeTeamId", fixture.getHomeTeam().getId());
        resp.put("homeTeamPicUrl", fixture.getHomeTeam().getTeamProfilePicUrl());
        resp.put("awayTeamName", fixture.getAwayTeam().getTeamName());
        resp.put("awayTeamId", fixture.getAwayTeam().getId());
        resp.put("awayTeamPicUrl", fixture.getAwayTeam().getTeamProfilePicUrl());
        String format = fixture.getLeague() != null ? fixture.getLeague().getFormat() : fixture.getFormat();
        resp.put("format", format);
        resp.put("matchDate", fixture.getMatchDate() != null ? fixture.getMatchDate().toString() : null);
        resp.put("matchStartTimeUtc", fixture.getLeague() != null ? fixture.getLeague().getMatchStartTime() : null);
        resp.put("groundName", fixture.getHomeTeam().getGroundName());
        resp.put("pitchType", fixture.getPitchType());
        resp.put("matchType", fixture.getMatchType());
        resp.put("fixtureStatus", fixture.getStatus());
        resp.put("fcDay", fixture.getFcDay());
        resp.put("attendance", result.getAttendance());
        // Parse attendance breakdown: "standAtt,standCap,ecoAtt,ecoCap,stdAtt,stdCap,premAtt,premCap"
        if (result.getAttendanceBreakdown() != null) {
            String[] parts = result.getAttendanceBreakdown().split(",");
            if (parts.length == 8) {
                Map<String, Object> bd = new LinkedHashMap<>();
                bd.put("standingAtt", Integer.parseInt(parts[0]));
                bd.put("standingCap", Integer.parseInt(parts[1]));
                bd.put("economyAtt", Integer.parseInt(parts[2]));
                bd.put("economyCap", Integer.parseInt(parts[3]));
                bd.put("standardAtt", Integer.parseInt(parts[4]));
                bd.put("standardCap", Integer.parseInt(parts[5]));
                bd.put("premiumAtt", Integer.parseInt(parts[6]));
                bd.put("premiumCap", Integer.parseInt(parts[7]));
                resp.put("attendanceBreakdown", bd);
            }
        }
        resp.put("createdAt", result.getCreatedAt() != null
                ? result.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : null);

        // Ball counts per innings for time-based calculation
        List<Integer> ballCounts = new ArrayList<>();
        for (Innings inn : result.getInningsList()) {
            long count = ballEventRepository.countByInningsId(inn.getId());
            ballCounts.add((int) count);
        }
        resp.put("ballCounts", ballCounts);
        resp.put("ballIntervalSeconds", BALL_INTERVAL_SECONDS);
        resp.put("inningsBreakSeconds", INNINGS_BREAK_SECONDS);
        if ("FC".equalsIgnoreCase(format)) {
            resp.put("sessionBreakSeconds", SESSION_BREAK_SECONDS);
            resp.put("sessionBreakPositions", computeSessionBreakPositions(result));
        }

        // Toss
        resp.put("tossWinner", result.getTossWinner().getTeamName());
        resp.put("tossWinnerId", result.getTossWinner().getId());
        resp.put("tossDecision", result.getTossDecision());

        // Result
        resp.put("resultType", result.getResultType());
        resp.put("resultMargin", result.getResultMargin());
        if (result.getWinner() != null) {
            resp.put("winner", result.getWinner().getTeamName());
            resp.put("winnerId", result.getWinner().getId());
        }

        // Man of the match
        if (result.getManOfMatch() != null) {
            resp.put("manOfMatch", result.getManOfMatch().getFirstName() + " " + result.getManOfMatch().getLastName());
            resp.put("manOfMatchId", result.getManOfMatch().getId());
        }

        // Summary line
        resp.put("summary", buildSummaryLine(result));

        // Innings
        List<Map<String, Object>> inningsList = new ArrayList<>();
        for (Innings inn : result.getInningsList()) {
            inningsList.add(buildInningsResponse(inn));
        }
        resp.put("innings", inningsList);

        // Team Strength Breakdown (using ME formulas: pitch + weather + exp + conf + fitness)
        try {
            Fixture fx = result.getFixture();
            String pitchType = fx.getPitchType();
            Map<String, Object> weather = weatherService.getWeather(
                    fx.getHomeTeam().getCountry(), fx.getMatchDate());
            String condition = (String) weather.get("condition");
            int temperature = (int) weather.get("temperature");

            // Find first innings per team for batting cards (has all 11), and bowling cards
            List<Innings> innings = result.getInningsList();
            Innings homeBatInn = null, awayBatInn = null;
            Innings homeBowlInn = null, awayBowlInn = null;
            UUID homeId = fx.getHomeTeam().getId();
            UUID awayId = fx.getAwayTeam().getId();
            for (Innings inn : innings) {
                if (homeBatInn == null && inn.getBattingTeam().getId().equals(homeId)) homeBatInn = inn;
                if (awayBatInn == null && inn.getBattingTeam().getId().equals(awayId)) awayBatInn = inn;
                if (homeBowlInn == null && inn.getBowlingTeam().getId().equals(homeId)) homeBowlInn = inn;
                if (awayBowlInn == null && inn.getBowlingTeam().getId().equals(awayId)) awayBowlInn = inn;
            }

            Map<String, Object> strengths = new LinkedHashMap<>();
            if (homeBatInn != null && homeBowlInn != null) {
                strengths.put("home", matchEngine.computeTeamStrengthBreakdown(
                        new ArrayList<>(homeBatInn.getBattingCards()),
                        new ArrayList<>(homeBowlInn.getBowlingCards()),
                        pitchType, condition, temperature));
            }
            if (awayBatInn != null && awayBowlInn != null) {
                strengths.put("away", matchEngine.computeTeamStrengthBreakdown(
                        new ArrayList<>(awayBatInn.getBattingCards()),
                        new ArrayList<>(awayBowlInn.getBowlingCards()),
                        pitchType, condition, temperature));
            }
            resp.put("teamStrengths", strengths);
        } catch (Exception ignored) {
            // If weather derivation fails, skip strengths
        }

        return resp;
    }

    private Map<String, Object> buildInningsResponse(Innings inn) {
        Map<String, Object> innMap = new LinkedHashMap<>();
        innMap.put("inningsNumber", inn.getInningsNumber());
        innMap.put("battingTeam", inn.getBattingTeam().getTeamName());
        innMap.put("battingTeamId", inn.getBattingTeam().getId());
        innMap.put("bowlingTeam", inn.getBowlingTeam().getTeamName());
        innMap.put("totalRuns", inn.getTotalRuns());
        innMap.put("totalWickets", inn.getTotalWickets());
        innMap.put("totalOvers", inn.getTotalOvers());
        innMap.put("extras", inn.getExtras());
        innMap.put("allOut", inn.getAllOut());
        innMap.put("declared", inn.getDeclared());
        innMap.put("scoreDisplay", inn.getTotalRuns() + "/" + inn.getTotalWickets()
                + " (" + formatOvers(inn.getTotalOvers()) + " ov)");

        // Batting scorecard
        List<Map<String, Object>> batCards = new ArrayList<>();
        List<BattingScorecard> sortedBat = new ArrayList<>(inn.getBattingCards());
        sortedBat.sort(Comparator.comparingInt(BattingScorecard::getBattingPosition));
        for (BattingScorecard bc : sortedBat) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("playerId", bc.getPlayer().getId());
            card.put("playerName", bc.getPlayer().getFirstName() + " " + bc.getPlayer().getLastName());
            card.put("battingPosition", bc.getBattingPosition());
            card.put("runs", bc.getRunsScored());
            card.put("balls", bc.getBallsFaced());
            card.put("fours", bc.getFours());
            card.put("sixes", bc.getSixes());
            card.put("strikeRate", bc.getStrikeRate());
            card.put("dismissal", bc.getDismissalType());
            if (bc.getBowler() != null) {
                card.put("bowler", bc.getBowler().getFirstName() + " " + bc.getBowler().getLastName());
            }
            if (bc.getFielder() != null) {
                card.put("fielder", bc.getFielder().getFirstName() + " " + bc.getFielder().getLastName());
            }
            card.put("notOut", bc.getDismissalType() == null);
            card.put("batRating", bc.getPlayer().getBatRating());
            card.put("bowlRating", bc.getPlayer().getBowlRating());
            card.put("fldRating", bc.getPlayer().getFldRating());
            card.put("keeperRating", bc.getPlayer().getKeeperRating());
            card.put("bowlType", bc.getPlayer().getBowlType());
            card.put("role", bc.getPlayer().getRole());
            batCards.add(card);
        }
        innMap.put("battingCard", batCards);

        // Bowling scorecard
        List<Map<String, Object>> bowlCards = new ArrayList<>();
        List<BowlingScorecard> sortedBowl = new ArrayList<>(inn.getBowlingCards());
        sortedBowl.sort((a, b) -> Double.compare(b.getWickets(), a.getWickets()));
        for (BowlingScorecard bc : sortedBowl) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("playerId", bc.getPlayer().getId());
            card.put("playerName", bc.getPlayer().getFirstName() + " " + bc.getPlayer().getLastName());
            card.put("overs", formatOvers(bc.getOvers()));
            card.put("maidens", bc.getMaidens());
            card.put("runs", bc.getRunsConceded());
            card.put("wickets", bc.getWickets());
            card.put("economy", bc.getEconomy());
            card.put("dotBalls", bc.getDotBalls());
            card.put("wides", bc.getWides());
            card.put("noBalls", bc.getNoBalls());
            card.put("bowlType", bc.getPlayer().getBowlType());
            card.put("bowlRating", bc.getPlayer().getBowlRating());
            bowlCards.add(card);
        }
        innMap.put("bowlingCard", bowlCards);

        return innMap;
    }

    private String buildSummaryLine(MatchResult result) {
        if ("TIE".equals(result.getResultType())) {
            return "Match tied!";
        }
        if ("DRAW".equals(result.getResultType())) {
            return "Match drawn";
        }
        if (result.getWinner() == null) {
            return "Match drawn";
        }
        String winner = result.getWinner().getTeamName();
        if ("RUNS".equals(result.getResultType())) {
            return winner + " won by " + result.getResultMargin() + " runs";
        }
        if ("WICKETS".equals(result.getResultType())) {
            return winner + " won by " + result.getResultMargin() + " wickets";
        }
        if ("INNINGS".equals(result.getResultType())) {
            return winner + " won by an innings and " + result.getResultMargin() + " runs";
        }
        return winner + " won";
    }

    private String formatOvers(Double overs) {
        if (overs == null) return "0";
        int full = overs.intValue();
        int balls = (int) Math.round((overs - full) * 10);
        if (balls >= 6) {
            full++;
            balls = 0;
        }
        return balls > 0 ? full + "." + balls : String.valueOf(full);
    }

    /**
     * Auto-complete fixture if enough time has elapsed since match started.
     */
    private void autoCompleteIfExpired(MatchResult result) {
        Fixture fixture = result.getFixture();
        if (!"IN_PROGRESS".equals(fixture.getStatus())) return;
        if (result.getCreatedAt() == null) return;

        long totalBalls = 0;
        for (Innings inn : result.getInningsList()) {
            totalBalls += ballEventRepository.countByInningsId(inn.getId());
        }
        int inningsCount = result.getInningsList().size();
        int breakCount = inningsCount > 1 ? inningsCount - 1 : 0;

        // For FC: count session breaks based on cumulative completed overs at over boundaries
        String fmt = fixture.getLeague() != null ? fixture.getLeague().getFormat() : fixture.getFormat();
        int sessionBreaks = 0;
        if ("FC".equalsIgnoreCase(fmt)) {
            List<List<Integer>> positions = computeSessionBreakPositions(result);
            for (List<Integer> innBreaks : positions) {
                sessionBreaks += innBreaks.size();
            }
        }

        long totalSeconds = totalBalls * BALL_INTERVAL_SECONDS
                + (long) breakCount * INNINGS_BREAK_SECONDS
                + (long) sessionBreaks * SESSION_BREAK_SECONDS;
        long elapsed = ChronoUnit.SECONDS.between(result.getCreatedAt(), LocalDateTime.now());

        if (elapsed >= totalSeconds) {
            fixture.setStatus("COMPLETED");
            fixtureRepository.save(fixture);
        }
    }

    /**
     * Get ball-by-ball commentary for a match.
     */
    @GetMapping("/commentary/{fixtureId}")
    public ResponseEntity<?> getCommentary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID fixtureId) {

        Optional<MatchResult> opt = matchResultRepository.findByFixtureId(fixtureId);
        if (opt.isEmpty()) {
            return ResponseEntity.ok(Map.of("found", false));
        }

        MatchResult result = opt.get();
        autoCompleteIfExpired(result);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("found", true);
        resp.put("summary", buildSummaryLine(result));
        resp.put("createdAt", result.getCreatedAt() != null
                ? result.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : null);
        resp.put("fixtureStatus", result.getFixture().getStatus());

        // Ball counts per innings
        List<Integer> ballCounts = new ArrayList<>();
        for (Innings inn : result.getInningsList()) {
            long count = ballEventRepository.countByInningsId(inn.getId());
            ballCounts.add((int) count);
        }
        resp.put("ballCounts", ballCounts);
        resp.put("ballIntervalSeconds", BALL_INTERVAL_SECONDS);
        resp.put("inningsBreakSeconds", INNINGS_BREAK_SECONDS);

        String commFormat = result.getFixture().getLeague() != null
                ? result.getFixture().getLeague().getFormat()
                : result.getFixture().getFormat();
        if ("FC".equalsIgnoreCase(commFormat)) {
            resp.put("sessionBreakSeconds", SESSION_BREAK_SECONDS);
            resp.put("sessionBreakPositions", computeSessionBreakPositions(result));
        }

        resp.put("tossWinner", result.getTossWinner().getTeamName());
        resp.put("tossDecision", result.getTossDecision());

        List<Map<String, Object>> inningsList = new ArrayList<>();
        for (Innings inn : result.getInningsList()) {
            Map<String, Object> innMap = new LinkedHashMap<>();
            innMap.put("inningsNumber", inn.getInningsNumber());
            innMap.put("battingTeam", inn.getBattingTeam().getTeamName());
            innMap.put("bowlingTeam", inn.getBowlingTeam().getTeamName());
            innMap.put("scoreDisplay", inn.getTotalRuns() + "/" + inn.getTotalWickets()
                    + " (" + formatOvers(inn.getTotalOvers()) + " ov)");
            innMap.put("declared", inn.getDeclared());
            innMap.put("allOut", inn.getAllOut());

            List<BallEvent> events = ballEventRepository.findByInningsIdOrderByOverNumberAscBallNumberAsc(inn.getId());
            List<Map<String, Object>> balls = new ArrayList<>();
            for (BallEvent be : events) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("over", be.getOverNumber() - 1);
                b.put("ball", be.getBallNumber());
                b.put("overBall", (be.getOverNumber() - 1) + "." + be.getBallNumber());
                b.put("batsman", be.getBatsman().getFirstName() + " " + be.getBatsman().getLastName());
                b.put("batsmanId", be.getBatsman().getId());
                b.put("bowler", be.getBowler().getFirstName() + " " + be.getBowler().getLastName());
                b.put("bowlerId", be.getBowler().getId());
                b.put("runs", be.getRuns());
                b.put("isWicket", be.getIsWicket());
                b.put("isBoundary", be.getIsBoundary());
                b.put("isSix", be.getIsSix());
                b.put("isWide", be.getIsWide());
                b.put("isNoBall", be.getIsNoBall());
                b.put("isBye", be.getIsBye());
                b.put("isLegBye", be.getIsLegBye());
                b.put("dismissalType", be.getDismissalType());
                if (be.getFielder() != null) {
                    b.put("fielder", be.getFielder().getFirstName() + " " + be.getFielder().getLastName());
                }
                b.put("commentary", be.getCommentary());
                balls.add(b);
            }
            innMap.put("ballEvents", balls);
            inningsList.add(innMap);
        }
        resp.put("innings", inningsList);
        return ResponseEntity.ok(resp);
    }

    /**
     * Get fixture preview — teams, pitch, weather, rivalry, lineup status.
     */
    @GetMapping("/preview/{fixtureId}")
    public ResponseEntity<?> getFixturePreview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID fixtureId) {

        Fixture f = fixtureRepository.findById(fixtureId)
                .orElse(null);
        if (f == null) return ResponseEntity.notFound().build();

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Optional<Team> userTeam = teamRepository.findByOwner(user);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("fixtureId", f.getId());
        resp.put("status", f.getStatus());
        resp.put("matchDate", f.getMatchDate() != null ? f.getMatchDate().toString() : null);
        resp.put("pitchType", f.getPitchType());
        resp.put("round", f.getRound());
        resp.put("matchNumber", f.getMatchNumber());

        boolean isFriendly = f.getLeague() == null;
        resp.put("matchType", isFriendly ? "FRIENDLY" : "LEAGUE");
        resp.put("format", isFriendly ? f.getFormat() : f.getLeague().getFormat());
        if (!isFriendly) {
            resp.put("leagueLabel", f.getLeague().getDivision() + "." + f.getLeague().getLeagueNumber());
            resp.put("leagueId", f.getLeague().getId());
            resp.put("matchStartTimeUtc", f.getLeague().getMatchStartTime());
        }

        // Home team info
        Team home = f.getHomeTeam();
        Map<String, Object> homeInfo = new LinkedHashMap<>();
        homeInfo.put("id", home.getId());
        homeInfo.put("teamName", home.getTeamName());
        homeInfo.put("teamProfilePicUrl", home.getTeamProfilePicUrl());
        homeInfo.put("country", home.getCountry());
        homeInfo.put("isBot", home.getIsBot());
        homeInfo.put("groundName", home.getGroundName());
        resp.put("homeTeam", homeInfo);

        // Away team info
        Team away = f.getAwayTeam();
        Map<String, Object> awayInfo = new LinkedHashMap<>();
        awayInfo.put("id", away.getId());
        awayInfo.put("teamName", away.getTeamName());
        awayInfo.put("teamProfilePicUrl", away.getTeamProfilePicUrl());
        awayInfo.put("country", away.getCountry());
        awayInfo.put("isBot", away.getIsBot());
        resp.put("awayTeam", awayInfo);

        // Weather
        if (f.getMatchDate() != null) {
            resp.put("weather", weatherService.getMatchWeather(home.getCountry(), f.getMatchDate(), LocalDate.now()));
        }

        // Rivalry (head-to-head)
        List<MatchResult> rivalryResults = matchResultRepository.findBetweenTeams(home.getId(), away.getId());
        int homeWins = 0, awayWins = 0, ties = 0;
        List<Map<String, Object>> recentMatches = new ArrayList<>();
        for (MatchResult mr : rivalryResults) {
            if (mr.getWinner() != null) {
                if (mr.getWinner().getId().equals(home.getId())) homeWins++;
                else awayWins++;
            } else if ("TIE".equals(mr.getResultType())) {
                ties++;
            }
            if (recentMatches.size() < 5) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("date", mr.getFixture().getMatchDate() != null ? mr.getFixture().getMatchDate().toString() : null);
                rm.put("format", mr.getFixture().getLeague() != null ? mr.getFixture().getLeague().getFormat() : mr.getFixture().getFormat());
                rm.put("summary", buildSummaryLine(mr));
                rm.put("winnerId", mr.getWinner() != null ? mr.getWinner().getId() : null);
                recentMatches.add(rm);
            }
        }
        Map<String, Object> rivalry = new LinkedHashMap<>();
        rivalry.put("totalMatches", rivalryResults.size());
        rivalry.put("homeWins", homeWins);
        rivalry.put("awayWins", awayWins);
        rivalry.put("ties", ties);
        rivalry.put("recentMatches", recentMatches);
        resp.put("rivalry", rivalry);

        // User lineup context
        boolean isUserHome = userTeam.isPresent() && userTeam.get().getId().equals(home.getId());
        boolean isUserAway = userTeam.isPresent() && userTeam.get().getId().equals(away.getId());
        resp.put("isUserInvolved", isUserHome || isUserAway);
        resp.put("isUserHome", isUserHome);

        if (userTeam.isPresent() && (isUserHome || isUserAway)) {
            boolean lineupSet = matchLineupRepository.findFixtureIdsByTeamId(userTeam.get().getId())
                    .contains(fixtureId);
            resp.put("lineupSet", lineupSet);
        }

        return ResponseEntity.ok(resp);
    }

    /**
     * Get head-to-head rivalry between two teams.
     */
    @GetMapping("/rivalry/{team1Id}/{team2Id}")
    public ResponseEntity<?> getRivalry(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID team1Id,
            @PathVariable UUID team2Id) {

        List<MatchResult> results = matchResultRepository.findBetweenTeams(team1Id, team2Id);
        Map<String, Object> resp = new LinkedHashMap<>();
        int team1Wins = 0, team2Wins = 0, draws = 0, ties = 0;

        List<Map<String, Object>> matches = new ArrayList<>();
        for (MatchResult mr : results) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fixtureId", mr.getFixture().getId());
            m.put("date", mr.getFixture().getMatchDate() != null ? mr.getFixture().getMatchDate().toString() : null);
            m.put("format", mr.getFixture().getLeague() != null ? mr.getFixture().getLeague().getFormat() : mr.getFixture().getFormat());
            m.put("homeTeam", mr.getFixture().getHomeTeam().getTeamName());
            m.put("awayTeam", mr.getFixture().getAwayTeam().getTeamName());
            m.put("summary", buildSummaryLine(mr));
            m.put("winnerId", mr.getWinner() != null ? mr.getWinner().getId() : null);

            if (mr.getWinner() != null) {
                if (mr.getWinner().getId().equals(team1Id)) team1Wins++;
                else if (mr.getWinner().getId().equals(team2Id)) team2Wins++;
            } else if ("TIE".equals(mr.getResultType())) {
                ties++;
            } else {
                draws++;
            }
            matches.add(m);
        }

        resp.put("totalMatches", results.size());
        resp.put("team1Wins", team1Wins);
        resp.put("team2Wins", team2Wins);
        resp.put("draws", draws);
        resp.put("ties", ties);
        resp.put("matches", matches);
        return ResponseEntity.ok(resp);
    }
}
