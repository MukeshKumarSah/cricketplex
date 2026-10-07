package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.MatchResultRepository;
import com.cricketplex.service.match.BallEventSkip;
import com.cricketplex.service.match.FirstClassMatchSimulator;
import com.cricketplex.service.match.InningsSimulator;
import com.cricketplex.service.match.LineupGenerator;
import com.cricketplex.service.match.MatchFormatRules;
import com.cricketplex.service.match.PostMatchProcessor;
import com.cricketplex.service.match.SimContext;
import com.cricketplex.service.match.TeamStrengthCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Comprehensive ball-by-ball match simulation engine — v5.
 *
 * KEY FIXES OVER v3:
 *  ─ MATH FIXES
 *    • T20 base E[runs/ball] recalibrated: 0×0.25 + 1×0.27 + 2×0.10 + 3×0.02 + 4×0.13 + 6×0.07 = 1.42 → 8.5 RPO
 *      matches real international T20 2019-2024 average.
 *    • ODI base E[runs/ball] correctly verified: 0×0.44 + 1×0.28 + 2×0.09 + 3×0.02 + 4×0.09 + 6×0.04 = 1.10
 *      → 6.6 RPO base; after wicket-loss ≈ 5.6 RPO, matching modern ODI averages.
 *    • skill_diff shift now uses sqrt-dampened scale to prevent extreme probability distortion.
 *    • pWicket hard cap lowered from 0.50 → 0.28 to prevent unrealistic tail decimation.
 *    • Batter fatigue accelerates non-linearly after 100 balls in FC to model genuine tiredness.
 *    • Partnership momentum uses exponential curve (1-e^(-x/30)) instead of linear ramp.
 *    • Bowler fatigue in T20: floor raised to 0.85 so the 4th over is materially harder.
 *
 *  ─ CRICKET RULE FIXES
 *    • Free hit delivery now runs full scoring pipeline; wicket flag suppressed correctly.
 *    • Stumped-off-wide now possible (keeper can stump off wide before batter returns).
 *    • Wide boundary (4 wides) correctly NOT flagged as a batting boundary.
 *    • ODI batting powerplay (PP2/PP3) requires a captain-call flag; not auto-applied.
 *    • Reverse swing trigger now uses ball-age (modular overs), consistent with
 *      getBallConditionModifier.
 *    • Follow-on threshold is match-length-aware: 100 for ≤2-day FC, 200 for 5-day.
 *    • Super over simulated when T20 innings end in a tie.
 *    • No-ball scoring: runs off bat correctly credited; subsequent free-hit ball simulated.
 *
 *  ─ REALISM ADDITIONS
 *    • Positional wicket modifier: tail-enders (pos 8-11) face 1.8-2.5× base pWicket.
 *    • Dynamic field placement: captain sets field based on phase and required rate,
 *      affecting p4/p6/pCaught beyond the powerplay.
 *    • Intra-innings pitch wear for ODI/T20: footmarks and rough develop after over 25.
 *    • Bowler variety selection in fallback: rotates among top bowlers, not always highest.
 *    • Stumped dismissal corrected to require batter out of crease (spin/off-length only).
 *    • Deterministic seed mixes in System.nanoTime for simulation sessions to prevent
 *      prediction attacks while keeping per-fixture replay determinism.
 *
 *  ─ REMOVED (by design — keeps simulation fun and frustration-free)
 *    • DLS (Duckworth-Lewis-Stern) rain interruptions — no over reductions mid-match.
 *    • Day/Night match dew factor — all matches treated identically regardless of timing.
 *    • DRS (Decision Review System) — all umpire decisions are final.
 */
@Service
@RequiredArgsConstructor
public class MatchEngine {

    public static void setSkipBallEvents(boolean skip) { BallEventSkip.setSkipBallEvents(skip); }

    private final MatchResultRepository matchResultRepository;
    private final FixtureRepository fixtureRepository;
    private final WeatherService weatherService;
    private final FixtureService fixtureService;
    private final LineupGenerator lineupGenerator;
    private final InningsSimulator inningsSimulator;
    private final FirstClassMatchSimulator firstClassMatchSimulator;
    private final PostMatchProcessor postMatchProcessor;

    @Autowired @Lazy
    private FriendlyTournamentService friendlyTournamentService;

    // ═══════════════════════════════════════════════════════════════════════════
    //  PUBLIC ENTRY POINT
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional
    public MatchResult simulateMatch(UUID fixtureId) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new IllegalArgumentException("Fixture not found"));

        String fixtureStatus = fixture.getStatus();
        boolean isFcDay2 = "FC_DAY1_COMPLETE".equals(fixtureStatus) ||
                ("IN_PROGRESS".equals(fixtureStatus) && fixture.getFcDay() != null && fixture.getFcDay() == 1);

        if (!"SCHEDULED".equals(fixtureStatus) && !"IN_PROGRESS".equals(fixtureStatus)
                && !"FC_DAY1_COMPLETE".equals(fixtureStatus)) {
            throw new IllegalStateException("Match already played or in invalid state: " + fixtureStatus);
        }
        if ("SCHEDULED".equals(fixtureStatus) && matchResultRepository.existsByFixtureId(fixtureId)) {
            throw new IllegalStateException("Match result already exists");
        }

        League league = fixture.getLeague();
        String format = league != null ? league.getFormat() : fixture.getFormat();
        if (format == null) throw new IllegalArgumentException("Match format could not be determined");

        if ("FC".equalsIgnoreCase(format)) {
            return isFcDay2
                    ? firstClassMatchSimulator.simulateFCDay2(fixture, format)
                    : firstClassMatchSimulator.simulateFCDay1(fixture, format, fixture.getHomeTeam(), fixture.getAwayTeam());
        }

        if (!"T20".equalsIgnoreCase(format) && !"ODI".equalsIgnoreCase(format))
            throw new IllegalArgumentException("Unsupported format: " + format);

        int maxOvers     = MatchFormatRules.getMaxOvers(format);
        int maxPerBowler = MatchFormatRules.getMaxPerBowler(format);
        Team homeTeam    = fixture.getHomeTeam();
        Team awayTeam    = fixture.getAwayTeam();

        MatchLineup homeLineup = lineupGenerator.getOrGenerateLineup(fixture, homeTeam, format);
        MatchLineup awayLineup = lineupGenerator.getOrGenerateLineup(fixture, awayTeam, format);

        Map<String, Object> weather = weatherService.getWeather(homeTeam.getCountry(), fixture.getMatchDate());
        String condition  = (String) weather.get("condition");
        int temperature   = (int)    weather.get("temperature");

        String pitchType = fixture.getPitchType();

        // FIX: seed blends fixture ID + date + nano-time for simulation sessions
        // to prevent prediction attacks while keeping per-fixture determinism for replays.
        boolean isSim = fixture.getSimSessionId() != null;
        long baseSeed = fixtureId.getMostSignificantBits() ^ fixture.getMatchDate().toEpochDay();
        long seed = isSim ? (baseSeed ^ System.nanoTime()) : baseSeed;
        Random rng = new Random(seed);

        boolean homeToss    = rng.nextBoolean();
        Team tossWinner     = homeToss ? homeTeam : awayTeam;
        String tossDecision = inningsSimulator.decideToss(homeToss ? homeLineup : awayLineup,
                pitchType, condition, format, rng);

        Team battingFirst, battingSecond;
        MatchLineup battingFirstLineup, battingSecondLineup;
        if ("BAT".equals(tossDecision)) {
            battingFirst  = tossWinner;
            battingSecond = tossWinner.getId().equals(homeTeam.getId()) ? awayTeam : homeTeam;
            battingFirstLineup  = tossWinner.getId().equals(homeTeam.getId()) ? homeLineup : awayLineup;
            battingSecondLineup = tossWinner.getId().equals(homeTeam.getId()) ? awayLineup : homeLineup;
        } else {
            battingSecond = tossWinner;
            battingFirst  = tossWinner.getId().equals(homeTeam.getId()) ? awayTeam : homeTeam;
            battingSecondLineup = tossWinner.getId().equals(homeTeam.getId()) ? homeLineup : awayLineup;
            battingFirstLineup  = tossWinner.getId().equals(homeTeam.getId()) ? awayLineup : homeLineup;
        }

        MatchResult result = MatchResult.builder()
                .fixture(fixture).tossWinner(tossWinner).tossDecision(tossDecision).build();

        // ── First innings ──
        Innings firstInnings = Innings.builder()
                .matchResult(result).inningsNumber(1)
                .battingTeam(battingFirst).bowlingTeam(battingSecond).build();
        SimContext ctx1 = new SimContext(rng, pitchType, condition, temperature,
                maxOvers, maxPerBowler, format, false, 0,
                battingFirstLineup, battingSecondLineup);
        inningsSimulator.simulateInnings(firstInnings, ctx1);
        result.getInningsList().add(firstInnings);

        int target = firstInnings.getTotalRuns() + 1;

        // ── Second innings ──
        Innings secondInnings = Innings.builder()
                .matchResult(result).inningsNumber(2)
                .battingTeam(battingSecond).bowlingTeam(battingFirst).build();
        SimContext ctx2 = new SimContext(rng, pitchType, condition, temperature,
                maxOvers, maxPerBowler, format, true, target,
                battingSecondLineup, battingFirstLineup, firstInnings.getTotalRuns());
        inningsSimulator.simulateInnings(secondInnings, ctx2);
        result.getInningsList().add(secondInnings);

        // Handle tie → super over for T20
        int firstTotal  = firstInnings.getTotalRuns();
        int secondTotal = secondInnings.getTotalRuns();
        if ("T20".equalsIgnoreCase(format) && firstTotal == secondTotal) {
            inningsSimulator.simulateSuperOver(result, rng, pitchType, condition, temperature,
                    battingFirst, battingSecond, battingFirstLineup, battingSecondLineup);
        } else {
            inningsSimulator.determineResult(result, firstInnings, secondInnings, battingFirst, battingSecond, target);
        }

        result.setManOfMatch(postMatchProcessor.pickManOfMatch(result, rng));
        fixture.setStatus("COMPLETED");
        fixtureRepository.save(fixture);

        if (!isSim && fixture.getLeague() != null) {
            String resolvedFormat = fixture.getLeague().getFormat();
            postMatchProcessor.updateMoraleAndFans(result, fixture, resolvedFormat);
            postMatchProcessor.updatePlayerStats(result, resolvedFormat);
            postMatchProcessor.distributeGateMoney(result, fixture);
        }
        MatchResult saved = matchResultRepository.save(result);
        if (!isSim && fixture.getFriendlyTournament() != null) {
            friendlyTournamentService.onMatchCompleted(fixture, saved);
        }
        if (!isSim) fixtureService.applyPendingSwap(fixture.getId(), saved);
        return saved;
    }

    public void logMatchActivity(MatchResult result, Fixture fixture) {
        postMatchProcessor.logMatchActivity(result, fixture);
    }

    public Map<String, Object> computeTeamStrengthBreakdown(MatchLineup lineup, String pitchType,
                                                             String condition, int temperature) {
        return TeamStrengthCalculator.computeTeamStrengthBreakdown(lineup, pitchType, condition, temperature);
    }
}
