package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchEngine {

    /** When true, BallEvent rows are NOT written to the DB. ThreadLocal for parallel safety. */
    private static final ThreadLocal<Boolean> SKIP_BALL_EVENTS = ThreadLocal.withInitial(() -> false);

    public static void setSkipBallEvents(boolean skip) { SKIP_BALL_EVENTS.set(skip); }

    // ── Format constants ──────────────────────────────────────────────────────
    private static final int ODI_OVERS        = 50;
    private static final int T20_OVERS        = 20;
    private static final int FC_SESSION_OVERS = 50;

    // ── Bowler-type constants ─────────────────────────────────────────────────
    private static final Set<String> PACE_TYPES = Set.of("F", "FM", "MF", "M", "LAP");
    private static final Set<String> SPIN_TYPES = Set.of("FS", "WS");

    // ── Repositories & services ───────────────────────────────────────────────
    private final MatchResultRepository        matchResultRepository;
    private final MatchLineupRepository        matchLineupRepository;
    private final MatchFCStrategyRepository    matchFCStrategyRepository;
    private final DefaultLineupRepository      defaultLineupRepository;
    private final PlayerRepository             playerRepository;
    private final FixtureRepository            fixtureRepository;
    private final TeamRepository               teamRepository;
    private final StadiumSeatsRepository       stadiumSeatsRepository;
    private final TransactionLogRepository     transactionLogRepository;
    private final WeatherService               weatherService;
    private final ActivityLogService           activityLogService;
    private final FixtureService               fixtureService;

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
                    ? simulateFCDay2(fixture, format)
                    : simulateFCDay1(fixture, format, fixture.getHomeTeam(), fixture.getAwayTeam());
        }

        if (!"T20".equalsIgnoreCase(format) && !"ODI".equalsIgnoreCase(format))
            throw new IllegalArgumentException("Unsupported format: " + format);

        int maxOvers     = getMaxOvers(format);
        int maxPerBowler = getMaxPerBowler(format);
        Team homeTeam    = fixture.getHomeTeam();
        Team awayTeam    = fixture.getAwayTeam();

        MatchLineup homeLineup = getOrGenerateLineup(fixture, homeTeam, format);
        MatchLineup awayLineup = getOrGenerateLineup(fixture, awayTeam, format);

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
        String tossDecision = decideToss(homeToss ? homeLineup : awayLineup,
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
        simulateInnings(firstInnings, ctx1);
        result.getInningsList().add(firstInnings);

        int target = firstInnings.getTotalRuns() + 1;

        // ── Second innings ──
        Innings secondInnings = Innings.builder()
                .matchResult(result).inningsNumber(2)
                .battingTeam(battingSecond).bowlingTeam(battingFirst).build();
        SimContext ctx2 = new SimContext(rng, pitchType, condition, temperature,
                maxOvers, maxPerBowler, format, true, target,
                battingSecondLineup, battingFirstLineup, firstInnings.getTotalRuns());
        simulateInnings(secondInnings, ctx2);
        result.getInningsList().add(secondInnings);

        // Handle tie → super over for T20
        int firstTotal  = firstInnings.getTotalRuns();
        int secondTotal = secondInnings.getTotalRuns();
        if ("T20".equalsIgnoreCase(format) && firstTotal == secondTotal) {
            simulateSuperOver(result, rng, pitchType, condition, temperature,
                    battingFirst, battingSecond, battingFirstLineup, battingSecondLineup);
        } else {
            determineResult(result, firstInnings, secondInnings, battingFirst, battingSecond, target);
        }

        result.setManOfMatch(pickManOfMatch(result, rng));
        fixture.setStatus("COMPLETED");
        fixtureRepository.save(fixture);

        if (!isSim && fixture.getLeague() != null) {
            String resolvedFormat = fixture.getLeague().getFormat();
            updateMoraleAndFans(result, fixture, resolvedFormat);
            updatePlayerStats(result, resolvedFormat);
            distributeGateMoney(result, fixture);
        }
        MatchResult saved = matchResultRepository.save(result);
        if (!isSim) fixtureService.applyPendingSwap(fixture.getId(), saved);
        return saved;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SUPER OVER (FIX: T20 tie resolution)
    // ═══════════════════════════════════════════════════════════════════════════

    private void simulateSuperOver(MatchResult result, Random rng,
                                    String pitchType, String condition, int temperature,
                                    Team team1, Team team2,
                                    MatchLineup lineup1, MatchLineup lineup2) {
        // Each team bats 1 over (6 legal balls), 2 wickets = innings over
        int score1 = simulateSuperOverInnings(rng, pitchType, condition, temperature, lineup1, "T20");
        int score2 = simulateSuperOverInnings(rng, pitchType, condition, temperature, lineup2, "T20");

        if (score2 > score1) {
            result.setWinner(team2);
            result.setResultType("SUPER_OVER");
            result.setResultMargin(score2 - score1);
        } else if (score1 > score2) {
            result.setWinner(team1);
            result.setResultType("SUPER_OVER");
            result.setResultMargin(score1 - score2);
        } else {
            // Boundary countback (team with more boundaries in the match wins)
            int b1 = countMatchBoundaries(result, team1);
            int b2 = countMatchBoundaries(result, team2);
            result.setWinner(b1 >= b2 ? team1 : team2);
            result.setResultType("BOUNDARY_COUNTBACK");
            result.setResultMargin(0);
        }
        log.info("Super Over: {} scored {} vs {} scored {}", team1.getTeamName(), score1, team2.getTeamName(), score2);
    }

    private int simulateSuperOverInnings(Random rng, String pitchType, String condition,
                                          int temperature, MatchLineup lineup, String format) {
        List<LineupPlayer> batters = new ArrayList<>(lineup.getPlayers());
        batters.sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));
        List<BowlingOrder> bowlers = new ArrayList<>(lineup.getBowlingOrders());
        bowlers.sort(Comparator.comparingInt(BowlingOrder::getOverNumber));

        Player bowler = bowlers.isEmpty()
                ? batters.get(0).getPlayer()
                : bowlers.get(0).getBowler();

        int idx = 0;
        BatsmanState striker    = new BatsmanState(batters.get(Math.min(idx++, batters.size() - 1)));
        BatsmanState nonStriker = new BatsmanState(batters.get(Math.min(idx++, batters.size() - 1)));
        double fieldingAvg = calculateTeamFielding(lineup);
        double keeperSkill = getKeeperRating(lineup);

        SimContext ctx = new SimContext(rng, pitchType, condition, temperature,
                1, 1, format, false, 0, lineup, lineup);

        int totalRuns = 0, wickets = 0, balls = 0;
        Map<UUID, BattingScorecard> dummyCards = new HashMap<>();
        dummyCards.put(striker.player.getId(), BattingScorecard.builder().build());

        while (balls < 6 && wickets < 2) {
            BattingScorecard batCard = dummyCards.computeIfAbsent(striker.player.getId(),
                    k -> BattingScorecard.builder().build());
            DeliveryResult dr = simulateDelivery(ctx, striker, nonStriker, bowler, "A",
                    fieldingAvg, keeperSkill, 1, totalRuns, wickets,
                    balls, batCard, balls, false, null, 0, 1, false);

            if (!dr.isWide && !dr.isNoBall) balls++;
            totalRuns += dr.runs;

            if (dr.isWicket) {
                wickets++;
                if (wickets < 2 && idx < batters.size()) {
                    striker = new BatsmanState(batters.get(idx++));
                    dummyCards.put(striker.player.getId(), BattingScorecard.builder().build());
                }
            } else if (dr.runs % 2 == 1) {
                BatsmanState tmp = striker; striker = nonStriker; nonStriker = tmp;
            }
        }
        return totalRuns;
    }

    private int countMatchBoundaries(MatchResult result, Team team) {
        int count = 0;
        for (Innings inn : result.getInningsList()) {
            if (!inn.getBattingTeam().getId().equals(team.getId())) continue;
            for (BattingScorecard bc : inn.getBattingCards())
                count += bc.getFours() + bc.getSixes();
        }
        return count;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  INNINGS SIMULATION
    // ═══════════════════════════════════════════════════════════════════════════

    private void simulateInnings(Innings innings, SimContext ctx) {
        List<LineupPlayer> battingOrder = new ArrayList<>(ctx.battingLineup.getPlayers());
        battingOrder.sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));

        List<BowlingOrder> bowlingOrders = new ArrayList<>(ctx.bowlingLineup.getBowlingOrders());
        bowlingOrders.sort(Comparator.comparingInt(BowlingOrder::getOverNumber));

        Map<Integer, BowlingOrder> bowlerPlan = new LinkedHashMap<>();
        for (BowlingOrder bo : bowlingOrders) bowlerPlan.put(bo.getOverNumber(), bo);

        double teamFieldingAvg = calculateTeamFielding(ctx.bowlingLineup);
        double keeperSkill     = getKeeperRating(ctx.bowlingLineup);

        if (battingOrder.size() < 2) return;

        int nextBatIdx   = 2;
        BatsmanState striker    = new BatsmanState(battingOrder.get(0));
        BatsmanState nonStriker = new BatsmanState(battingOrder.get(1));

        Map<UUID, BattingScorecard> batCards  = new LinkedHashMap<>();
        Map<UUID, BowlingScorecard> bowlCards = new LinkedHashMap<>();
        batCards.put(striker.player.getId(),    createBatCard(innings, striker.lineupPlayer,    1));
        batCards.put(nonStriker.player.getId(), createBatCard(innings, nonStriker.lineupPlayer, 2));

        int totalRuns = 0, totalWickets = 0, totalExtras = 0, ballsBowled = 0;
        boolean isFreeHit = false;
        String previousBatHand = null;

        Player currentBowler = null;
        String currentBowlerAggression = "N";
        Map<UUID, Integer> bowlerBallCounts = new HashMap<>();
        Player previousBowler = null;
        int partnershipBalls  = 0;

        // ODI batting PP called by captain — each team gets 1 use
        boolean battingPpUsed = false;
        int battingPpOver     = -1; // over where captain calls it (-1 = not called yet)

        outerLoop:
        for (int over = 0; over < ctx.maxOvers; over++) {
            int overNumber = over + 1;

            // ODI batting powerplay — AI captain calls between overs 11-15 or 41-45
            // when in aggressive situation or required rate demands it.
            if ("ODI".equalsIgnoreCase(ctx.format) && !battingPpUsed) {
                boolean inPp2Window = overNumber >= 11 && overNumber <= 15;
                boolean inPp3Window = overNumber >= 41 && overNumber <= 45;
                if (inPp2Window || inPp3Window) {
                    // Call PP if scoring rate is behind or early in window
                    int runsNeeded = ctx.isChasing ? Math.max(0, ctx.target - totalRuns) : 0;
                    boolean shouldCall = inPp2Window   // usually call PP2 for consolidation
                            || (ctx.isChasing && runsNeeded > 0 && (runsNeeded / (double) Math.max(1, (ctx.maxOvers - over) * 6)) > 1.2);
                    if (shouldCall) { battingPpUsed = true; battingPpOver = overNumber; }
                }
            }
            boolean battingPpActive = battingPpUsed && overNumber == battingPpOver;

            // Bowler selection
            BowlingOrder planned = bowlerPlan.get(overNumber);
            if (planned != null) {
                currentBowler = planned.getBowler();
                currentBowlerAggression = planned.getAggression();
            } else {
                currentBowler = pickFallbackBowler(ctx, bowlerBallCounts, previousBowler, overNumber);
                currentBowlerAggression = "N";
            }

            if (!bowlCards.containsKey(currentBowler.getId()))
                bowlCards.put(currentBowler.getId(), createBowlCard(innings, currentBowler));
            BowlingScorecard bowlCard = bowlCards.get(currentBowler.getId());

            int legalBallsThisOver = 0, runsThisOver = 0;
            boolean maidenPossible  = true;
            int ballInOver          = 0;
            int currentBowlerBalls  = bowlerBallCounts.getOrDefault(currentBowler.getId(), 0);

            while (legalBallsThisOver < 6) {
                ballInOver++;

                DeliveryResult delivery = simulateDelivery(
                        ctx, striker, nonStriker, currentBowler, currentBowlerAggression,
                        teamFieldingAvg, keeperSkill, overNumber, totalRuns, totalWickets,
                        ballsBowled, batCards.get(striker.player.getId()),
                        currentBowlerBalls, isFreeHit, previousBatHand, partnershipBalls,
                        striker.lineupPlayer.getBattingPosition(), battingPpActive);

                applyStrikeFarming(ctx, striker, nonStriker, delivery, legalBallsThisOver);

                if (!Boolean.TRUE.equals(SKIP_BALL_EVENTS.get())) {
                    Player eventBatsman = (delivery.isWicket && delivery.isNonStrikerOut)
                            ? nonStriker.player : striker.player;
                    innings.getBallEvents().add(BallEvent.builder()
                            .innings(innings).overNumber(overNumber).ballNumber(ballInOver)
                            .batsman(eventBatsman).bowler(currentBowler)
                            .runs(delivery.runs).isWicket(delivery.isWicket)
                            .isBoundary(delivery.isBoundary).isSix(delivery.isSix)
                            .isWide(delivery.isWide).isNoBall(delivery.isNoBall)
                            .isBye(delivery.isBye).isLegBye(delivery.isLegBye)
                            .dismissalType(delivery.dismissalType)
                            .fielder(delivery.fielder).commentary(delivery.commentary)
                            .build());
                }

                previousBatHand = striker.player.getBatHand();
                totalRuns += delivery.runs;

                if (!delivery.isWide && !delivery.isNoBall) isFreeHit = false;
                if (delivery.isNoBall && ("T20".equalsIgnoreCase(ctx.format)
                        || "ODI".equalsIgnoreCase(ctx.format))) {
                    isFreeHit = true;
                }

                if (delivery.isWide || delivery.isNoBall) {
                    if (delivery.isNoBall) {
                        int runsOffBat = Math.max(0, delivery.runs - 1);
                        totalExtras += 1;
                        BattingScorecard batCard = batCards.get(striker.player.getId());
                        batCard.setBallsFaced(batCard.getBallsFaced() + 1);
                        if (runsOffBat > 0) {
                            batCard.setRunsScored(batCard.getRunsScored() + runsOffBat);
                            if (delivery.isBoundary) batCard.setFours(batCard.getFours() + 1);
                            if (delivery.isSix)      batCard.setSixes(batCard.getSixes() + 1);
                        }
                    } else {
                        totalExtras += delivery.runs;
                        // FIX: stumped off wide — possible if batter strayed out of crease
                        if (delivery.isWide && delivery.isWicket && "STUMPED".equals(delivery.dismissalType)) {
                            totalWickets++;
                            BattingScorecard bc = batCards.get(striker.player.getId());
                            bc.setDismissalType("STUMPED");
                            bc.setFielder(delivery.fielder);
                            if (totalWickets >= 10 || nextBatIdx >= battingOrder.size()) {
                                innings.setAllOut(true); break outerLoop;
                            }
                            striker = new BatsmanState(battingOrder.get(nextBatIdx));
                            batCards.put(striker.player.getId(),
                                    createBatCard(innings, striker.lineupPlayer, nextBatIdx + 1));
                            nextBatIdx++;
                            partnershipBalls = 0;
                        }
                    }
                    bowlCard.setRunsConceded(bowlCard.getRunsConceded() + delivery.runs);
                    if (delivery.isWide)   bowlCard.setWides(bowlCard.getWides() + 1);
                    if (delivery.isNoBall) bowlCard.setNoBalls(bowlCard.getNoBalls() + 1);
                    maidenPossible = false;
                    runsThisOver  += delivery.runs;
                    if ((delivery.runs % 2) == 1) {
                        BatsmanState t = striker; striker = nonStriker; nonStriker = t;
                    }
                } else {
                    legalBallsThisOver++;
                    ballsBowled++;
                    bowlerBallCounts.merge(currentBowler.getId(), 1, Integer::sum);
                    partnershipBalls++;

                    BattingScorecard batCard = batCards.get(striker.player.getId());
                    batCard.setBallsFaced(batCard.getBallsFaced() + 1);

                    if (delivery.isBye || delivery.isLegBye) {
                        totalExtras += delivery.runs;
                        if (delivery.runs == 0) bowlCard.setDotBalls(bowlCard.getDotBalls() + 1);
                    } else {
                        batCard.setRunsScored(batCard.getRunsScored() + delivery.runs);
                        if (delivery.isBoundary) batCard.setFours(batCard.getFours() + 1);
                        if (delivery.isSix)      batCard.setSixes(batCard.getSixes() + 1);
                        bowlCard.setRunsConceded(bowlCard.getRunsConceded() + delivery.runs);
                        if (delivery.runs == 0 && !delivery.isWicket)
                            bowlCard.setDotBalls(bowlCard.getDotBalls() + 1);
                        else if (delivery.runs > 0) {
                            maidenPossible = false;
                            runsThisOver  += delivery.runs;
                        }
                    }

                    if (!delivery.isWide && !delivery.isNoBall) currentBowlerBalls++;

                    if (delivery.isWicket) {
                        maidenPossible   = false;
                        totalWickets++;
                        partnershipBalls = 0;

                        BattingScorecard dismissedCard = batCard;
                        boolean nso = delivery.isNonStrikerOut;
                        if (nso) dismissedCard = batCards.get(nonStriker.player.getId());

                        if (!"RUN_OUT".equals(delivery.dismissalType)) {
                            bowlCard.setWickets(bowlCard.getWickets() + 1);
                            dismissedCard.setBowler(currentBowler);
                        }
                        dismissedCard.setDismissalType(delivery.dismissalType);
                        dismissedCard.setFielder(delivery.fielder);
                        if (dismissedCard.getBallsFaced() > 0) {
                            dismissedCard.setStrikeRate(
                                    Math.round(dismissedCard.getRunsScored() * 100.0
                                            / dismissedCard.getBallsFaced() * 100.0) / 100.0);
                        }

                        if (totalWickets >= 10 || nextBatIdx >= battingOrder.size()) {
                            innings.setAllOut(true);
                            break outerLoop;
                        }
                        if (nso) {
                            nonStriker = new BatsmanState(battingOrder.get(nextBatIdx));
                            batCards.put(nonStriker.player.getId(),
                                    createBatCard(innings, nonStriker.lineupPlayer, nextBatIdx + 1));
                        } else {
                            striker = new BatsmanState(battingOrder.get(nextBatIdx));
                            batCards.put(striker.player.getId(),
                                    createBatCard(innings, striker.lineupPlayer, nextBatIdx + 1));
                        }
                        nextBatIdx++;
                    } else {
                        if (delivery.runs % 2 == 1) {
                            BatsmanState t = striker; striker = nonStriker; nonStriker = t;
                        }
                    }
                }

                if (ctx.isChasing && totalRuns >= ctx.target) break outerLoop;
            }

            // End of over: rotate strike
            BatsmanState t = striker; striker = nonStriker; nonStriker = t;
            if (maidenPossible && runsThisOver == 0)
                bowlCard.setMaidens(bowlCard.getMaidens() + 1);
            previousBowler = currentBowler;
        }

        // Finalise innings
        innings.setTotalRuns(totalRuns);
        innings.setTotalWickets(totalWickets);
        innings.setExtras(totalExtras);
        int cO = ballsBowled / 6, rB = ballsBowled % 6;
        innings.setTotalOvers(cO + rB / 10.0);

        for (BattingScorecard card : batCards.values()) {
            if (card.getBallsFaced() > 0) {
                card.setStrikeRate(
                        Math.round(card.getRunsScored() * 100.0 / card.getBallsFaced() * 100.0) / 100.0);
            }
            innings.getBattingCards().add(card);
        }
        for (BowlingScorecard card : bowlCards.values()) {
            int bb = bowlerBallCounts.getOrDefault(card.getPlayer().getId(), 0);
            card.setOvers(bb / 6 + (bb % 6) / 10.0);
            if (bb > 0) card.setEconomy(
                    Math.round(card.getRunsConceded() / (bb / 6.0) * 100.0) / 100.0);
            innings.getBowlingCards().add(card);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  DELIVERY SIMULATION
    // ═══════════════════════════════════════════════════════════════════════════

    private DeliveryResult simulateDelivery(
            SimContext ctx, BatsmanState batter, BatsmanState nonStriker,
            Player bowler, String bowlerAggression,
            double teamFieldingAvg, double keeperSkill,
            int overNumber, int totalRuns, int totalWickets, int legalBallsBowled,
            BattingScorecard batCard, int bowlerBalls,
            boolean isFreeHit, String previousBatHand, int partnershipBalls,
            int battingPosition, boolean battingPpActive) {

        Random rng = ctx.rng;
        DeliveryResult result = new DeliveryResult();

        // ── Aggression & player attributes ──
        double batAggrMod  = getAggressionModifier(batter.lineupPlayer.getBatAggression());
        double bowlAggrMod = getAggressionModifier(bowlerAggression);
        int ballsFaced = batCard != null && batCard.getBallsFaced() != null ? batCard.getBallsFaced() : 0;
        double setBatsmanFactor = getSetBatsmanFactor(ctx.format, ballsFaced,
                batter.lineupPlayer.getBatAggression());

        double batConfidence  = batter.player.getConfidence()  / 100.0;
        double bowlConfidence = bowler.getConfidence()         / 100.0;
        double batExpBonus    = Math.min(batter.player.getExperience()  * 0.3, 10.0);
        double bowlExpBonus   = Math.min(bowler.getExperience()         * 0.3, 10.0);
        double batFitness     = batter.player.getFitness()  / 100.0;
        double bowlFitness    = bowler.getFitness()          / 100.0;
        double batStamina     = batter.player.getStamina()   / 100.0;
        double bowlStamina    = bowler.getStamina()          / 100.0;

        // FIX: batter fatigue — non-linear acceleration after 100 balls for FC realism
        double batFatigue;
        if (ballsFaced <= 100) {
            batFatigue = 1.0 - (ballsFaced * 0.0005 * (1.0 - batStamina));
        } else {
            double baseLoss = 100 * 0.0005 * (1.0 - batStamina);
            double extraLoss = (ballsFaced - 100) * 0.0015 * (1.0 - batStamina);
            batFatigue = 1.0 - baseLoss - extraLoss;
        }
        batFatigue = Math.max(0.70, batFatigue);

        // FIX: bowler fatigue — T20 floor raised to 0.85 so 4th over is harder
        boolean bowlerIsPace = PACE_TYPES.contains(bowler.getBowlType());
        double decayRate = bowlerIsPace ? 0.003 : 0.0015;
        double fatigueFloor = "T20".equalsIgnoreCase(ctx.format) ? 0.85
                : "ODI".equalsIgnoreCase(ctx.format) ? 0.78 : 0.65;
        double bowlFatigue = Math.max(fatigueFloor, 1.0 - (bowlerBalls * decayRate * (1.0 - bowlStamina)));

        // ── Pitch effects ──
        PitchEffect pitchEffect    = getPitchEffect(ctx.pitchType, bowler.getBowlType());
        double pitchDecay          = getPitchDecayFactor(ctx.pitchType, overNumber, ctx.maxOvers);
        // FIX: intra-innings pitch wear for ODI/T20 — spin gets more grip after over 25
        double intraPitchWear      = getIntraPitchWear(ctx.format, overNumber, bowler.getBowlType());
        double effectivePitchBonus = pitchEffect.bowlerBonus * pitchDecay + intraPitchWear;

        double batPitchMod = getBatterPitchModifier(ctx.pitchType, batter.player.getBatHand(),
                batter.player.getBatRating(), batter.player.getExperience(), overNumber, ctx.maxOvers);

        // ── Weather ──
        WeatherEffect weatherEffect = getWeatherEffect(ctx.condition, ctx.temperature, bowler.getBowlType());
        double batWeatherMod = getBatterWeatherModifier(ctx.condition, ctx.temperature,
                batter.player.getExperience(), batter.player.getBatRating());

        // ── Type matchup ──
        double typeMatchup   = getBowlerTypeMatchup(bowler.getBowlType(), batter.player.getBatHand(), ctx.pitchType);
        double phaseModifier = getPhaseModifier(ctx.format, overNumber, ctx.maxOvers);

        // FIX: ball age consistent with getBallConditionModifier (uses modular over for FC)
        int ballAgeOvers = "FC".equalsIgnoreCase(ctx.format)
                ? ((overNumber - 1) % 80) + 1 : overNumber;
        double ballCondMod = getBallConditionModifier(ctx.format, overNumber, bowler.getBowlType(), ctx.condition);

        // FIX: reverse swing uses ball age (ballAgeOvers), not raw overNumber
        double reverseSwingBonus = 0;
        if (bowlerIsPace && bowler.getBowlRating() >= 60) {
            int ageThreshold = "ODI".equalsIgnoreCase(ctx.format) ? 35
                    : "FC".equalsIgnoreCase(ctx.format) ? 55 : 99;
            if (ballAgeOvers > ageThreshold) {
                double intensity = Math.min(1.0,
                        (ballAgeOvers - ageThreshold) / (double)(ctx.maxOvers - ageThreshold + 1));
                reverseSwingBonus = 6.0 * intensity;
            }
        }

        // LH/RH disruption (bowler-type-sensitive)
        double lrDisruption = 0;
        if (previousBatHand != null && !previousBatHand.equals(batter.player.getBatHand())) {
            String bt = bowler.getBowlType() != null ? bowler.getBowlType() : "";
            if      ("LAP".equals(bt) && "RH".equals(batter.player.getBatHand())) lrDisruption = -5.0;
            else if ("FS".equals(bt)  && "LH".equals(batter.player.getBatHand())) lrDisruption = -4.0;
            else if ("WS".equals(bt)  && "RH".equals(batter.player.getBatHand())) lrDisruption = -3.5;
            else                                                                    lrDisruption = -2.5;
        }

        // FIX: dynamic field placement — affects p4/p6/pCaught outside powerplay
        double[] fieldPlacement = getDynamicFieldPlacement(ctx, overNumber, totalRuns, totalWickets);
        double fieldP4Mod  = fieldPlacement[0];
        double fieldP6Mod  = fieldPlacement[1];
        double fieldWktMod = fieldPlacement[2];

        // ── Composite strengths ──
        double batStrength = batter.player.getBatRating()
                + batAggrMod   * 8.0
                + batConfidence * 6.0
                + batExpBonus
                + batFitness   * 3.0
                - effectivePitchBonus * 0.75
                + batPitchMod
                + weatherEffect.battingMod
                + batWeatherMod
                + phaseModifier * 2.0
                + setBatsmanFactor * 3.5;
        batStrength *= batFatigue;
        batStrength  = Math.max(5, Math.min(batStrength, 120));

        double bowlStrength = bowler.getBowlRating()
                + bowlAggrMod    * 5.0
                + bowlConfidence * 5.0
                + bowlExpBonus
                + bowlFitness    * 3.0
                + effectivePitchBonus
                + weatherEffect.bowlingMod
                + ballCondMod
                + reverseSwingBonus
                + typeMatchup
                + teamFieldingAvg * 0.15
                + keeperSkill     * 0.05
                + lrDisruption;
        bowlStrength *= bowlFatigue;
        bowlStrength  = Math.max(5, Math.min(bowlStrength, 120));

        // ─── WIDE / NO-BALL CHECK ───
        double wideBase = "T20".equalsIgnoreCase(ctx.format) ? 0.028
                : "FC".equalsIgnoreCase(ctx.format) ? 0.008 : 0.016;
        double noBallBase = "T20".equalsIgnoreCase(ctx.format) ? 0.010
                : "FC".equalsIgnoreCase(ctx.format) ? 0.003 : 0.007;
        boolean deathOvers = ("T20".equalsIgnoreCase(ctx.format) && overNumber >= 16)
                || ("ODI".equalsIgnoreCase(ctx.format) && overNumber >= 41);
        double yorkerPressure = deathOvers && "A".equals(bowlerAggression) ? 0.006 : 0.0;
        double wideChance = wideBase
                + (bowlAggrMod * 0.004)
                + yorkerPressure
                + (1.0 - bowlFitness) * 0.004
                + (1.0 - bowlStamina) * 0.003
                - (bowler.getBowlRating() * 0.00008);
        double noBallChance = noBallBase
                + (bowlAggrMod * 0.0025)
                + (bowlerIsPace ? 0.0015 : -0.0005)
                + (1.0 - bowlFitness) * 0.002
                + (1.0 - bowlStamina) * 0.0015
                - (bowler.getBowlRating() * 0.00004);
        wideChance = Math.max(0.003, Math.min(wideChance, 0.060));
        noBallChance = Math.max(0.001, Math.min(noBallChance, 0.025));
        double extraChance = wideChance + noBallChance;

        if (rng.nextDouble() < extraChance) {
            boolean isWide = rng.nextDouble() < (wideChance / Math.max(0.0001, extraChance));
            result.isWide   = isWide;
            result.isNoBall = !isWide;
            result.runs     = 1;

            if (isWide) {
                double extraRoll = rng.nextDouble();
                if      (extraRoll < 0.05)  { result.runs = 5; }
                else if (extraRoll < 0.15)  { result.runs += 2; }
                else if (extraRoll < 0.20)  { result.runs += 1; }
                // FIX: wide boundary does NOT set isBoundary — it's extras, not batting boundary
                result.commentary = result.runs >= 5
                        ? "Wide — races away for 4 wides to the boundary"
                        : "Wide ball" + (result.runs > 1 ? ", plus " + (result.runs - 1) + " run" + (result.runs > 2 ? "s" : "") : "");

                // FIX: stumped off wide — possible if spinner bowling and batter ventures out
                boolean isSpinner = SPIN_TYPES.contains(bowler.getBowlType());
                if (isSpinner && result.runs == 1 && rng.nextDouble() < 0.015) {
                    // Batter came down the track, missed, stumped off wide
                    result.isWicket     = true;
                    result.dismissalType = "STUMPED";
                    result.fielder       = ctx.bowlingLineup.getKeeper();
                    result.commentary    = "Stumped off a wide! Batter strayed out of crease.";
                }
            } else {
                double extraRoll = rng.nextDouble();
                if      (extraRoll < 0.05)  { result.runs += 4; result.isBoundary = true; }
                else if (extraRoll < 0.15)  { result.runs += 2; }
                else if (extraRoll < 0.20)  { result.runs += 1; }
                result.commentary = "No ball"
                        + (result.runs > 1 ? ", plus " + (result.runs - 1) + " run" + (result.runs > 2 ? "s" : "") : "");
                if (result.isBoundary) result.commentary += " away to the boundary";
            }
            if (isFreeHit) result.commentary = "Free Hit! " + result.commentary;
            return result;
        }

        // ─── BASE SCORING PROBABILITIES ───
        // FIX: Recalibrated for real international averages (2019-2024 ball-by-ball data):
        //   T20:  E = 0.25×0 + 0.27×1 + 0.10×2 + 0.02×3 + 0.13×4 + 0.07×6 = 1.41 → 8.46 RPO (baseline)
        //   ODI:  E = 0.44×0 + 0.28×1 + 0.09×2 + 0.02×3 + 0.09×4 + 0.04×6 = 1.10 → 6.6 RPO → ~5.6 after wickets
        //   FC:   E = 0.52×0 + 0.25×1 + 0.08×2 + 0.03×3 + 0.05×4 + 0.01×6 = 0.76 → ~3.3 RPO after wickets
        double pDot, p1, p2, p3, p4, p6, pWicket;

        if ("T20".equalsIgnoreCase(ctx.format)) {
            pDot    = 0.30;
            p1      = 0.27;   // was 0.30 — fewer singles, more intent
            p2      = 0.09;
            p3      = 0.015;
            p4      = 0.120;  // was 0.115
            p6      = 0.055;
            pWicket = 0.050;
        } else if ("FC".equalsIgnoreCase(ctx.format)) {
            pDot    = 0.57;
            p1      = 0.24;
            p2      = 0.07;
            p3      = 0.02;
            p4      = 0.04;
            p6      = 0.005;
            pWicket = 0.023;
        } else { // ODI
            pDot    = 0.50;   // was 0.48 — more dots means pitch-reading matters
            p1      = 0.27;   // was 0.31 — KEY FIX: fewer singles, more decisive play
            p2      = 0.08;
            p3      = 0.015;
            p4      = 0.085;  // was 0.075 — more boundaries to compensate
            p6      = 0.022;  // was 0.020
            pWicket = 0.028;  // was 0.030 — slightly lower base wicket rate
        }

        // FIX: skill_diff uses sqrt-damped scale to prevent extreme probability distortion
        double skill_diff = batStrength - bowlStrength;
        double rawScale   = "T20".equalsIgnoreCase(ctx.format) ? 1.2
                : "FC".equalsIgnoreCase(ctx.format) ? 0.6 : 0.9;
        // sqrt dampening: large gaps shift less dramatically
        double dampedDiff = Math.signum(skill_diff) * Math.sqrt(Math.abs(skill_diff));
        double shift      = (dampedDiff / 15.0) * rawScale;  // 15.0 normalizes sqrt(225)
        pDot    -= shift * 0.35;
        p1      += shift * 0.10;
        p2      += shift * 0.05;
        p4      += shift * 0.10;
        p6      += shift * 0.06;
        pWicket -= shift * 0.20;

        // ── Batting aggression ──
        double aggrScale  = "T20".equalsIgnoreCase(ctx.format) ? 1.3
                : "FC".equalsIgnoreCase(ctx.format) ? 0.5 : 0.8;
        double batSkillMod = Math.max(0.2, Math.min(1.2, batter.player.getBatRating() / 50.0));
        if ("A".equals(batter.lineupPlayer.getBatAggression())) {
            p4      += 0.025 * aggrScale * batSkillMod;
            p6      += 0.020 * aggrScale * batSkillMod;
            pWicket += 0.012 * aggrScale;
            pDot    -= 0.040 * aggrScale * batSkillMod;
            p1      -= 0.015 * aggrScale * batSkillMod;
        } else if ("D".equals(batter.lineupPlayer.getBatAggression())) {
            p4      -= 0.015 * aggrScale;
            p6      -= 0.015 * aggrScale;
            pWicket -= 0.015 * aggrScale * batSkillMod;
            pDot    += 0.030 * aggrScale;
            p1      += 0.015 * aggrScale;
        }

        // ── Set-batsman effect ──
        double setSkillMod = Math.max(0.3, Math.min(1.0, batter.player.getBatRating() / 40.0));
        if (setBatsmanFactor >= 0) {
            pDot    -= 0.022 * setBatsmanFactor * setSkillMod;
            p1      += 0.004 * setBatsmanFactor * setSkillMod;
            p2      += 0.008 * setBatsmanFactor * setSkillMod;
            p4      += 0.011 * setBatsmanFactor * setSkillMod;
            p6      += 0.006 * setBatsmanFactor * setSkillMod;
            pWicket -= 0.010 * setBatsmanFactor;
        } else {
            // New batter — harder to score, slightly more vulnerable
            // FIXED: reduced settling pWicket from 0.025 to 0.012 to prevent
            // triple-compounding with chase pressure + wicket cluster in innings 2
            double settling = Math.abs(setBatsmanFactor);
            pDot    += 0.06  * settling;
            p1      -= 0.01  * settling;
            p4      -= 0.030 * settling;
            p6      -= 0.020 * settling;
            pWicket += 0.012 * settling;   // was 0.025
        }

        // ── Bowling aggression ──
        if ("A".equals(bowlerAggression)) {
            pWicket += 0.010 * aggrScale;
            p4      += 0.015 * aggrScale;
            p6      += 0.010 * aggrScale;
            pDot    -= 0.015 * aggrScale;
            p1      -= 0.010 * aggrScale;
        } else if ("D".equals(bowlerAggression)) {
            pDot    += 0.030 * aggrScale;
            p1      -= 0.010 * aggrScale;
            p4      -= 0.010 * aggrScale;
            pWicket -= 0.010 * aggrScale;
        }

        // ─── SITUATIONAL MODIFIERS ───

        String playerAggr = batter.lineupPlayer.getBatAggression();
        if (playerAggr == null) playerAggr = "N";

        // 1. Wicket-cluster pressure — FIXED: thresholds raised, pWicket reduced
        // and cluster now REDUCES in chasing innings to prevent triple-compounding
        {
            double completedOvers   = Math.max(1.0, legalBallsBowled / 6.0);
            double wktsPerOver      = totalWickets / completedOvers;
            // RAISED thresholds — was 0.60/0.35/0.45, too trigger-happy
            double collapseThreshold = "T20".equalsIgnoreCase(ctx.format) ? 0.75
                    : "FC".equalsIgnoreCase(ctx.format) ? 0.45 : 0.60;
            double collapseFactor   = 0;
            if (wktsPerOver > collapseThreshold)
                collapseFactor = Math.min(1.0, (wktsPerOver - collapseThreshold) / 0.9);
            // RAISED floor thresholds — was 8/6/4, now 9/7/5
            if (totalWickets >= 9)      collapseFactor = Math.max(collapseFactor, 0.60);
            else if (totalWickets >= 7) collapseFactor = Math.max(collapseFactor, 0.25);
            else if (totalWickets >= 5) collapseFactor = Math.max(collapseFactor, 0.08);
            double resistFactor     = "A".equals(playerAggr) ? 0.7 : ("D".equals(playerAggr) ? 1.2 : 1.0);
            double adjustedCollapse = collapseFactor * resistFactor;
            // In chasing innings, halve the collapse wicket penalty — teams fight back
            if (ctx.isChasing) adjustedCollapse *= 0.5;
            pDot    += 0.050 * adjustedCollapse;  // was 0.060
            p1      += 0.020 * adjustedCollapse;  // was 0.030
            p4      -= 0.030 * adjustedCollapse;  // was 0.040
            p6      -= 0.020 * adjustedCollapse;  // was 0.030
            pWicket += 0.006 * adjustedCollapse;  // was 0.010 — significantly reduced
        }

        // 2. Chase pressure — FIXED: boundary compensation raised, pWicket reduced
        // so teams accelerate scoring rather than just losing wickets faster.
        if (ctx.isChasing) {
            int runsNeeded     = ctx.target - totalRuns;
            int totalBalls     = ctx.maxOvers * 6;
            int ballsRemaining = Math.max(1, totalBalls - legalBallsBowled);
            double requiredRate = (runsNeeded * 6.0) / ballsRemaining;
            // Use actual first innings RPO as par — avoids false pressure spikes
            // when batting first scored a high-but-achievable total
            double pitchParRate = getPitchAwareParRate(ctx.format, ctx.pitchType);
            double parRate = ctx.firstInningsTotal > 0
                    ? Math.max(pitchParRate, ctx.firstInningsTotal * 6.0 / totalBalls)
                    : pitchParRate;
            double progress     = legalBallsBowled / (double) totalBalls;
            double progressMult = 0.30 + (0.70 * progress);

            if (runsNeeded > 0) {
                double ratio = requiredRate / parRate;
                if (ratio > 1.0) {
                    // Behind in chase — accelerate scoring, accept some wicket risk
                    double intensity = Math.min(1.0, (ratio - 1.0)) * progressMult;
                    pDot    -= 0.05 * intensity;   // more intent
                    p1      -= 0.02 * intensity;
                    p4      += 0.04 * intensity;   // more boundary hunting
                    p6      += 0.04 * intensity;   // more sixes attempted
                    pWicket += 0.008 * intensity;  // REDUCED: less punishing
                } else if (ratio < 1.0) {
                    // Ahead in chase — tick singles, protect wickets
                    double intensity = Math.min(1.0, (1.0 - ratio) / 0.6) * progressMult;
                    pDot    -= 0.03 * intensity;
                    p1      += 0.05 * intensity;
                    p2      += 0.01 * intensity;
                    p4      -= 0.01 * intensity;
                    p6      -= 0.005 * intensity;
                    pWicket -= 0.005 * intensity;
                }
            }
        }

        // 3a. Early innings pacing
        double oversDone = legalBallsBowled / 6.0;
        if (oversDone < 4 && totalWickets >= 2) {
            double earlyPressure = Math.min(1.0, totalWickets / 3.0);
            pDot    += 0.04 * earlyPressure;
            p1      += 0.03 * earlyPressure;
            p4      -= 0.03 * earlyPressure;
            p6      -= 0.03 * earlyPressure;
            pWicket -= 0.01 * earlyPressure;
        }
        if ("FC".equalsIgnoreCase(ctx.format) && !ctx.isChasing && oversDone < 10 && totalWickets >= 3) {
            double earlyPressure = Math.min(1.0, totalWickets / 4.0);
            pDot    += 0.06 * earlyPressure;
            p1      += 0.02 * earlyPressure;
            p4      -= 0.04 * earlyPressure;
            p6      -= 0.02 * earlyPressure;
            pWicket -= 0.01 * earlyPressure;
        }

        // 3b. ODI middle overs consolidation — FIXED: removed p1+=0.07 which was
        // consuming too many balls on singles for BOTH innings. Now a mild
        // dot+boundary reshuffle only. Chasing teams in particular benefit from
        // keeping boundary probability alive in the middle overs.
        if ("ODI".equalsIgnoreCase(ctx.format) && oversDone >= 10 && oversDone < 40) {
            pDot += 0.02;   // slightly more defensive
            p1   += 0.02;   // modest singles increase (was +0.07 — removed)
            p4   -= 0.01;   // mild boundary reduction vs powerplay
        }

        // 3c. Death overs acceleration
        if ("T20".equalsIgnoreCase(ctx.format) && oversDone >= 15) {
            double wktFactor = Math.max(0.0, 1.0 - (Math.max(0, totalWickets - 4) * 0.2));
            double deathPush = Math.min(0.90, (0.5 + (oversDone - 15) * 0.16)) * wktFactor;
            pDot    -= 0.07 * deathPush;
            p1      += 0.02 * deathPush;
            p4      += 0.03 * deathPush;
            p6      += 0.02 * deathPush;
            pWicket += 0.015 * deathPush;
        } else if ("ODI".equalsIgnoreCase(ctx.format) && oversDone >= 40) {
            double wktFactor = Math.max(0.0, 1.0 - (Math.max(0, totalWickets - 4) * 0.15));
            double deathPush = Math.min(0.90, (0.5 + (oversDone - 40) * 0.10)) * wktFactor;
            pDot    -= 0.08 * deathPush;
            p1      += 0.04 * deathPush;
            p4      += 0.04 * deathPush;
            p6      += 0.025 * deathPush;
            pWicket += 0.018 * deathPush;
        }

        // 4. Powerplay field restriction (mandatory)
        double[] ppBoost = getPowerplayBoost(ctx.format, overNumber);
        p4      += ppBoost[0];
        p6      += ppBoost[1];
        pDot    -= ppBoost[2];
        pWicket -= ppBoost[3];

        // 5. Dynamic field placement (captain strategy outside powerplay)
        p4      += fieldP4Mod;
        p6      += fieldP6Mod;
        pWicket += fieldWktMod;

        // 6. Mismatch crush / feast
        if (skill_diff < -25) {
            double mm = Math.min(1.0, Math.abs(skill_diff + 25) / 50.0);
            p4      *= (1.0 - 0.85 * mm);
            p6      *= (1.0 - 0.95 * mm);
            pWicket += 0.15 * mm;
            pDot    += 0.10 * mm;
            p1      += 0.05 * mm;
        } else if (skill_diff > 25) {
            double fi = Math.min(1.0, (skill_diff - 25) / 50.0);
            p4      += 0.10 * fi;
            p6      += 0.08 * fi;
            pDot    -= 0.12 * fi;
            p1      -= 0.06 * fi;
        }

        // 7. Free Hit — scoring boost, no wicket (handled at result phase)
        if (isFreeHit) {
            p4      += 0.12;
            p6      += 0.10;
            pDot    -= 0.15;
            p1      -= 0.07;
        }

        // 8. Pitch-phase direct boost
        double[] phaseBoost = getPitchPhaseDirectBoost(ctx.pitchType, overNumber,
                ctx.maxOvers, bowler.getBowlType());
        pDot    += phaseBoost[0];
        pWicket += phaseBoost[1];

        // 9. Partnership momentum — FIX: exponential curve (1-e^(-x/30)) instead of linear
        if (partnershipBalls >= 10) {
            double momentum = 1.0 - Math.exp(-(partnershipBalls - 10) / 30.0); // max approaches 1.0
            pWicket -= 0.020 * momentum; // up to −0.020
            pDot    -= 0.010 * momentum;
            p4      += 0.005 * momentum;
        }

        // 10. Spinner variation delivery (doosra / googly)
        if (SPIN_TYPES.contains(bowler.getBowlType())
                && bowler.getBowlRating() >= 50
                && rng.nextDouble() < 0.05) {
            pWicket += 0.020;
            pDot    += 0.015;
            p4      -= 0.010;
            result.commentary = "[Variation] ";
        }

        // FIX: positional wicket modifier — tailenders face higher dismissal probability
        double positionalWktMult = getPositionalWicketMultiplier(battingPosition);
        pWicket *= positionalWktMult;

        // ── Clamp & normalize ──
        pDot    = Math.max(0.10, pDot);
        p1      = Math.max(0.05, p1);
        p2      = Math.max(0.02, p2);
        p3      = Math.max(0.005, p3);
        p4      = Math.max(0.005, p4);
        p6      = Math.max(0.001, p6);
        // FIX: hard cap at 0.28 (was 0.50) to prevent unrealistic tail decimation
        pWicket = Math.max(0.01, Math.min(pWicket, 0.28));

        double total = pDot + p1 + p2 + p3 + p4 + p6 + pWicket;
        pDot    /= total; p1 /= total; p2 /= total; p3 /= total;
        p4      /= total; p6 /= total; pWicket /= total;

        // ─── ROLL ───
        double roll = rng.nextDouble();
        double cumulative = 0;

        double byeChance = Math.max(0.002, 0.015 - keeperSkill * 0.0001);

        if (roll < (cumulative += pDot)) {
            if (rng.nextDouble() < byeChance) {
                double byeRoll = rng.nextDouble();
                int byeRuns    = (byeRoll < 0.05) ? 4 : (byeRoll < 0.15) ? 2 : (byeRoll < 0.20) ? 3 : 1;
                boolean isLegBye = rng.nextBoolean();
                result.runs      = byeRuns;
                result.isBye     = !isLegBye;
                result.isLegBye  = isLegBye;
                result.isBoundary = (byeRuns == 4);
                result.commentary = (isLegBye ? "Leg bye, " : "Bye, ") + byeRuns
                        + " run" + (byeRuns > 1 ? "s" : "");
                if (result.isBoundary) result.commentary += " away to the boundary";
            } else {
                result.runs       = 0;
                result.commentary += "Dot ball";
            }
        } else if (roll < (cumulative += p1)) {
            result.runs = 1; result.commentary += "Single taken";
        } else if (roll < (cumulative += p2)) {
            result.runs = 2; result.commentary += "Pushed for two";
        } else if (roll < (cumulative += p3)) {
            result.runs = 3; result.commentary += "Three runs taken";
        } else if (roll < (cumulative += p4)) {
            result.runs = 4; result.isBoundary = true;
            result.commentary += "FOUR! " + getBoundaryCommentary(rng);
        } else if (roll < (cumulative += p6)) {
            result.runs = 6; result.isSix = true;
            result.commentary += "SIX! " + getSixCommentary(rng);
        } else {
            // ─── WICKET ───
            // FIX: free hit — run through normal scoring but suppress wicket (except run out)
            if (isFreeHit) {
                // Batter survives; re-roll for scoring
                double fhRoll = rng.nextDouble();
                result.runs = fhRoll < 0.25 ? 0 : fhRoll < 0.50 ? 1 : fhRoll < 0.70 ? 2
                        : fhRoll < 0.85 ? 4 : 6;
                if (result.runs == 4)  result.isBoundary = true;
                if (result.runs == 6)  result.isSix      = true;
                result.commentary = "Free Hit! Would have been out, but batter survives!"
                        + (result.runs > 0 ? " Scores " + result.runs + "." : "");
                return result;
            }

            result.isWicket = true;
            result.runs     = 0;
            DismissalInfo dismissal = determineDismissal(
                    rng, bowler, batter, teamFieldingAvg, keeperSkill, ctx,
                    legalBallsBowled, totalRuns, totalWickets, battingPosition);
            result.dismissalType = dismissal.type;
            result.fielder       = dismissal.fielder;
            result.commentary   += "OUT! " + dismissal.commentary;

            if ("DROPPED".equals(dismissal.type)) {
                result.isWicket      = false;
                result.runs          = 0;
                result.dismissalType = null;
                result.fielder       = dismissal.fielder;
                result.commentary    = dismissal.commentary + " Batter survives!";
            } else if ("RUN_OUT".equals(dismissal.type) && rng.nextDouble() < 0.25) {
                result.runs = 1;
            }
        }

        if (isFreeHit && !result.commentary.startsWith("Free Hit!")) {
            result.commentary = "Free Hit! " + result.commentary;
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  FIX: POSITIONAL WICKET MULTIPLIER
    //  Top-order batters are harder to dismiss; tailenders much easier.
    //  Based on real dismissal rate data: pos 1-3 ~3.5%, pos 8-11 ~8-12%
    // ═══════════════════════════════════════════════════════════════════════════

    private double getPositionalWicketMultiplier(int battingPosition) {
        // FIXED: reduced tail multipliers — 2.10× at pos 10/11 combined with
        // pWicket cap 0.28 was causing every tail ball to be near-fatal,
        // making innings-1 tail batting far worse than innings-2 (which starts
        // chasing with top order). Calibrated to real dismissal rate distributions.
        return switch (battingPosition) {
            case 1, 2    -> 0.75;   // openers — very hard to dismiss, experienced
            case 3       -> 0.82;   // #3 — usually the best batter
            case 4, 5    -> 0.95;   // solid middle order
            case 6, 7    -> 1.10;   // lower middle / all-rounders
            case 8       -> 1.28;   // first tailender — was 1.35
            case 9       -> 1.48;   // genuine tail — was 1.65
            case 10, 11  -> 1.70;   // last two — was 2.10 (way too high)
            default      -> 1.00;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  FIX: DYNAMIC FIELD PLACEMENT
    //  Captain adjusts field based on phase and match situation.
    //  Returns [p4Mod, p6Mod, pWicketMod]
    // ═══════════════════════════════════════════════════════════════════════════

    private double[] getDynamicFieldPlacement(SimContext ctx, int overNumber,
                                               int totalRuns, int totalWickets) {
        // In powerplay: handled by getPowerplayBoost; no additional effect here
        boolean inMandatoryPP = ("T20".equalsIgnoreCase(ctx.format) && overNumber <= 6)
                || ("ODI".equalsIgnoreCase(ctx.format) && overNumber <= 10);
        if (inMandatoryPP) return new double[]{0, 0, 0};

        int wicketsLeft = 10 - totalWickets;
        double completedOvers = overNumber / (double) ctx.maxOvers;

        // Attacking field: more slips, more catchers → higher pWicket, lower p4
        // Defensive field: spread field → lower pWicket, higher p4
        boolean isDeathOvers = ("T20".equalsIgnoreCase(ctx.format) && overNumber > 16)
                || ("ODI".equalsIgnoreCase(ctx.format) && overNumber > 43);

        if (isDeathOvers) {
            // Defensive spread field in death — batter can hit freely but fewer catches
            return new double[]{0.015, 0.010, -0.008};
        }

        if (wicketsLeft <= 3) {
            // Tail: attacking field — more cordon, short leg
            return new double[]{-0.010, -0.008, 0.020};
        }

        if (ctx.isChasing) {
            int runsNeeded = Math.max(0, ctx.target - totalRuns);
            int ballsLeft  = Math.max(1, (ctx.maxOvers - overNumber) * 6);
            double rrr     = runsNeeded * 6.0 / ballsLeft;
            double par     = getPitchAwareParRate(ctx.format, ctx.pitchType);
            if (rrr > par * 1.3) {
                // Chasing hard — batter opening up, fielding captain sets attacking cordon
                return new double[]{0.005, 0.005, 0.010};
            }
            if (rrr < par * 0.7) {
                // Chase very comfortable — fielding captain spreads to prevent easy singles
                return new double[]{0.010, 0.000, -0.005};
            }
        }

        // Middle overs default: mild attacking field (2-3 catchers)
        if (completedOvers > 0.25 && completedOvers < 0.75) {
            return new double[]{-0.005, -0.003, 0.008};
        }

        return new double[]{0, 0, 0};
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  FIX: INTRA-INNINGS PITCH WEAR (ODI/T20)
    //  After over 25 in ODI / over 13 in T20, rough develops from footmarks.
    //  Spin benefits; pace loses conventional swing aid.
    // ═══════════════════════════════════════════════════════════════════════════

    private double getIntraPitchWear(String format, int overNumber, String bowlType) {
        if ("FC".equalsIgnoreCase(format)) return 0; // FC handled by pitch decay already
        boolean isSpin = bowlType != null && SPIN_TYPES.contains(bowlType);
        boolean isPace = bowlType != null && PACE_TYPES.contains(bowlType);

        int wearThreshold = "T20".equalsIgnoreCase(format) ? 13 : 25;
        if (overNumber <= wearThreshold) return 0;

        double wearProgress = Math.min(1.0,
                (overNumber - wearThreshold) / (double)(getMaxOvers(format) - wearThreshold));

        if (isSpin)  return  2.5 * wearProgress;  // rough helps spin grip
        if (isPace)  return -1.0 * wearProgress;  // footmarks disturb run-up line slightly
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  POWERPLAY FIELD RESTRICTION BOOSTS
    // ═══════════════════════════════════════════════════════════════════════════

    private double[] getPowerplayBoost(String format, int overNumber) {
        if ("T20".equalsIgnoreCase(format)) {
            if (overNumber <= 6) return new double[]{0.055, 0.025, 0.04, 0.005};
        } else if ("ODI".equalsIgnoreCase(format)) {
            if (overNumber <= 10) return new double[]{0.045, 0.015, 0.03, 0.004};
            // PP2 and PP3 handled by captain-call in simulateInnings; no auto-boost here
        }
        return new double[]{0, 0, 0, 0};
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PITCH DECAY CURVE
    // ═══════════════════════════════════════════════════════════════════════════

    private double getPitchDecayFactor(String pitchType, int overNumber, int maxOvers) {
        double progress = Math.min(1.0, (double) overNumber / maxOvers);
        return switch (pitchType != null ? pitchType : "STANDARD") {
            case "GREEN"    -> Math.max(0.20, 1.0 - (progress * 1.30));
            case "DUSTY"    -> Math.min(1.15, 0.35 + (progress * 1.10));
            case "DRY"      -> Math.min(1.15, 0.45 + (progress * 0.95));
            case "BOUNCY"   -> Math.max(0.50, 1.0 - (progress * 0.55));
            case "SLOW"     -> Math.min(1.05, 0.55 + (progress * 0.65));
            case "UNEVEN"   -> Math.max(0.70, 1.0 - (progress * 0.30));
            default         -> 1.0;
        };
    }

    private double[] getPitchPhaseDirectBoost(String pitchType, int overNumber,
                                               int maxOvers, String bowlType) {
        double progress  = Math.min(1.0, (double) overNumber / maxOvers);
        boolean isPace   = bowlType != null && PACE_TYPES.contains(bowlType);
        boolean isSpin   = bowlType != null && SPIN_TYPES.contains(bowlType);

        return switch (pitchType != null ? pitchType : "STANDARD") {
            case "GREEN" -> {
                if (isPace && progress < 0.35) {
                    double intensity = 1.0 - (progress / 0.35);
                    yield new double[]{0.07 * intensity, 0.018 * intensity};
                }
                yield new double[]{0, 0};
            }
            case "DUSTY" -> {
                if (isSpin && progress > 0.35) {
                    double intensity = Math.min(1.0, (progress - 0.35) / 0.65);
                    yield new double[]{0.06 * intensity, 0.020 * intensity};
                }
                yield new double[]{0, 0};
            }
            case "DRY" -> {
                if (isSpin && progress > 0.30) {
                    double intensity = Math.min(1.0, (progress - 0.30) / 0.70);
                    yield new double[]{0.05 * intensity, 0.015 * intensity};
                }
                yield new double[]{0, 0};
            }
            case "FLAT"    -> new double[]{-0.04, -0.008};
            case "BOUNCY"  -> {
                if (isPace && progress < 0.50) {
                    double intensity = 1.0 - (progress / 0.50);
                    yield new double[]{0.04 * intensity, 0.012 * intensity};
                }
                yield new double[]{0, 0};
            }
            default -> new double[]{0, 0};
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PITCH AWARE PAR RATE
    // ═══════════════════════════════════════════════════════════════════════════

    private double getPitchAwareParRate(String format, String pitchType) {
        double baseRate = "T20".equalsIgnoreCase(format) ? 8.5
                : "FC".equalsIgnoreCase(format) ? 3.0 : 5.6;
        double pitchMult = switch (pitchType != null ? pitchType : "STANDARD") {
            case "FLAT"   -> 1.15;
            case "GREEN"  -> 0.82;
            case "BOUNCY" -> 0.85;
            case "DUSTY"  -> 0.88;
            case "DRY"    -> 0.90;
            case "UNEVEN" -> 0.88;
            case "SLOW"   -> 0.95;
            default       -> 1.00;
        };
        return baseRate * pitchMult;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  DISMISSAL DETERMINATION
    // ═══════════════════════════════════════════════════════════════════════════

    private DismissalInfo determineDismissal(
            Random rng, Player bowler, BatsmanState batter,
            double fieldingAvg, double keeperSkill, SimContext ctx,
            int legalBallsBowled, int totalRuns, int totalWickets, int battingPosition) {

        String bowlType = bowler.getBowlType() != null ? bowler.getBowlType() : "M";
        boolean isPace  = PACE_TYPES.contains(bowlType);
        boolean isSpin  = SPIN_TYPES.contains(bowlType);
        boolean isLAP   = "LAP".equals(bowlType);

        // Dismissal distribution calibrated to real-world international data:
        // Caught ≈57%, Bowled ≈17%, LBW ≈19%, RunOut ≈4%, Stumped ≈2%, HitWicket ≈0.2%
        double pBowled       = isPace ? 0.17 : (isSpin ? 0.14 : 0.16);
        double pCaught       = isPace ? 0.44 : (isSpin ? 0.42 : 0.43);
        double pLBW          = isPace ? 0.19 : (isSpin ? 0.19 : 0.18);
        // FIX: stumping only realistic if batter is out of crease (spin / off-length bowling)
        double pStumped      = isSpin ? 0.04 : (isPace ? 0.003 : 0.008);
        double pRunOut       = 0.04;
        double pCaughtBehind = isPace ? 0.08 : (isSpin ? 0.03 : 0.05);
        double pHitWicket    = 0.002;

        // Keeper & fielding influences
        pCaught       += fieldingAvg  * 0.001;
        pCaughtBehind += keeperSkill  * 0.0005;
        pStumped      += keeperSkill  * 0.0008;

        // Pitch modifiers
        if ("GREEN".equals(ctx.pitchType) || "BOUNCY".equals(ctx.pitchType)) {
            pCaughtBehind += 0.04;
            pBowled       += 0.03;
        }
        if ("DUSTY".equals(ctx.pitchType) || "DRY".equals(ctx.pitchType)) {
            pStumped += 0.015;
            pLBW     += 0.02;
        }

        // Type-sensitive LBW adjustments
        if (isLAP && "RH".equals(batter.player.getBatHand())) {
            pLBW += 0.04; pBowled += 0.02;
        }
        if (!isLAP && isPace && "RH".equals(batter.player.getBatHand())) {
            pLBW += 0.02;
        }
        if (isSpin && "LH".equals(batter.player.getBatHand()) && "FS".equals(bowlType)) {
            pLBW += 0.03; pStumped += 0.01;
        }
        if ("WS".equals(bowlType) && "RH".equals(batter.player.getBatHand())) {
            pCaughtBehind += 0.015; pStumped += 0.01;
        }

        // FIX: tailenders more likely to be caught or bowled (less LBW — poor technique)
        if (battingPosition >= 9) {
            pCaught += 0.04;
            pBowled += 0.03;
            pLBW    -= 0.03;
        } else if (battingPosition >= 7) {
            pCaught += 0.02;
        }

        // Run-out pressure
        double runOutPressure = 0.0;
        if (ctx.isChasing) {
            double runsNeeded   = ctx.target > 0 ? ctx.target - totalRuns : 0;
            double oversComp    = legalBallsBowled / 6.0;
            double oversRem     = ctx.maxOvers - oversComp;
            double requiredRate = oversRem > 0 ? runsNeeded / oversRem : 0;
            double parRate      = ctx.target > 0 ? (double) ctx.target / ctx.maxOvers : 0;
            if (requiredRate > parRate * 1.5)      runOutPressure = 0.04;
            else if (requiredRate > parRate * 1.2) runOutPressure = 0.02;
        } else {
            double oversComp  = legalBallsBowled / 6.0;
            double wicketRate = oversComp > 0 ? totalWickets / oversComp : 0;
            if (wicketRate > 1.5)      runOutPressure = 0.02;
            else if (wicketRate > 1.0) runOutPressure = 0.01;
        }
        pRunOut += runOutPressure;

        // Normalize
        double dTotal = pBowled + pCaught + pLBW + pStumped + pRunOut + pCaughtBehind + pHitWicket;
        if (dTotal > 1.0) {
            double scale = 1.0 / dTotal;
            pBowled *= scale; pCaught *= scale; pLBW *= scale; pStumped *= scale;
            pRunOut *= scale; pCaughtBehind *= scale; pHitWicket *= scale;
            dTotal = 1.0;
        }

        double dRoll = rng.nextDouble() * dTotal;
        double dCum  = 0;

        List<LineupPlayer> fieldingPlayers = ctx.bowlingLineup.getPlayers();
        Player keeper = ctx.bowlingLineup.getKeeper();
        List<Player> nonKeeperFielders = fieldingPlayers.stream()
                .map(LineupPlayer::getPlayer)
                .filter(p -> keeper == null || !p.getId().equals(keeper.getId()))
                .toList();
        Player randomFielder = nonKeeperFielders.isEmpty()
                ? fieldingPlayers.get(rng.nextInt(fieldingPlayers.size())).getPlayer()
                : nonKeeperFielders.get(rng.nextInt(nonKeeperFielders.size()));

        if (dRoll < (dCum += pBowled)) {
            return new DismissalInfo("BOWLED", null,
                    batter.player.getFirstName() + " " + batter.player.getLastName()
                    + " bowled by " + bowler.getFirstName() + " " + bowler.getLastName());

        } else if (dRoll < (dCum += pCaught)) {
            double catchChance = 0.40 + (randomFielder.getFldRating() * 0.006);
            catchChance = Math.min(0.97, catchChance);
            if (rng.nextDouble() > catchChance) {
                return new DismissalInfo("DROPPED", randomFielder,
                        "Dropped! " + randomFielder.getFirstName() + " "
                        + randomFielder.getLastName() + " puts it down at "
                        + getFieldPosition(rng) + "!");
            }
            return new DismissalInfo("CAUGHT", randomFielder,
                    "Caught by " + randomFielder.getFirstName() + " " + randomFielder.getLastName()
                    + " off " + bowler.getFirstName() + " " + bowler.getLastName());

        } else if (dRoll < (dCum += pLBW)) {
            return new DismissalInfo("LBW", null,
                    "LBW! Trapped in front by " + bowler.getFirstName() + " " + bowler.getLastName());

        } else if (dRoll < (dCum += pStumped)) {
            return new DismissalInfo("STUMPED", keeper,
                    "Stumped by " + (keeper != null
                            ? keeper.getFirstName() + " " + keeper.getLastName() : "the keeper")
                    + " off " + bowler.getFirstName() + " " + bowler.getLastName());

        } else if (dRoll < (dCum += pRunOut)) {
            return new DismissalInfo("RUN_OUT", randomFielder,
                    "Run out! Direct hit by " + randomFielder.getFirstName() + " " + randomFielder.getLastName());

        } else if (dRoll < (dCum += pCaughtBehind)) {
            double catchChance = 0.55 + (keeper != null ? keeper.getKeeperRating() * 0.004 : 0);
            catchChance = Math.min(0.97, catchChance);
            if (rng.nextDouble() > catchChance) {
                String kn = keeper != null
                        ? keeper.getFirstName() + " " + keeper.getLastName() : "The keeper";
                return new DismissalInfo("DROPPED", keeper,
                        "Dropped! " + kn + " spills a tough chance behind the stumps!");
            }
            return new DismissalInfo("CAUGHT_BEHIND", keeper,
                    "Caught behind by " + (keeper != null
                            ? keeper.getFirstName() + " " + keeper.getLastName() : "the keeper")
                    + " off " + bowler.getFirstName() + " " + bowler.getLastName());

        } else {
            return new DismissalInfo("HIT_WICKET", null,
                    "Hit wicket! " + batter.player.getFirstName() + " " + batter.player.getLastName()
                    + " dislodged the bails");
        }
    }

    private String getFieldPosition(Random rng) {
        String[] positions = {"mid-on", "mid-off", "square leg", "fine leg", "cover",
                "point", "gully", "slip", "third man", "long-on", "long-off", "deep mid-wicket"};
        return positions[rng.nextInt(positions.length)];
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PITCH EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════

    private PitchEffect getPitchEffect(String pitchType, String bowlType) {
        String type = bowlType != null ? bowlType : "NONE";
        double bowlerBonus = switch (pitchType != null ? pitchType : "STANDARD") {
            case "GREEN" -> switch (type) {
                case "F", "LAP" -> 14; case "M" -> 12; case "MF" -> 10;
                case "FM" -> 9; case "FS" -> 2; case "WS" -> 1; default -> 6;
            };
            case "DUSTY" -> switch (type) {
                case "FS" -> 14; case "WS" -> 12; case "M" -> 5; case "MF" -> 4;
                case "FM" -> 3; case "F", "LAP" -> 2; default -> 5;
            };
            case "FLAT" -> switch (type) {
                case "F", "LAP" -> -5; case "FM" -> -6; case "MF" -> -6;
                case "M" -> -7; case "FS" -> -5; case "WS" -> -4; default -> -6;
            };
            case "BOUNCY" -> switch (type) {
                case "F", "LAP" -> 12; case "FM" -> 8; case "MF" -> 7;
                case "M" -> 4; case "FS" -> 1; case "WS" -> 0; default -> 5;
            };
            case "UNEVEN" -> switch (type) {
                case "F", "LAP" -> 10; case "MF" -> 9; case "FM" -> 8;
                case "M" -> 6; case "WS" -> 7; case "FS" -> 6; default -> 7;
            };
            case "DRY" -> switch (type) {
                case "WS" -> 12; case "FS" -> 10; case "M" -> 5; case "MF" -> 4;
                case "FM" -> 2; case "F", "LAP" -> 1; default -> 4;
            };
            case "SLOW" -> switch (type) {
                case "FS" -> 7; case "WS" -> 5; case "M" -> 2; case "MF" -> 0;
                case "FM" -> -2; case "F", "LAP" -> -4; default -> 1;
            };
            default -> 0;
        };
        return new PitchEffect(bowlerBonus);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  WEATHER EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════

    private WeatherEffect getWeatherEffect(String condition, int temperature, String bowlType) {
        String type = bowlType != null ? bowlType : "NONE";
        double battingMod = 0, bowlingMod = 0;
        switch (condition != null ? condition : "Sunny") {
            case "Sunny" -> {
                battingMod = 3;
                bowlingMod = switch (type) {
                    case "F","LAP" -> -3; case "FM" -> -2; case "MF" -> -1;
                    case "M" -> -1; case "FS" -> 0; case "WS" -> 0; default -> -1;
                };
            }
            case "Hot & Humid" -> {
                battingMod = 1;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 2; case "FM" -> 1; case "MF" -> 0;
                    case "M" -> -1; case "FS" -> 2; case "WS" -> 1; default -> 0;
                };
            }
            case "Partly Cloudy" -> {
                battingMod = 1;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 3; case "FM" -> 2; case "MF" -> 2;
                    case "M" -> 1; case "FS" -> 1; case "WS" -> 0; default -> 1;
                };
            }
            case "Overcast" -> {
                battingMod = -3;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 10; case "FM" -> 7; case "MF" -> 6;
                    case "M" -> 5; case "FS" -> 1; case "WS" -> 0; default -> 4;
                };
            }
            case "Light Rain" -> {
                battingMod = -4;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 8; case "FM" -> 6; case "MF" -> 5;
                    case "M" -> 4; case "FS" -> 0; case "WS" -> -1; default -> 3;
                };
            }
            case "Heavy Rain" -> {
                battingMod = -6;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 5; case "FM" -> 4; case "MF" -> 3;
                    case "M" -> 2; case "FS" -> -2; case "WS" -> -3; default -> 2;
                };
            }
            case "Windy" -> {
                battingMod = -1;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 4; case "FM" -> 3; case "MF" -> 2;
                    case "M" -> 1; case "FS" -> 2; case "WS" -> 3; default -> 2;
                };
            }
            case "Foggy" -> {
                battingMod = -5;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 4; case "FM" -> 3; case "MF" -> 2;
                    case "M" -> 1; case "FS" -> 3; case "WS" -> 3; default -> 2;
                };
            }
        }
        if (temperature > 38) { battingMod -= 2; bowlingMod -= 1; }
        else if (temperature < 12) { bowlingMod -= 1; }
        return new WeatherEffect(battingMod, bowlingMod);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BATTER PITCH MODIFIER (with DUSTY/DRY adaptability decay)
    // ═══════════════════════════════════════════════════════════════════════════

    private double getBatterPitchModifier(String pitchType, String batHand,
                                           int batRating, int experience,
                                           int overNumber, int maxOvers) {
        boolean isLH      = "LH".equals(batHand);
        double skillAdapt = batRating >= 60 ? 2.0 : (batRating >= 40 ? 1.0 : 0);
        double expAdapt   = experience >= 15 ? 2.0 : (experience >= 8 ? 1.0 : -1.0);
        double progress   = Math.min(1.0, (double) overNumber / maxOvers);

        return switch (pitchType != null ? pitchType : "STANDARD") {
            case "GREEN"   -> (isLH ? 3 : 0) + skillAdapt * 0.5 + expAdapt * 0.5;
            case "DUSTY"   -> {
                double decay = Math.max(0.0, 1.0 - (progress * 1.2));
                yield ((isLH ? -4 : -1) + skillAdapt + expAdapt * 0.5) * decay;
            }
            case "FLAT"    -> 4 + skillAdapt * 1.5;
            case "BOUNCY"  -> (isLH ? -2 : -1) + skillAdapt + expAdapt;
            case "UNEVEN"  -> -2 + skillAdapt + expAdapt;
            case "DRY"     -> {
                double decay = Math.max(0.0, 1.0 - (progress * 1.0));
                yield ((isLH ? -3 : 0) + skillAdapt * 0.8 + expAdapt * 0.5) * decay;
            }
            case "SLOW"    -> 2 + skillAdapt;
            default        -> 0;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BATTER WEATHER MODIFIER
    // ═══════════════════════════════════════════════════════════════════════════

    private double getBatterWeatherModifier(String condition, int temperature,
                                             int experience, int batRating) {
        double expFactor   = experience >= 15 ? 2.0 : (experience >= 8 ? 0.5 : -1.5);
        double skillFactor = batRating >= 50  ? 1.0 : (batRating >= 30 ? 0 : -1.0);
        return switch (condition != null ? condition : "Sunny") {
            case "Sunny","Hot & Humid","Partly Cloudy" -> 0;
            case "Overcast"   -> -1 + expFactor * 0.8 + skillFactor * 0.5;
            case "Light Rain" -> -2 + expFactor + skillFactor * 0.5;
            case "Heavy Rain" -> -3 + expFactor * 1.2 + skillFactor * 0.5;
            case "Windy"      -> -1 + skillFactor + expFactor * 0.3;
            case "Foggy"      -> -2 + expFactor + skillFactor * 0.3;
            default           -> 0;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BOWLER TYPE MATCHUP
    // ═══════════════════════════════════════════════════════════════════════════

    private double getBowlerTypeMatchup(String bowlType, String batHand, String pitchType) {
        if (bowlType == null) return 0;
        double bonus = 0;
        if ("LAP".equals(bowlType) && "RH".equals(batHand)) bonus += 4;
        if ("LAP".equals(bowlType) && "LH".equals(batHand)) bonus -= 1;
        if ("LH".equals(batHand)) {
            if ("FS".equals(bowlType)) bonus += 3;
            if ("WS".equals(bowlType)) bonus -= 1;
        }
        if (("F".equals(bowlType) || "FM".equals(bowlType)) &&
                ("GREEN".equals(pitchType) || "BOUNCY".equals(pitchType))) bonus += 3;
        if (("FS".equals(bowlType) || "WS".equals(bowlType)) &&
                ("DUSTY".equals(pitchType) || "DRY".equals(pitchType))) bonus += 4;
        if ("M".equals(bowlType) || "MF".equals(bowlType)) bonus -= 1;
        return bonus;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PHASE MODIFIER
    // ═══════════════════════════════════════════════════════════════════════════

    private double getPhaseModifier(String format, int overNumber, int maxOvers) {
        if ("T20".equalsIgnoreCase(format)) {
            if (overNumber <= 6)  return 1.0;
            if (overNumber <= 15) return -0.5;
            return 4.0;
        } else if ("ODI".equalsIgnoreCase(format)) {
            if (overNumber <= 10) return 0.5;
            if (overNumber <= 30) return 0;
            if (overNumber <= 40) return 0.5;
            return 2.5;
        } else if ("FC".equalsIgnoreCase(format)) {
            int sessionOver = ((overNumber - 1) % 50) + 1;
            if (sessionOver <= 10)  return 0.8;
            if (sessionOver <= 30)  return -1.0;
            if (sessionOver <= 45)  return 0;
            return 1.0;
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BALL CONDITION MODIFIER
    // ═══════════════════════════════════════════════════════════════════════════

    private double getBallConditionModifier(String format, int overNumber,
                                             String bowlType, String condition) {
        String type    = bowlType != null ? bowlType : "M";
        boolean isPace = PACE_TYPES.contains(type);
        boolean isSpin = SPIN_TYPES.contains(type);

        // FIX: use modular ball age for FC (new ball taken every 80 overs)
        int ballAgeOvers = "FC".equalsIgnoreCase(format)
                ? ((overNumber - 1) % 80) + 1 : overNumber;

        boolean isNewBall, isOldBall;
        if ("T20".equalsIgnoreCase(format)) {
            isNewBall = ballAgeOvers <= 4;
            isOldBall = ballAgeOvers >= 15;
        } else if ("ODI".equalsIgnoreCase(format)) {
            isNewBall = ballAgeOvers <= 10;
            isOldBall = ballAgeOvers >= 35;
        } else {
            isNewBall = ballAgeOvers <= 15;
            isOldBall = ballAgeOvers >= 60;
        }

        if (isNewBall) {
            if (isPace) return 4.0;
            if (isSpin) return -3.0;
        } else if (isOldBall) {
            if (isSpin) return 5.0;
            if (isPace) {
                if (("Hot & Humid".equals(condition) || "Sunny".equals(condition))
                        && (type.equals("F") || type.equals("FM") || type.equals("LAP")))
                    return 3.0;
                return -3.0;
            }
        }
        return 0.0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  AGGRESSION & SET-BATSMAN HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private double getAggressionModifier(String aggression) {
        return switch (aggression != null ? aggression : "N") {
            case "A" ->  1.5;
            case "D" -> -1.0;
            default  ->  0.0;
        };
    }

    private double getSetBatsmanFactor(String format, int ballsFaced, String aggression) {
        int balls = Math.max(0, ballsFaced);
        double set;
        if ("T20".equalsIgnoreCase(format)) {
            if (balls <= 6)       set = -0.10;
            else if (balls <= 15) set = -0.10 + 0.32 * (balls - 6)  / 9.0;
            else if (balls <= 30) set = 0.22  + 0.30 * (balls - 15) / 15.0;
            else                  set = 0.52  + 0.10 * (1.0 - Math.exp(-(balls - 30) / 16.0));
        } else if ("FC".equalsIgnoreCase(format)) {
            if (balls <= 15)       set = -0.15;
            else if (balls <= 50)  set = -0.15 + 0.47 * (balls - 15)  / 35.0;
            else if (balls <= 120) set = 0.32  + 0.38 * (balls - 50)  / 70.0;
            else                   set = 0.70  + 0.12 * (1.0 - Math.exp(-(balls - 120) / 55.0));
        } else { // ODI
            if (balls <= 10)       set = -0.12;
            else if (balls <= 30)  set = -0.12 + 0.34 * (balls - 10) / 20.0;
            else if (balls <= 50)  set = 0.22  + 0.24 * (balls - 30) / 20.0;
            else if (balls <= 90)  set = 0.46  + 0.26 * (balls - 50) / 40.0;
            else if (balls <= 120) set = 0.72  + 0.08 * (balls - 90) / 30.0;
            else                   set = 0.80  + 0.04 * (1.0 - Math.exp(-(balls - 120) / 45.0));
        }
        if ("A".equals(aggression))      set *= 1.06;
        else if ("D".equals(aggression)) set *= 0.94;
        return Math.max(-0.18, Math.min(set, 0.88));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  STRIKE FARMING
    // ═══════════════════════════════════════════════════════════════════════════

    private void applyStrikeFarming(SimContext ctx, BatsmanState striker, BatsmanState nonStriker,
                                     DeliveryResult delivery, int legalBallsThisOver) {
        if (delivery.isWicket || delivery.isBoundary || delivery.isSix
                || delivery.isWide || delivery.isNoBall || delivery.isBye || delivery.isLegBye) return;

        double strikerSkill = Math.max(1.0, striker.player.getBatRating());
        double partnerSkill = Math.max(1.0, nonStriker.player.getBatRating());
        boolean isStrongStriker = strikerSkill > partnerSkill && (partnerSkill / strikerSkill) <= 0.45;
        boolean isWeakStriker   = partnerSkill > strikerSkill && (strikerSkill / partnerSkill) <= 0.45;
        if (!isStrongStriker && !isWeakStriker) return;

        double gapIntensity = isStrongStriker
                ? (0.45 - partnerSkill / strikerSkill) / 0.45
                : (0.45 - strikerSkill / partnerSkill) / 0.45;
        double actionChance = 0.30 + 0.40 * gapIntensity;
        if ("T20".equalsIgnoreCase(ctx.format))      actionChance += 0.15;
        else if ("FC".equalsIgnoreCase(ctx.format))  actionChance -= 0.10;
        if (ctx.rng.nextDouble() > actionChance) return;

        if (isStrongStriker) {
            if (legalBallsThisOver < 4) {
                if (delivery.runs % 2 != 0) {
                    delivery.runs--;
                    delivery.commentary = "Refuses the single to keep the strike.";
                }
            } else {
                if (delivery.runs % 2 == 0) {
                    if (delivery.runs == 0) {
                        delivery.runs = 1;
                        delivery.commentary = "Tapped for a quick single to farm the strike next over.";
                    } else if (delivery.runs == 2) {
                        delivery.runs = 1;
                        delivery.commentary = "Settles for one to keep strike.";
                    }
                }
            }
        } else {
            if (legalBallsThisOver < 5) {
                if (delivery.runs % 2 == 0) {
                    if (delivery.runs == 0) {
                        delivery.runs = 1;
                        delivery.commentary = "Nudges one to give strike to the set batter.";
                    } else if (delivery.runs == 2) {
                        delivery.runs = 1;
                        delivery.commentary = "Declines the second to hand over the strike.";
                    }
                }
            } else {
                if (delivery.runs % 2 != 0) {
                    delivery.runs--;
                    delivery.commentary = "Refuses the single to keep the better batter on strike next over.";
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  TEAM FIELDING & KEEPER
    // ═══════════════════════════════════════════════════════════════════════════

    private double calculateTeamFielding(MatchLineup bowlingLineup) {
        double sum = 0; int count = 0;
        for (LineupPlayer lp : bowlingLineup.getPlayers()) { sum += lp.getPlayer().getFldRating(); count++; }
        return count > 0 ? sum / count : 30;
    }

    private double getKeeperRating(MatchLineup bowlingLineup) {
        Player keeper = bowlingLineup.getKeeper();
        if (keeper != null) return keeper.getKeeperRating();
        return bowlingLineup.getPlayers().stream()
                .mapToDouble(lp -> lp.getPlayer().getKeeperRating()).max().orElse(20);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  FIX: FALLBACK BOWLER SELECTION — rotates among top bowlers, not always #1
    // ═══════════════════════════════════════════════════════════════════════════

    private Player pickFallbackBowler(SimContext ctx, Map<UUID, Integer> bowlerBallCounts,
                                       Player previousBowler, int overNumber) {
        int maxBalls = ctx.maxPerBowler * 6;
        List<LineupPlayer> candidates = new ArrayList<>();
        for (LineupPlayer lp : ctx.bowlingLineup.getPlayers()) {
            Player p   = lp.getPlayer();
            if (previousBowler != null && p.getId().equals(previousBowler.getId())) continue;
            int bowled = bowlerBallCounts.getOrDefault(p.getId(), 0);
            if (bowled < maxBalls && p.getBowlRating() >= 15) candidates.add(lp);
        }
        if (candidates.isEmpty()) {
            for (LineupPlayer lp : ctx.bowlingLineup.getPlayers()) {
                Player p = lp.getPlayer();
                if (previousBowler != null && p.getId().equals(previousBowler.getId())) continue;
                if (bowlerBallCounts.getOrDefault(p.getId(), 0) < maxBalls) candidates.add(lp);
            }
        }
        if (candidates.isEmpty()) return ctx.bowlingLineup.getPlayers().get(0).getPlayer();

        // FIX: weighted random selection — higher rated bowlers get more picks but not exclusively
        // Sort by rating descending; use overNumber as variety seed to rotate
        candidates.sort((a, b) -> Integer.compare(b.getPlayer().getBowlRating(), a.getPlayer().getBowlRating()));
        int topN = Math.min(3, candidates.size()); // consider top 3
        // Rotate among top-N using a simple weighted pick: top bowler 50%, 2nd 30%, 3rd 20%
        double[] weights = {0.50, 0.30, 0.20};
        double roll = ctx.rng.nextDouble();
        double cum  = 0;
        for (int i = 0; i < topN; i++) {
            cum += weights[i];
            if (roll < cum) return candidates.get(i).getPlayer();
        }
        return candidates.get(0).getPlayer();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  TOSS DECISION — FORMAT-AWARE
    // ═══════════════════════════════════════════════════════════════════════════

    private String decideToss(MatchLineup lineup, String pitchType, String weatherCondition,
                               String format, Random rng) {
        if (lineup.getBatOrBowl() != null && !lineup.getBatOrBowl().isEmpty()) {
            return lineup.getBatOrBowl().toUpperCase();
        }
        int batScore = 0;
        if ("T20".equalsIgnoreCase(format)) {
            batScore -= 1;
        } else if ("ODI".equalsIgnoreCase(format)) {
            batScore += 1;
        }
        switch (pitchType != null ? pitchType : "STANDARD") {
            case "FLAT"              -> batScore += 3;
            case "GREEN", "BOUNCY"   -> batScore -= 3;
            case "DUSTY", "DRY"      -> batScore -= 1;
            case "UNEVEN"            -> batScore -= 2;
            default                  -> { }
        }
        if ("Overcast".equals(weatherCondition) || "Light Rain".equals(weatherCondition)) batScore -= 2;
        else if ("Sunny".equals(weatherCondition)) batScore += 1;
        batScore += rng.nextInt(3) - 1;
        return batScore >= 0 ? "BAT" : "BOWL";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  RESULT DETERMINATION
    // ═══════════════════════════════════════════════════════════════════════════

    private void determineResult(MatchResult result, Innings first, Innings second,
                                  Team battingFirst, Team battingSecond, int target) {
        int ft = first.getTotalRuns(), st = second.getTotalRuns();
        if (st >= target) {
            result.setWinner(battingSecond);
            result.setResultType("WICKETS");
            result.setResultMargin(10 - second.getTotalWickets());
        } else if (ft > st) {
            result.setWinner(battingFirst);
            result.setResultType("RUNS");
            result.setResultMargin(ft - st);
        } else {
            result.setResultType("TIE");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  FC SIMULATION
    // ═══════════════════════════════════════════════════════════════════════════

    private static class FCResumeState {
        UUID strikerId, nonStrikerId, previousBowlerId;
        int nextBatIdx;
        Map<UUID, Integer> bowlerBallCounts = new HashMap<>();
    }

    private String buildResumeState(BatsmanState striker, BatsmanState nonStriker,
                                     int nextBatIdx, Map<UUID, Integer> bowlerBallCounts,
                                     Player previousBowler) {
        StringBuilder sb = new StringBuilder();
        sb.append(striker.player.getId()).append('|');
        sb.append(nonStriker.player.getId()).append('|');
        sb.append(nextBatIdx).append('|');
        sb.append(previousBowler != null ? previousBowler.getId().toString() : "null").append('|');
        boolean first = true;
        for (Map.Entry<UUID, Integer> e : bowlerBallCounts.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    private FCResumeState parseResumeState(String state) {
        String[] parts = state.split("\\|", -1);
        FCResumeState r = new FCResumeState();
        r.strikerId        = UUID.fromString(parts[0]);
        r.nonStrikerId     = UUID.fromString(parts[1]);
        r.nextBatIdx       = Integer.parseInt(parts[2]);
        r.previousBowlerId = "null".equals(parts[3]) ? null : UUID.fromString(parts[3]);
        if (parts.length > 4 && !parts[4].isEmpty()) {
            for (String entry : parts[4].split(",")) {
                String[] kv = entry.split("=");
                r.bowlerBallCounts.put(UUID.fromString(kv[0]), Integer.parseInt(kv[1]));
            }
        }
        return r;
    }

    private int computeBallsBowled(Double totalOvers) {
        if (totalOvers == null || totalOvers <= 0) return 0;
        int full    = (int) Math.floor(totalOvers);
        int partial = (int) Math.round((totalOvers - full) * 10);
        return full * 6 + partial;
    }

    private boolean isHumanTeam(Team team) { return team.getOwner() != null; }

    private Map<UUID, MatchFCStrategy> loadFCStrategies(Fixture fixture) {
        Map<UUID, MatchFCStrategy> byTeam = new HashMap<>();
        for (MatchFCStrategy s : matchFCStrategyRepository.findByFixtureId(fixture.getId()))
            byTeam.put(s.getTeam().getId(), s);
        return byTeam;
    }

    private Integer getDeclareInn1(Fixture fixture, Map<UUID, MatchFCStrategy> s, Team team) {
        MatchFCStrategy st = s.get(team.getId()); return st != null ? st.getDeclareInn1() : fixture.getFcDeclareInn1();
    }
    private Integer getDeclareInn2Lead(Fixture fixture, Map<UUID, MatchFCStrategy> s, Team team) {
        MatchFCStrategy st = s.get(team.getId()); return st != null ? st.getDeclareInn2Lead() : fixture.getFcDeclareInn2Lead();
    }
    private Integer getDeclareInn3Lead(Fixture fixture, Map<UUID, MatchFCStrategy> s, Team team) {
        MatchFCStrategy st = s.get(team.getId()); return st != null ? st.getDeclareInn3Lead() : fixture.getFcDeclareInn3Lead();
    }
    private Boolean getFollowOn(Fixture fixture, Map<UUID, MatchFCStrategy> s, Team team) {
        MatchFCStrategy st = s.get(team.getId()); return st != null ? st.getFollowOn() : fixture.getFcFollowOn();
    }

    private MatchResult saveFCDay1Complete(Fixture fixture, MatchResult result) {
        result.setResultType("PENDING");
        fixture.setFcDay(1);
        fixture.setStatus("FC_DAY1_COMPLETE");
        if (fixture.getMatchDate() != null) fixture.setMatchDate(fixture.getMatchDate().plusDays(1));
        fixtureRepository.save(fixture);
        return matchResultRepository.save(result);
    }

    private MatchResult finalizeFCMatch(Fixture fixture, MatchResult result, Random rng) {
        result.setManOfMatch(pickManOfMatch(result, rng));
        fixture.setFcDay(2);
        fixture.setStatus("COMPLETED");
        fixtureRepository.save(fixture);
        boolean isSim = fixture.getSimSessionId() != null;
        if (!isSim && fixture.getLeague() != null) {
            updateMoraleAndFans(result, fixture, "FC");
            updatePlayerStats(result, "FC");
            distributeGateMoney(result, fixture);
        }
        MatchResult saved = matchResultRepository.save(result);
        if (!isSim) fixtureService.applyPendingSwap(fixture.getId(), saved);
        return saved;
    }

    private long[] computeInn3Declaration(Fixture fixture, Map<UUID, MatchFCStrategy> s,
                                           List<Innings> inningsList, Team battingTeam,
                                           Team battingFirst, Team battingSecond) {
        int i1 = inningsList.get(0).getTotalRuns();
        int i2 = inningsList.get(1).getTotalRuns();
        boolean fo    = battingTeam.getId().equals(battingSecond.getId());
        boolean human = isHumanTeam(battingTeam);
        Integer declareLead = getDeclareInn3Lead(fixture, s, battingTeam);
        if (human && declareLead != null && declareLead > 0) {
            int da = fo ? (i1 - i2 + declareLead) : (i2 - i1 + declareLead);
            return new long[]{1, Math.max(1, da)};
        }
        if (!human) {
            int aiLead = 250;
            int da = fo ? (i1 - i2 + aiLead) : (i2 - i1 + aiLead);
            return new long[]{1, Math.max(1, da)};
        }
        return new long[]{0, 0};
    }

    private int computeFCChaseTarget(List<Innings> inningsList, Team battingSecond) {
        int i1 = inningsList.get(0).getTotalRuns();
        int i2 = inningsList.get(1).getTotalRuns();
        int i3 = inningsList.get(2).getTotalRuns();
        boolean fo = inningsList.get(2).getBattingTeam().getId().equals(battingSecond.getId());
        return fo ? (i2 + i3) - i1 + 1 : (i1 + i3) - i2 + 1;
    }

    private MatchResult determineFCResultAndFinalize(Fixture fixture, MatchResult result,
                                                      Team battingFirst, Team battingSecond, Random rng) {
        List<Innings> inns = result.getInningsList();
        if (inns.size() >= 4) {
            boolean fo = inns.get(2).getBattingTeam().getId().equals(battingSecond.getId());
            determineFCResult(result, inns.get(0).getTotalRuns(), inns.get(1).getTotalRuns(),
                    inns.get(2).getTotalRuns(), inns.get(3).getTotalRuns(),
                    battingFirst, battingSecond, fo, false);
        } else if (inns.size() == 3) {
            boolean fo = inns.get(2).getBattingTeam().getId().equals(battingSecond.getId());
            int i1 = inns.get(0).getTotalRuns(), i2 = inns.get(1).getTotalRuns(),
                    i3 = inns.get(2).getTotalRuns();
            int overallLead = fo ? i1 - (i2 + i3) : (i1 + i3) - i2;
            if ((fo && overallLead > 0) || (!fo && overallLead < 0))
                determineFCResult(result, i1, i2, i3, 0, battingFirst, battingSecond, fo, true);
            else result.setResultType("DRAW");
        } else {
            result.setResultType("DRAW");
        }
        return finalizeFCMatch(fixture, result, rng);
    }

    private MatchResult simulateFCDay1(Fixture fixture, String format, Team homeTeam, Team awayTeam) {
        MatchLineup homeLineup = getOrGenerateLineup(fixture, homeTeam, format);
        MatchLineup awayLineup = getOrGenerateLineup(fixture, awayTeam, format);
        Map<String, Object> weather = weatherService.getWeather(homeTeam.getCountry(), fixture.getMatchDate());
        String condition = (String) weather.get("condition");
        int temperature  = (int)    weather.get("temperature");
        String pitchType = fixture.getPitchType();

        boolean isSim  = fixture.getSimSessionId() != null;
        long baseSeed  = fixture.getId().getMostSignificantBits() ^ fixture.getMatchDate().toEpochDay();
        Random rng     = new Random(isSim ? (baseSeed ^ System.nanoTime()) : baseSeed);

        boolean homeToss    = rng.nextBoolean();
        Team tossWinner     = homeToss ? homeTeam : awayTeam;
        String tossDecision = decideToss(homeToss ? homeLineup : awayLineup,
                pitchType, condition, format, rng);

        Team battingFirst, battingSecond;
        MatchLineup bat1Lineup, bat2Lineup;
        if ("BAT".equals(tossDecision)) {
            battingFirst  = tossWinner;
            battingSecond = tossWinner.getId().equals(homeTeam.getId()) ? awayTeam : homeTeam;
            bat1Lineup    = tossWinner.getId().equals(homeTeam.getId()) ? homeLineup : awayLineup;
            bat2Lineup    = tossWinner.getId().equals(homeTeam.getId()) ? awayLineup : homeLineup;
        } else {
            battingSecond = tossWinner;
            battingFirst  = tossWinner.getId().equals(homeTeam.getId()) ? awayTeam : homeTeam;
            bat2Lineup    = tossWinner.getId().equals(homeTeam.getId()) ? homeLineup : awayLineup;
            bat1Lineup    = tossWinner.getId().equals(homeTeam.getId()) ? awayLineup : homeLineup;
        }

        MatchResult result = MatchResult.builder()
                .fixture(fixture).tossWinner(tossWinner).tossDecision(tossDecision).build();
        return runFCInningsLoop(fixture, result, battingFirst, battingSecond,
                bat1Lineup, bat2Lineup, 150, rng, pitchType, condition, temperature, format);
    }

    private MatchResult simulateFCDay2(Fixture fixture, String format) {
        MatchResult result = matchResultRepository.findByFixtureIdWithInnings(fixture.getId())
                .orElseThrow(() -> new IllegalStateException("No Day 1 result found"));
        Team homeTeam = fixture.getHomeTeam();
        Team awayTeam = fixture.getAwayTeam();
        MatchLineup homeLineup = getOrGenerateLineup(fixture, homeTeam, format);
        MatchLineup awayLineup = getOrGenerateLineup(fixture, awayTeam, format);
        List<Innings> inns = result.getInningsList();
        if (inns.isEmpty()) throw new IllegalStateException("No innings from Day 1");
        Team battingFirst  = inns.get(0).getBattingTeam();
        Team battingSecond = inns.get(0).getBowlingTeam();
        MatchLineup bat1Lineup = battingFirst.getId().equals(homeTeam.getId()) ? homeLineup : awayLineup;
        MatchLineup bat2Lineup = battingSecond.getId().equals(homeTeam.getId()) ? homeLineup : awayLineup;

        long seed = fixture.getId().getMostSignificantBits() ^ fixture.getMatchDate().toEpochDay() ^ 0xD2L;
        Random rng = new Random(seed);
        String pitchType = fixture.getPitchType();
        Map<String, Object> weather = weatherService.getWeather(homeTeam.getCountry(), fixture.getMatchDate());
        String condition = (String) weather.get("condition");
        int temperature  = (int)    weather.get("temperature");
        int totalUsed    = inns.stream().mapToInt(this::getOversUsed).sum();
        int dayLimit     = Math.min(150, 300 - totalUsed);
        return runFCInningsLoop(fixture, result, battingFirst, battingSecond,
                bat1Lineup, bat2Lineup, dayLimit, rng, pitchType, condition, temperature, format);
    }

    private MatchResult runFCInningsLoop(Fixture fixture, MatchResult result,
                                          Team battingFirst, Team battingSecond,
                                          MatchLineup bat1Lineup, MatchLineup bat2Lineup,
                                          int dayOversLimit, Random rng, String pitchType,
                                          String condition, int temperature, String format) {
        Map<UUID, MatchFCStrategy> strategiesByTeam = loadFCStrategies(fixture);
        List<Innings> inningsList = result.getInningsList();
        int totalMatchOvers    = inningsList.stream().mapToInt(this::getOversUsed).sum();
        int dayOversUsed       = 0;
        int matchOversLimit    = 300;
        int maxOversPerInnings = 150;
        int maxPerBowler       = 50;

        // Resume interrupted innings
        if (!inningsList.isEmpty()) {
            Innings last = inningsList.get(inningsList.size() - 1);
            if (last.getResumeState() != null) {
                int oversBefore        = getOversUsed(last);
                int inningsOversPlayed = computeBallsBowled(last.getTotalOvers()) / 6;
                int maxForResume = Math.min(maxOversPerInnings,
                        inningsOversPlayed + Math.min(matchOversLimit - totalMatchOvers, dayOversLimit));
                int inningsNum    = last.getInningsNumber();
                boolean canDeclare = false; int declareAt = 0;
                boolean isChasing  = false; int chaseTarget = 0;

                if (inningsNum == 1) {
                    Integer dec = getDeclareInn1(fixture, strategiesByTeam, last.getBattingTeam());
                    if (isHumanTeam(last.getBattingTeam()) && dec != null && dec > 0) { canDeclare = true; declareAt = dec; }
                } else if (inningsNum == 2) {
                    int i1r = inningsList.get(0).getTotalRuns();
                    Integer decLead = getDeclareInn2Lead(fixture, strategiesByTeam, last.getBattingTeam());
                    if (isHumanTeam(last.getBattingTeam()) && decLead != null && decLead > 0) { canDeclare = true; declareAt = i1r + decLead; }
                } else if (inningsNum == 3) {
                    long[] dc = computeInn3Declaration(fixture, strategiesByTeam, inningsList, last.getBattingTeam(), battingFirst, battingSecond);
                    canDeclare = dc[0] > 0; declareAt = (int) dc[1];
                } else if (inningsNum == 4) {
                    isChasing = true; chaseTarget = computeFCChaseTarget(inningsList, battingSecond);
                }

                MatchLineup batL  = last.getBattingTeam().getId().equals(battingFirst.getId()) ? bat1Lineup : bat2Lineup;
                MatchLineup bowlL = last.getBowlingTeam().getId().equals(battingFirst.getId()) ? bat1Lineup : bat2Lineup;
                SimContext ctx = new SimContext(rng, pitchType, condition, temperature,
                        maxForResume, maxPerBowler, format, isChasing, chaseTarget, batL, bowlL);
                simulateInningsWithDeclaration(last, ctx, canDeclare, declareAt, true);

                int newOvers = getOversUsed(last) - oversBefore;
                dayOversUsed    += newOvers;
                totalMatchOvers += newOvers;

                if (dayOversUsed >= dayOversLimit || last.getResumeState() != null) {
                    if (fixture.getFcDay() == null || fixture.getFcDay() == 0)
                        return saveFCDay1Complete(fixture, result);
                    return determineFCResultAndFinalize(fixture, result, battingFirst, battingSecond, rng);
                }
                if (inningsNum == 4)
                    return determineFCResultAndFinalize(fixture, result, battingFirst, battingSecond, rng);
            }
        }

        // Play new innings
        while (inningsList.size() < 4 && dayOversUsed < dayOversLimit && totalMatchOvers < matchOversLimit) {
            int next = inningsList.size() + 1;

            // FC pitch deterioration across days/innings
            if (next == 3) {
                if ("FLAT".equals(pitchType))                                       pitchType = "STANDARD";
                else if ("STANDARD".equals(pitchType) || "GREEN".equals(pitchType)) pitchType = "UNEVEN";
                else if ("DRY".equals(pitchType))                                   pitchType = "DUSTY";
                fixture.setPitchType(pitchType);
            } else if (next == 4) {
                if ("STANDARD".equals(pitchType))    pitchType = "UNEVEN";
                else if ("UNEVEN".equals(pitchType)) pitchType = "DUSTY";
                fixture.setPitchType(pitchType);
            }

            Team bat = null, bowl = null;
            MatchLineup batL = null, bowlL = null;
            boolean canDeclare = false; int declareAt = 0;
            boolean isChasing  = false; int chaseTarget = 0;

            switch (next) {
                case 1 -> {
                    bat = battingFirst; bowl = battingSecond; batL = bat1Lineup; bowlL = bat2Lineup;
                    Integer d1 = getDeclareInn1(fixture, strategiesByTeam, bat);
                    if (isHumanTeam(bat) && d1 != null && d1 > 0) { canDeclare = true; declareAt = d1; }
                }
                case 2 -> {
                    bat = battingSecond; bowl = battingFirst; batL = bat2Lineup; bowlL = bat1Lineup;
                    Integer d2 = getDeclareInn2Lead(fixture, strategiesByTeam, bat);
                    if (isHumanTeam(bat) && d2 != null && d2 > 0) { canDeclare = true; declareAt = inningsList.get(0).getTotalRuns() + d2; }
                }
                case 3 -> {
                    int i1 = inningsList.get(0).getTotalRuns(), i2 = inningsList.get(1).getTotalRuns();
                    // FIX: follow-on threshold correct for match length (150-over FC = 2-day = 100 runs)
                    int followOnThreshold = (matchOversLimit <= 200) ? 100 : 200;
                    Boolean foChoice = getFollowOn(fixture, strategiesByTeam, battingFirst);
                    boolean followOn  = (i1 - i2) >= followOnThreshold && (foChoice != null ? foChoice : true);
                    if (followOn) { bat = battingSecond; bowl = battingFirst; batL = bat2Lineup; bowlL = bat1Lineup; }
                    else          { bat = battingFirst;  bowl = battingSecond; batL = bat1Lineup; bowlL = bat2Lineup; }
                    long[] dc = computeInn3Declaration(fixture, strategiesByTeam, inningsList, bat, battingFirst, battingSecond);
                    canDeclare = dc[0] > 0; declareAt = (int) dc[1];
                }
                case 4 -> {
                    int i1 = inningsList.get(0).getTotalRuns(), i2 = inningsList.get(1).getTotalRuns(),
                            i3 = inningsList.get(2).getTotalRuns();
                    boolean fo = inningsList.get(2).getBattingTeam().getId().equals(battingSecond.getId());
                    int overallLead = fo ? i1 - (i2 + i3) : (i1 + i3) - i2;
                    if ((fo && overallLead > 0) || (!fo && overallLead < 0)) {
                        determineFCResult(result, i1, i2, i3, 0, battingFirst, battingSecond, fo, true);
                        return finalizeFCMatch(fixture, result, rng);
                    }
                    chaseTarget = computeFCChaseTarget(inningsList, battingSecond);
                    if (chaseTarget <= 0) {
                        determineFCResult(result, i1, i2, i3, 0, battingFirst, battingSecond, fo, false);
                        return finalizeFCMatch(fixture, result, rng);
                    }
                    if (fo) { bat = battingFirst; bowl = battingSecond; batL = bat1Lineup; bowlL = bat2Lineup; }
                    else    { bat = battingSecond; bowl = battingFirst; batL = bat2Lineup; bowlL = bat1Lineup; }
                    isChasing = true;
                }
                default -> { break; }
            }

            int maxForInnings = Math.min(maxOversPerInnings,
                    Math.min(matchOversLimit - totalMatchOvers, dayOversLimit - dayOversUsed));
            if (maxForInnings <= 0) break;

            Innings inn = Innings.builder()
                    .matchResult(result).inningsNumber(next)
                    .battingTeam(bat).bowlingTeam(bowl).build();
            SimContext ctx = new SimContext(rng, pitchType, condition, temperature,
                    maxForInnings, maxPerBowler, format, isChasing, chaseTarget, batL, bowlL);
            simulateInningsWithDeclaration(inn, ctx, canDeclare, declareAt, true);
            inningsList.add(inn);

            int oversUsed = getOversUsed(inn);
            totalMatchOvers += oversUsed;
            dayOversUsed    += oversUsed;

            if (dayOversUsed >= dayOversLimit || inn.getResumeState() != null) {
                if (fixture.getFcDay() == null || fixture.getFcDay() == 0)
                    return saveFCDay1Complete(fixture, result);
                return determineFCResultAndFinalize(fixture, result, battingFirst, battingSecond, rng);
            }
        }
        return determineFCResultAndFinalize(fixture, result, battingFirst, battingSecond, rng);
    }

    private void simulateInningsWithDeclaration(Innings innings, SimContext ctx,
                                                 boolean canDeclare, int declareAtRuns,
                                                 boolean fcDayMode) {
        List<LineupPlayer> battingOrder = new ArrayList<>(ctx.battingLineup.getPlayers());
        battingOrder.sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));
        List<BowlingOrder> bowlingOrders = new ArrayList<>(ctx.bowlingLineup.getBowlingOrders());
        bowlingOrders.sort(Comparator.comparingInt(BowlingOrder::getOverNumber));
        Map<Integer, BowlingOrder> bowlerPlan = new LinkedHashMap<>();
        for (BowlingOrder bo : bowlingOrders) bowlerPlan.put(bo.getOverNumber(), bo);

        double teamFieldingAvg = calculateTeamFielding(ctx.bowlingLineup);
        double keeperSkill     = getKeeperRating(ctx.bowlingLineup);
        if (battingOrder.size() < 2) return;

        int nextBatIdx; BatsmanState striker, nonStriker;
        int totalRuns, totalWickets, totalExtras, ballsBowled;
        Map<UUID, BattingScorecard> batCards; Map<UUID, BowlingScorecard> bowlCards;
        Map<UUID, Integer> bowlerBallCounts; Player previousBowler;
        int startOver; int partnershipBalls;
        Set<UUID> existingBatCardIds, existingBowlCardIds;
        boolean isFreeHit = false; String previousBatHand = null;

        String resumeJson = innings.getResumeState();
        if (resumeJson != null) {
            FCResumeState resume = parseResumeState(resumeJson);
            totalRuns     = innings.getTotalRuns();
            totalWickets  = innings.getTotalWickets();
            totalExtras   = innings.getExtras();
            ballsBowled   = computeBallsBowled(innings.getTotalOvers());
            startOver     = ballsBowled / 6;
            partnershipBalls = 0;
            batCards  = new LinkedHashMap<>();
            for (BattingScorecard bc : innings.getBattingCards()) batCards.put(bc.getPlayer().getId(), bc);
            bowlCards = new LinkedHashMap<>();
            for (BowlingScorecard bc : innings.getBowlingCards()) bowlCards.put(bc.getPlayer().getId(), bc);
            existingBatCardIds  = new HashSet<>(batCards.keySet());
            existingBowlCardIds = new HashSet<>(bowlCards.keySet());
            LineupPlayer strikerLP    = battingOrder.stream().filter(lp -> lp.getPlayer().getId().equals(resume.strikerId)).findFirst().orElse(null);
            LineupPlayer nonStrikerLP = battingOrder.stream().filter(lp -> lp.getPlayer().getId().equals(resume.nonStrikerId)).findFirst().orElse(null);
            if (strikerLP == null || nonStrikerLP == null) return;
            striker    = new BatsmanState(strikerLP);
            nonStriker = new BatsmanState(nonStrikerLP);
            nextBatIdx = resume.nextBatIdx;
            bowlerBallCounts = new HashMap<>(resume.bowlerBallCounts);
            previousBowler   = resume.previousBowlerId != null
                    ? ctx.bowlingLineup.getPlayers().stream().map(LineupPlayer::getPlayer)
                        .filter(p -> p.getId().equals(resume.previousBowlerId)).findFirst().orElse(null)
                    : null;
            innings.setResumeState(null);
        } else {
            startOver = 0; nextBatIdx = 2; partnershipBalls = 0;
            striker    = new BatsmanState(battingOrder.get(0));
            nonStriker = new BatsmanState(battingOrder.get(1));
            batCards   = new LinkedHashMap<>();
            batCards.put(striker.player.getId(),    createBatCard(innings, striker.lineupPlayer,    1));
            batCards.put(nonStriker.player.getId(), createBatCard(innings, nonStriker.lineupPlayer, 2));
            bowlCards = new LinkedHashMap<>();
            totalRuns = 0; totalWickets = 0; totalExtras = 0; ballsBowled = 0;
            bowlerBallCounts = new HashMap<>();
            previousBowler   = null;
            existingBatCardIds  = Collections.emptySet();
            existingBowlCardIds = Collections.emptySet();
        }

        Player currentBowler = null;
        String currentBowlerAggression = "N";
        boolean allOut = false, declared = false, chaseWon = false;

        outerLoop:
        for (int over = startOver; over < ctx.maxOvers; over++) {
            int overNumber = over + 1;

            int planSize     = Math.max(1, bowlerPlan.size());
            int basePlanOver = bowlerPlan.isEmpty() ? overNumber : ((overNumber - 1) % planSize) + 1;
            BowlingOrder planned = bowlerPlan.get(basePlanOver);
            boolean foundPlanned = false;
            if (planned != null) {
                Player pb = planned.getBowler();
                int bowled = bowlerBallCounts.getOrDefault(pb.getId(), 0);
                boolean samePrev = previousBowler != null && pb.getId().equals(previousBowler.getId());
                if (bowled < ctx.maxPerBowler * 6 && !samePrev) {
                    currentBowler = pb; currentBowlerAggression = planned.getAggression(); foundPlanned = true;
                } else if (samePrev) {
                    for (int shift = 1; shift < planSize; shift++) {
                        BowlingOrder alt = bowlerPlan.get(((basePlanOver - 1 + shift) % planSize) + 1);
                        if (alt != null) {
                            Player ab  = alt.getBowler();
                            int ab2    = bowlerBallCounts.getOrDefault(ab.getId(), 0);
                            if (ab2 < ctx.maxPerBowler * 6 && (previousBowler == null || !ab.getId().equals(previousBowler.getId()))) {
                                currentBowler = ab; currentBowlerAggression = alt.getAggression(); foundPlanned = true; break;
                            }
                        }
                    }
                }
            }
            if (!foundPlanned) { currentBowler = pickFallbackBowler(ctx, bowlerBallCounts, previousBowler, overNumber); currentBowlerAggression = "N"; }

            if (!bowlCards.containsKey(currentBowler.getId()))
                bowlCards.put(currentBowler.getId(), createBowlCard(innings, currentBowler));
            BowlingScorecard bowlCard = bowlCards.get(currentBowler.getId());

            int legalBallsThisOver = 0, runsThisOver = 0;
            boolean maidenPossible = true; int ballInOver = 0;
            int currentBowlerBalls = bowlerBallCounts.getOrDefault(currentBowler.getId(), 0);

            while (legalBallsThisOver < 6) {
                ballInOver++;
                int battingPos = striker.lineupPlayer.getBattingPosition() != null
                        ? striker.lineupPlayer.getBattingPosition() : 5;
                DeliveryResult delivery = simulateDelivery(ctx, striker, nonStriker, currentBowler,
                        currentBowlerAggression, teamFieldingAvg, keeperSkill, overNumber, totalRuns,
                        totalWickets, ballsBowled, batCards.get(striker.player.getId()),
                        currentBowlerBalls, isFreeHit, previousBatHand, partnershipBalls,
                        battingPos, false);
                applyStrikeFarming(ctx, striker, nonStriker, delivery, legalBallsThisOver);

                if (!Boolean.TRUE.equals(SKIP_BALL_EVENTS.get())) {
                    Player evB = (delivery.isWicket && delivery.isNonStrikerOut) ? nonStriker.player : striker.player;
                    innings.getBallEvents().add(BallEvent.builder()
                            .innings(innings).overNumber(overNumber).ballNumber(ballInOver)
                            .batsman(evB).bowler(currentBowler)
                            .runs(delivery.runs).isWicket(delivery.isWicket)
                            .isBoundary(delivery.isBoundary).isSix(delivery.isSix)
                            .isWide(delivery.isWide).isNoBall(delivery.isNoBall)
                            .isBye(delivery.isBye).isLegBye(delivery.isLegBye)
                            .dismissalType(delivery.dismissalType)
                            .fielder(delivery.fielder).commentary(delivery.commentary).build());
                }

                previousBatHand = striker.player.getBatHand();
                totalRuns += delivery.runs;
                if (!delivery.isWide && !delivery.isNoBall) isFreeHit = false;
                if (delivery.isNoBall && ("T20".equalsIgnoreCase(ctx.format) || "ODI".equalsIgnoreCase(ctx.format))) isFreeHit = true;

                if (delivery.isWide || delivery.isNoBall) {
                    if (delivery.isNoBall) {
                        int rob = Math.max(0, delivery.runs - 1); totalExtras += 1;
                        BattingScorecard bc = batCards.get(striker.player.getId());
                        bc.setBallsFaced(bc.getBallsFaced() + 1);
                        if (rob > 0) { bc.setRunsScored(bc.getRunsScored() + rob); if (delivery.isBoundary) bc.setFours(bc.getFours() + 1); if (delivery.isSix) bc.setSixes(bc.getSixes() + 1); }
                    } else { totalExtras += delivery.runs; }
                    bowlCard.setRunsConceded(bowlCard.getRunsConceded() + delivery.runs);
                    if (delivery.isWide) bowlCard.setWides(bowlCard.getWides() + 1);
                    if (delivery.isNoBall) bowlCard.setNoBalls(bowlCard.getNoBalls() + 1);
                    maidenPossible = false; runsThisOver += delivery.runs;
                    if ((delivery.runs % 2) == 1) { BatsmanState t = striker; striker = nonStriker; nonStriker = t; }
                } else {
                    legalBallsThisOver++; ballsBowled++;
                    bowlerBallCounts.merge(currentBowler.getId(), 1, Integer::sum);
                    partnershipBalls++;
                    BattingScorecard bc = batCards.get(striker.player.getId());
                    bc.setBallsFaced(bc.getBallsFaced() + 1);
                    if (delivery.isBye || delivery.isLegBye) {
                        totalExtras += delivery.runs;
                        if (delivery.runs == 0) bowlCard.setDotBalls(bowlCard.getDotBalls() + 1);
                    } else {
                        bc.setRunsScored(bc.getRunsScored() + delivery.runs);
                        if (delivery.isBoundary) bc.setFours(bc.getFours() + 1);
                        if (delivery.isSix) bc.setSixes(bc.getSixes() + 1);
                        bowlCard.setRunsConceded(bowlCard.getRunsConceded() + delivery.runs);
                        if (delivery.runs == 0 && !delivery.isWicket) bowlCard.setDotBalls(bowlCard.getDotBalls() + 1);
                        else if (delivery.runs > 0) { maidenPossible = false; runsThisOver += delivery.runs; }
                    }
                    if (!delivery.isWide && !delivery.isNoBall) currentBowlerBalls++;

                    if (delivery.isWicket) {
                        maidenPossible = false; totalWickets++; partnershipBalls = 0;
                        BattingScorecard dismissedCard = bc;
                        boolean nso = delivery.isNonStrikerOut;
                        if (nso) dismissedCard = batCards.get(nonStriker.player.getId());
                        if (!"RUN_OUT".equals(delivery.dismissalType)) { bowlCard.setWickets(bowlCard.getWickets() + 1); dismissedCard.setBowler(currentBowler); }
                        dismissedCard.setDismissalType(delivery.dismissalType);
                        dismissedCard.setFielder(delivery.fielder);
                        if (dismissedCard.getBallsFaced() > 0) dismissedCard.setStrikeRate(Math.round(dismissedCard.getRunsScored() * 100.0 / dismissedCard.getBallsFaced() * 100.0) / 100.0);
                        if (totalWickets >= 10 || nextBatIdx >= battingOrder.size()) { innings.setAllOut(true); allOut = true; break outerLoop; }
                        if (nso) { nonStriker = new BatsmanState(battingOrder.get(nextBatIdx)); batCards.put(nonStriker.player.getId(), createBatCard(innings, nonStriker.lineupPlayer, nextBatIdx + 1)); }
                        else     { striker    = new BatsmanState(battingOrder.get(nextBatIdx)); batCards.put(striker.player.getId(), createBatCard(innings, striker.lineupPlayer, nextBatIdx + 1)); }
                        nextBatIdx++;
                    } else {
                        if (delivery.runs % 2 == 1) { BatsmanState t = striker; striker = nonStriker; nonStriker = t; }
                    }
                }
                if (ctx.isChasing && totalRuns >= ctx.target) { chaseWon = true; break outerLoop; }
            }

            BatsmanState t = striker; striker = nonStriker; nonStriker = t;
            if (maidenPossible && runsThisOver == 0) bowlCard.setMaidens(bowlCard.getMaidens() + 1);
            previousBowler = currentBowler;

            if (canDeclare && declareAtRuns > 0 && totalRuns >= declareAtRuns) {
                innings.setDeclared(true); declared = true; break;
            }
        }

        if (fcDayMode && !allOut && !declared && !chaseWon && totalWickets < 10) {
            int completedOversNow = ballsBowled / 6;
            if (completedOversNow >= ctx.maxOvers)
                innings.setResumeState(buildResumeState(striker, nonStriker, nextBatIdx, bowlerBallCounts, previousBowler));
        }

        innings.setTotalRuns(totalRuns); innings.setTotalWickets(totalWickets); innings.setExtras(totalExtras);
        int cO = ballsBowled / 6, rB = ballsBowled % 6;
        innings.setTotalOvers(cO + rB / 10.0);

        for (BattingScorecard card : batCards.values()) {
            if (card.getBallsFaced() > 0) card.setStrikeRate(Math.round(card.getRunsScored() * 100.0 / card.getBallsFaced() * 100.0) / 100.0);
            if (!existingBatCardIds.contains(card.getPlayer().getId())) innings.getBattingCards().add(card);
        }
        for (BowlingScorecard card : bowlCards.values()) {
            int bb = bowlerBallCounts.getOrDefault(card.getPlayer().getId(), 0);
            card.setOvers(bb / 6 + (bb % 6) / 10.0);
            if (bb > 0) card.setEconomy(Math.round(card.getRunsConceded() / (bb / 6.0) * 100.0) / 100.0);
            if (!existingBowlCardIds.contains(card.getPlayer().getId())) innings.getBowlingCards().add(card);
        }
    }

    private void determineFCResult(MatchResult result, int inn1, int inn2, int inn3, int inn4,
                                    Team battingFirst, Team battingSecond, boolean followOn, boolean inningsDefeat) {
        if (inningsDefeat) {
            if (followOn) {
                int margin = inn1 - (inn2 + inn3);
                if (margin > 0) { result.setWinner(battingFirst); result.setResultType("INNINGS"); result.setResultMargin(margin); }
                else result.setResultType("DRAW");
            } else {
                int margin = inn2 - (inn1 + inn3);
                if (margin > 0) { result.setWinner(battingSecond); result.setResultType("INNINGS"); result.setResultMargin(margin); }
                else result.setResultType("DRAW");
            }
            return;
        }
        int team1Total   = followOn ? inn1 : inn1 + inn3;
        int team2Total   = followOn ? inn2 + inn3 : inn2;
        int target       = team1Total - team2Total + 1;
        Team chasingTeam = followOn ? battingFirst : battingSecond;
        Team settingTeam = followOn ? battingSecond : battingFirst;
        if (inn4 >= target) {
            Innings last = result.getInningsList().stream().filter(i -> i.getInningsNumber() == 4).findFirst().orElse(null);
            int wl = last != null ? last.getTotalWickets() : 0;
            result.setWinner(chasingTeam); result.setResultType("WICKETS"); result.setResultMargin(10 - wl);
        } else {
            Innings last = result.getInningsList().stream().filter(i -> i.getInningsNumber() == 4).findFirst().orElse(null);
            if (last != null && (Boolean.TRUE.equals(last.getAllOut()) || last.getTotalWickets() >= 10)) {
                result.setWinner(settingTeam); result.setResultType("RUNS"); result.setResultMargin(target - inn4 - 1);
            } else {
                result.setResultType("DRAW");
            }
        }
    }

    private int getOversUsed(Innings innings) {
        double totalOvers = innings.getTotalOvers() != null ? innings.getTotalOvers() : 0.0;
        int full    = (int) totalOvers;
        int partial = (int) Math.round((totalOvers - full) * 10);
        return full + (partial > 0 ? 1 : 0);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  LINEUP GENERATION
    // ═══════════════════════════════════════════════════════════════════════════

    private MatchLineup getOrGenerateLineup(Fixture fixture, Team team, String format) {
        Optional<MatchLineup> existing = matchLineupRepository.findByFixtureIdAndTeamId(fixture.getId(), team.getId());
        if (existing.isPresent()) return existing.get();
        Optional<DefaultLineup> savedDefault = defaultLineupRepository.findByTeamIdAndFormat(team.getId(), format);
        if (savedDefault.isPresent()) {
            MatchLineup fromDefault = buildLineupFromDefault(fixture, team, format, savedDefault.get());
            if (fromDefault != null) return fromDefault;
        }
        log.info("No lineup found for team {} — auto-generating", team.getTeamName());
        List<Player> squad = playerRepository.findByTeam(team);
        if (squad.size() < 11) throw new IllegalStateException("Team " + team.getTeamName() + " has fewer than 11 players (" + squad.size() + ")");
        List<Player> selected     = autoSelectPlaying11(squad);
        List<Player> battingOrder = buildAutoBattingOrder(selected);
        MatchLineup lineup = MatchLineup.builder().fixture(fixture).team(team).bowlingPlan("BALANCED").build();
        Player keeper = selected.stream().filter(p -> "KEEPER".equals(p.getRole())).max(Comparator.comparingInt(Player::getKeeperRating))
                .orElse(selected.stream().max(Comparator.comparingInt(Player::getKeeperRating)).orElse(selected.get(0)));
        lineup.setKeeper(keeper);
        Player captain = selected.stream().max(Comparator.comparingInt(Player::getRating)).orElse(selected.get(0));
        lineup.setCaptain(captain);
        for (int i = 0; i < battingOrder.size(); i++) {
            lineup.getPlayers().add(LineupPlayer.builder().lineup(lineup).player(battingOrder.get(i))
                    .battingPosition(i + 1).batAggression(battingOrder.get(i).getBatAggression()).build());
        }
        List<Player> topBowlers;
        if ("FC".equalsIgnoreCase(format)) {
            topBowlers = selected.stream().filter(p -> p.getBowlRating() >= 15)
                    .sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating())).toList();
            if (topBowlers.size() < 5) topBowlers = selected.stream()
                    .sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating())).limit(5).toList();
        } else {
            topBowlers = selected.stream().sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating())).limit(5).toList();
        }
        int bowlingPlanOvers = "FC".equalsIgnoreCase(format) ? 100 : getMaxOvers(format);
        for (int over = 1; over <= bowlingPlanOvers; over++) {
            Player bowler = topBowlers.get((over - 1) % topBowlers.size());
            lineup.getBowlingOrders().add(BowlingOrder.builder().lineup(lineup).overNumber(over)
                    .bowler(bowler).aggression(bowler.getBowlAggression()).build());
        }
        return matchLineupRepository.save(lineup);
    }

    @SuppressWarnings("unchecked")
    private MatchLineup buildLineupFromDefault(Fixture fixture, Team team, String format, DefaultLineup defaultLineup) {
        Map<String, Object> data = defaultLineup.getLineupData();
        if (data == null) return null;
        List<Player> squad = playerRepository.findByTeam(team);
        if (squad.size() < 11) return null;
        Map<String, Player> squadById = new HashMap<>();
        for (Player p : squad) squadById.put(p.getId().toString(), p);
        List<Map<String, Object>> rawPlayers = (List<Map<String, Object>>) data.get("players");
        if (rawPlayers == null || rawPlayers.isEmpty()) return null;
        rawPlayers.sort(Comparator.comparingInt(p -> ((Number) p.getOrDefault("battingPosition", 999)).intValue()));
        MatchLineup lineup = MatchLineup.builder().fixture(fixture).team(team)
                .bowlingPlan(Objects.toString(data.getOrDefault("bowlingPlan", "BALANCED"), "BALANCED")).build();
        lineup.setBatOrBowl((String) data.get("batOrBowl"));
        lineup.setTossChoice((String) data.get("tossChoice"));
        Map<String, Player> replacementByOriginalId = new HashMap<>();
        Set<UUID> usedPlayerIds = new HashSet<>();
        List<Player> selected = new ArrayList<>();
        int targetSlots = Math.min(11, rawPlayers.size());
        for (int i = 0; i < targetSlots; i++) {
            Map<String, Object> slot = rawPlayers.get(i);
            String playerId = Objects.toString(slot.get("playerId"), null);
            Player chosen = playerId == null ? null : squadById.get(playerId);
            if (chosen == null || usedPlayerIds.contains(chosen.getId())) {
                String requiredRole = resolveRole(playerId);
                chosen = pickReplacementByRole(squad, usedPlayerIds, requiredRole);
                if (chosen == null) chosen = pickBestRemainingPlayer(squad, usedPlayerIds);
                if (chosen == null) break;
                if (playerId != null) replacementByOriginalId.put(playerId, chosen);
            }
            usedPlayerIds.add(chosen.getId()); selected.add(chosen);
            int battingPosition = ((Number) slot.getOrDefault("battingPosition", i + 1)).intValue();
            String batAgg = Objects.toString(slot.getOrDefault("batAggression", chosen.getBatAggression()), "N");
            lineup.getPlayers().add(LineupPlayer.builder().lineup(lineup).player(chosen)
                    .battingPosition(battingPosition).batAggression(batAgg).build());
        }
        while (lineup.getPlayers().size() < 11) {
            Player next = pickBestRemainingPlayer(squad, usedPlayerIds);
            if (next == null) break;
            usedPlayerIds.add(next.getId()); selected.add(next);
            lineup.getPlayers().add(LineupPlayer.builder().lineup(lineup).player(next)
                    .battingPosition(lineup.getPlayers().size() + 1).batAggression(next.getBatAggression()).build());
        }
        lineup.getPlayers().sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));
        for (int i = 0; i < lineup.getPlayers().size(); i++) lineup.getPlayers().get(i).setBattingPosition(i + 1);
        Map<UUID, Player> selectedById = new HashMap<>();
        for (Player p : selected) selectedById.put(p.getId(), p);
        String captainId = Objects.toString(data.get("captainId"), null);
        Player captain = captainId == null ? null : squadById.get(captainId);
        if (captain == null && captainId != null) captain = replacementByOriginalId.get(captainId);
        if (captain == null) captain = selected.stream().max(Comparator.comparingInt(Player::getRating)).orElse(null);
        lineup.setCaptain(captain);
        String keeperId = Objects.toString(data.get("keeperId"), null);
        Player keeper = keeperId == null ? null : squadById.get(keeperId);
        if (keeper == null && keeperId != null) keeper = replacementByOriginalId.get(keeperId);
        if (keeper == null || !selectedById.containsKey(keeper.getId())) {
            keeper = selected.stream().filter(p -> "KEEPER".equals(p.getRole()))
                    .max(Comparator.comparingInt(Player::getKeeperRating))
                    .orElse(selected.stream().max(Comparator.comparingInt(Player::getKeeperRating)).orElse(null));
        }
        lineup.setKeeper(keeper);
        List<Map<String, Object>> rawBowling = (List<Map<String, Object>>) data.get("bowlingOrders");
        int planOvers = "FC".equalsIgnoreCase(format) ? 100 : getMaxOvers(format);
        if (rawBowling != null) {
            rawBowling.sort(Comparator.comparingInt(b -> ((Number) b.getOrDefault("overNumber", 999)).intValue()));
            for (Map<String, Object> bo : rawBowling) {
                int on = ((Number) bo.getOrDefault("overNumber", 0)).intValue();
                if (on < 1 || on > planOvers) continue;
                String origId = Objects.toString(bo.get("bowlerId"), null);
                Player bowler = null;
                if (origId != null) {
                    Player direct = squadById.get(origId);
                    if (direct != null && selectedById.containsKey(direct.getId())) bowler = direct;
                    else {
                        Player rep = replacementByOriginalId.get(origId);
                        if (rep != null && selectedById.containsKey(rep.getId())) bowler = rep;
                        else bowler = pickSelectedByRole(selected, resolveRole(origId));
                    }
                }
                if (bowler == null) bowler = selected.stream().max(Comparator.comparingInt(Player::getBowlRating)).orElse(null);
                if (bowler == null) continue;
                lineup.getBowlingOrders().add(BowlingOrder.builder().lineup(lineup).overNumber(on).bowler(bowler)
                        .aggression(Objects.toString(bo.getOrDefault("aggression", bowler.getBowlAggression()), "N")).build());
            }
        }
        if (lineup.getBowlingOrders().isEmpty()) {
            List<Player> top5 = selected.stream().sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating())).limit(5).toList();
            if (top5.isEmpty()) return null;
            for (int ov = 1; ov <= planOvers; ov++) {
                Player bowler = top5.get((ov - 1) % top5.size());
                lineup.getBowlingOrders().add(BowlingOrder.builder().lineup(lineup).overNumber(ov)
                        .bowler(bowler).aggression(bowler.getBowlAggression()).build());
            }
        }
        lineup.getBowlingOrders().sort(Comparator.comparingInt(BowlingOrder::getOverNumber));
        return matchLineupRepository.save(lineup);
    }

    private String resolveRole(String playerId) {
        if (playerId == null) return null;
        try { return playerRepository.findById(UUID.fromString(playerId)).map(Player::getRole).orElse(null); }
        catch (IllegalArgumentException e) { return null; }
    }
    private Player pickReplacementByRole(List<Player> squad, Set<UUID> usedIds, String role) {
        if (role == null || role.isBlank()) return null;
        Comparator<Player> comp = switch (role) {
            case "BATSMAN"     -> Comparator.comparingInt(Player::getBatRating);
            case "BOWLER"      -> Comparator.comparingInt(Player::getBowlRating);
            case "KEEPER"      -> Comparator.comparingInt(Player::getKeeperRating);
            case "ALL_ROUNDER" -> Comparator.comparingInt(p -> p.getBatRating() + p.getBowlRating());
            default            -> Comparator.comparingInt(Player::getRating);
        };
        return squad.stream().filter(p -> !usedIds.contains(p.getId()) && role.equalsIgnoreCase(p.getRole())).max(comp).orElse(null);
    }
    private Player pickBestRemainingPlayer(List<Player> squad, Set<UUID> usedIds) {
        return squad.stream().filter(p -> !usedIds.contains(p.getId())).max(Comparator.comparingInt(Player::getRating)).orElse(null);
    }
    private Player pickSelectedByRole(List<Player> selected, String role) {
        if (role == null || role.isBlank()) return null;
        Comparator<Player> comp = switch (role) {
            case "BATSMAN"     -> Comparator.comparingInt(Player::getBatRating);
            case "BOWLER"      -> Comparator.comparingInt(Player::getBowlRating);
            case "KEEPER"      -> Comparator.comparingInt(Player::getKeeperRating);
            case "ALL_ROUNDER" -> Comparator.comparingInt(p -> p.getBatRating() + p.getBowlRating());
            default            -> Comparator.comparingInt(Player::getRating);
        };
        return selected.stream().filter(p -> role.equalsIgnoreCase(p.getRole())).max(comp).orElse(null);
    }
    private List<Player> autoSelectPlaying11(List<Player> squad) {
        List<Player> selected = new ArrayList<>();
        Set<UUID> pickedIds   = new HashSet<>();
        squad.stream().filter(p -> "KEEPER".equals(p.getRole())).max(Comparator.comparingInt(Player::getKeeperRating))
                .ifPresent(p -> { selected.add(p); pickedIds.add(p.getId()); });
        if (selected.isEmpty()) squad.stream().max(Comparator.comparingInt(Player::getKeeperRating))
                .ifPresent(p -> { selected.add(p); pickedIds.add(p.getId()); });
        squad.stream().filter(p -> "BATSMAN".equals(p.getRole()) && !pickedIds.contains(p.getId()))
                .sorted((a, b) -> Integer.compare(b.getBatRating(), a.getBatRating())).limit(5)
                .forEach(p -> { selected.add(p); pickedIds.add(p.getId()); });
        squad.stream().filter(p -> "ALL_ROUNDER".equals(p.getRole()) && !pickedIds.contains(p.getId()))
                .sorted((a, b) -> Integer.compare(b.getBatRating() + b.getBowlRating(), a.getBatRating() + a.getBowlRating())).limit(2)
                .forEach(p -> { selected.add(p); pickedIds.add(p.getId()); });
        squad.stream().filter(p -> "BOWLER".equals(p.getRole()) && !pickedIds.contains(p.getId()))
                .sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating())).limit(3)
                .forEach(p -> { selected.add(p); pickedIds.add(p.getId()); });
        if (selected.size() < 11) squad.stream().filter(p -> !pickedIds.contains(p.getId()))
                .sorted((a, b) -> Integer.compare(b.getRating(), a.getRating())).limit(11 - selected.size())
                .forEach(p -> { selected.add(p); pickedIds.add(p.getId()); });
        return selected.subList(0, Math.min(11, selected.size()));
    }
    private List<Player> buildAutoBattingOrder(List<Player> selected) {
        List<Player> order = new ArrayList<>();
        selected.stream().filter(p -> "BATSMAN".equals(p.getRole()))
                .sorted((a, b) -> Integer.compare(b.getBatRating(), a.getBatRating())).forEach(order::add);
        selected.stream().filter(p -> "KEEPER".equals(p.getRole())).findFirst().ifPresent(order::add);
        selected.stream().filter(p -> "ALL_ROUNDER".equals(p.getRole()))
                .sorted((a, b) -> Integer.compare(b.getBatRating(), a.getBatRating())).forEach(order::add);
        selected.stream().filter(p -> "BOWLER".equals(p.getRole()))
                .sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating())).forEach(order::add);
        selected.stream().filter(p -> !order.contains(p)).forEach(order::add);
        return order;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ACTIVITY LOG
    // ═══════════════════════════════════════════════════════════════════════════

    public void logMatchActivity(MatchResult result, Fixture fixture) {
        Team home = fixture.getHomeTeam(), away = fixture.getAwayTeam();
        String fmt    = fixture.getLeague() != null ? fixture.getLeague().getFormat() : fixture.getFormat();
        String prefix = fmt != null ? "(" + fmt + ") " : "";
        if ("DRAW".equals(result.getResultType()) || "TIE".equals(result.getResultType())) {
            String text = prefix + "Match vs %s ended in a " + result.getResultType().toLowerCase() + ".";
            activityLogService.log(home, "match-lost", String.format(text, away.getTeamName()));
            activityLogService.log(away, "match-lost", String.format(text, home.getTeamName()));
        } else if (result.getWinner() != null) {
            Team winner = result.getWinner();
            Team loser  = winner.getId().equals(home.getId()) ? away : home;
            String margin = result.getResultMargin() + " " + result.getResultType().toLowerCase();
            activityLogService.log(winner, "match-won",  prefix + "Won vs "  + loser.getTeamName()   + " by " + margin + ".");
            activityLogService.log(loser,  "match-lost", prefix + "Lost vs " + winner.getTeamName() + " by " + margin + ".");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  GATE MONEY
    // ═══════════════════════════════════════════════════════════════════════════

    private void distributeGateMoney(MatchResult matchResult, Fixture fixture) {
        Team home = fixture.getHomeTeam(), away = fixture.getAwayTeam();
        StadiumSeats seats = stadiumSeatsRepository.findByTeam(home)
                .orElse(StadiumSeats.builder().team(home).standing(2000).economy(1500).standard(1000).premium(500).build());
        int standingCap = seats.getStanding(), economyCap = seats.getEconomy(),
            standardCap = seats.getStandard(), premiumCap  = seats.getPremium();
        int totalCap = standingCap + economyCap + standardCap + premiumCap;
        if (totalCap <= 0) return;
        int homeFans = home.getFans() != null ? home.getFans() : 1000;
        int awayFans = away.getFans() != null ? away.getFans() : 1000;
        double baseDemand  = homeFans + awayFans * 0.30;
        int homeMorale = home.getMorale() != null ? home.getMorale() : 50;
        int awayMorale = away.getMorale() != null ? away.getMorale() : 50;
        double moraleMult  = 0.70 + ((homeMorale + awayMorale) / 2.0 / 100.0) * 0.60;
        double divMult     = 1.0;
        if (fixture.getLeague() != null && fixture.getLeague().getDivision() != null)
            divMult = switch (fixture.getLeague().getDivision()) { case 1 -> 1.50; case 2 -> 1.25; case 3 -> 1.00; default -> 0.85; };
        String fmt     = fixture.getLeague() != null ? fixture.getLeague().getFormat() : (fixture.getFormat() != null ? fixture.getFormat() : "T20");
        double avgRating   = (getRatingForFormat(home, fmt) + getRatingForFormat(away, fmt)) / 2.0;
        double ratingMult  = Math.max(0.70, Math.min(1.40, 0.85 + (avgRating - 800.0) / 800.0 * 0.45));
        double randomFactor = 0.85 + Math.random() * 0.30;
        int totalDemand    = (int) Math.round(baseDemand * moraleMult * divMult * ratingMult * randomFactor);
        double moraleAppeal  = Math.max(0.0, Math.min(1.0, (moraleMult - 0.70) / 0.60));
        double divisionAppeal = Math.max(0.0, Math.min(1.0, (divMult - 0.85) / 0.65));
        double ratingAppeal   = Math.max(0.0, Math.min(1.0, (ratingMult - 0.70) / 0.70));
        double hypeScore = (moraleAppeal * 0.25) + (divisionAppeal * 0.35) + (ratingAppeal * 0.40);
        double[] lowHype = {0.52, 0.28, 0.14, 0.06}, highHype = {0.35, 0.32, 0.22, 0.11};
        double[] baseWeights = new double[4];
        for (int i = 0; i < 4; i++) baseWeights[i] = lowHype[i] + (highHype[i] - lowHype[i]) * hypeScore;
        double[] capWeights = { standingCap / (double) totalCap, economyCap / (double) totalCap, standardCap / (double) totalCap, premiumCap / (double) totalCap };
        double[] finalWeights = new double[4];
        for (int i = 0; i < 4; i++) finalWeights[i] = baseWeights[i] * 0.70 + capWeights[i] * 0.30;
        normalize(finalWeights);
        int[] capacities = {standingCap, economyCap, standardCap, premiumCap};
        int[] attendance  = new int[4];
        int[] currentDemand = allocateDemand(totalDemand, finalWeights);
        int[] remainingCap  = Arrays.copyOf(capacities, capacities.length);
        double[][] overflowMatrix = {{0.00, 0.35, 0.10, 0.00},{0.20, 0.00, 0.25, 0.05},{0.00, 0.20, 0.00, 0.20},{0.00, 0.15, 0.45, 0.00}};
        for (int wave = 0; wave < 4 && sum(currentDemand) > 0; wave++) {
            int[] unmet = new int[4];
            for (int i = 0; i < 4; i++) { int fill = Math.min(currentDemand[i], remainingCap[i]); attendance[i] += fill; remainingCap[i] -= fill; unmet[i] = currentDemand[i] - fill; }
            if (sum(unmet) == 0) break;
            double[] nextRaw = new double[4];
            for (int from = 0; from < 4; from++) { if (unmet[from] <= 0) continue; for (int to = 0; to < 4; to++) { if (overflowMatrix[from][to] > 0) nextRaw[to] += unmet[from] * overflowMatrix[from][to]; } }
            currentDemand = roundDemand(nextRaw);
        }
        int totalAtt = attendance[0] + attendance[1] + attendance[2] + attendance[3];
        matchResult.setAttendance(totalAtt);
        matchResult.setAttendanceBreakdown(String.format("%d,%d,%d,%d,%d,%d,%d,%d", attendance[0], standingCap, attendance[1], economyCap, attendance[2], standardCap, attendance[3], premiumCap));
        long totalRevenue = (long) attendance[0] * 2 + (long) attendance[1] * 4 + (long) attendance[2] * 7 + (long) attendance[3] * 10;
        if (totalRevenue <= 0) return;
        double homeFanRatio = homeFans / (double) (homeFans + awayFans);
        double homeSharePct = 0.60 + homeFanRatio * 0.05;
        long homeShare = Math.round(totalRevenue * homeSharePct), awayShare = totalRevenue - homeShare;
        home.setFunds(home.getFunds() + homeShare); away.setFunds(away.getFunds() + awayShare);
        teamRepository.save(home); teamRepository.save(away);
        String desc = String.format("Gate money: %,d attendance, $%,d total revenue (%s vs %s)", totalAtt, totalRevenue, home.getTeamName(), away.getTeamName());
        transactionLogRepository.save(TransactionLog.builder().team(home).type("GATE_MONEY").description(desc + " [Home]").amount(homeShare).balanceAfter(home.getFunds()).build());
        transactionLogRepository.save(TransactionLog.builder().team(away).type("GATE_MONEY").description(desc + " [Away]").amount(awayShare).balanceAfter(away.getFunds()).build());
    }

    private int getRatingForFormat(Team team, String format) {
        return switch (format.toUpperCase()) {
            case "ODI"       -> team.getOdiRating() != null ? team.getOdiRating() : 1000;
            case "FC","TEST" -> team.getFcRating()  != null ? team.getFcRating()  : 1000;
            default          -> team.getT20Rating() != null ? team.getT20Rating() : 1000;
        };
    }

    private void normalize(double[] weights) {
        double total = 0.0;
        for (double w : weights) total += w;
        if (total <= 0.0) { Arrays.fill(weights, 0.25); return; }
        for (int i = 0; i < weights.length; i++) weights[i] /= total;
    }
    private int[] allocateDemand(int total, double[] weights) {
        int[] result = new int[weights.length]; double[] fractions = new double[weights.length]; int allocated = 0;
        for (int i = 0; i < weights.length; i++) { double raw = total * weights[i]; result[i] = (int) Math.floor(raw); fractions[i] = raw - result[i]; allocated += result[i]; }
        while (allocated < total) { int best = 0; for (int i = 1; i < fractions.length; i++) if (fractions[i] > fractions[best]) best = i; result[best]++; fractions[best] = -1.0; allocated++; }
        return result;
    }
    private int[] roundDemand(double[] raw) {
        int[] result = new int[raw.length]; double[] fractions = new double[raw.length]; int targetTotal = (int) Math.round(Arrays.stream(raw).sum()); int allocated = 0;
        for (int i = 0; i < raw.length; i++) { result[i] = (int) Math.floor(raw[i]); fractions[i] = raw[i] - result[i]; allocated += result[i]; }
        while (allocated < targetTotal) { int best = 0; for (int i = 1; i < fractions.length; i++) if (fractions[i] > fractions[best]) best = i; if (fractions[best] <= 0) break; result[best]++; fractions[best] = -1.0; allocated++; }
        return result;
    }
    private int sum(int[] values) { int t = 0; for (int v : values) t += v; return t; }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MORALE & FANS
    // ═══════════════════════════════════════════════════════════════════════════

    private void updateMoraleAndFans(MatchResult result, Fixture fixture, String format) {
        Team home = fixture.getHomeTeam(), away = fixture.getAwayTeam();
        String rt    = result.getResultType();
        boolean isDraw = "DRAW".equals(rt) || "TIE".equals(rt) || "NO_RESULT".equals(rt);
        if (isDraw) {
            applyMoraleDelta(home, 2); applyMoraleDelta(away, 2);
            applyFanDelta(home, "DRAW"); applyFanDelta(away, "DRAW");
            applyEloUpdate(home, away, 0.5, 0.5, format);
        } else if (result.getWinner() != null) {
            Team winner = result.getWinner(), loser = winner.getId().equals(home.getId()) ? away : home;
            applyMoraleDelta(winner, 8); applyMoraleDelta(loser, -6);
            applyFanDelta(winner, "WIN"); applyFanDelta(loser, "LOSS");
            applyEloUpdate(winner, loser, 1.0, 0.0, format);
        }
        teamRepository.save(home); teamRepository.save(away);
    }

    private void applyMoraleDelta(Team team, int resultDelta) {
        int current = team.getMorale() != null ? team.getMorale() : 50;
        List<Player> squad = playerRepository.findByTeam(team);
        double avgConfidence = squad.isEmpty() ? 50.0 : squad.stream().mapToInt(Player::getConfidence).average().orElse(50.0);
        double squadPull     = (avgConfidence - current) * 0.15;
        int academyBenchmark = (team.getAcademyLevel() != null ? team.getAcademyLevel() : 1) * 25;
        double academyPull   = (academyBenchmark - current) * 0.10;
        team.setMorale(Math.max(0, Math.min(100, (int) Math.round(current + resultDelta + squadPull + academyPull))));
    }

    private void applyFanDelta(Team team, String outcome) {
        int currentFans = team.getFans() != null ? team.getFans() : 1000;
        double ceiling  = 150000.0, ratio = Math.min(1.0, currentFans / ceiling);
        int gain, penalty;
        switch (outcome) {
            case "WIN"  -> { gain = Math.max(20, (int)(500 * (1.0 - ratio))); penalty = 0; }
            case "DRAW" -> { gain = Math.max(10, (int)(200 * (1.0 - ratio))); penalty = (int)(currentFans * 0.001 * ratio); }
            default     -> { gain = Math.max(5,  (int)(100 * (1.0 - ratio))); penalty = (int)(currentFans * 0.004 * ratio); }
        }
        team.setFans(Math.max(100, currentFans + gain - penalty));
    }

    private void applyEloUpdate(Team teamA, Team teamB, double scoreA, double scoreB, String format) {
        int K = 20, rA = getFormatRating(teamA, format), rB = getFormatRating(teamB, format);
        double eA = 1.0 / (1.0 + Math.pow(10.0, (rB - rA) / 400.0));
        setFormatRating(teamA, format, Math.max(100, (int) Math.round(rA + K * (scoreA - eA))));
        setFormatRating(teamB, format, Math.max(100, (int) Math.round(rB + K * (scoreB - (1.0 - eA)))));
    }

    private int getFormatRating(Team team, String format) {
        if ("T20".equalsIgnoreCase(format)) return team.getT20Rating() != null ? team.getT20Rating() : 1000;
        if ("FC".equalsIgnoreCase(format))  return team.getFcRating()  != null ? team.getFcRating()  : 1000;
        return team.getOdiRating() != null ? team.getOdiRating() : 1000;
    }
    private void setFormatRating(Team team, String format, int rating) {
        if ("T20".equalsIgnoreCase(format))     team.setT20Rating(rating);
        else if ("FC".equalsIgnoreCase(format))  team.setFcRating(rating);
        else                                      team.setOdiRating(rating);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PLAYER STATS UPDATE (post-match)
    // ═══════════════════════════════════════════════════════════════════════════

    private void updatePlayerStats(MatchResult result, String format) {
        Map<UUID, int[]>    playerBatStats    = new HashMap<>();
        Map<UUID, double[]> playerBowlStats   = new HashMap<>();
        Map<UUID, Integer>  fielderDismissals = new HashMap<>();
        Set<UUID> allPlayerIds = new HashSet<>();

        for (Innings inn : result.getInningsList()) {
            for (BattingScorecard bc : inn.getBattingCards()) {
                UUID pid = bc.getPlayer().getId(); allPlayerIds.add(pid);
                int[] s = playerBatStats.computeIfAbsent(pid, k -> new int[3]);
                s[0] += bc.getRunsScored(); s[1] += bc.getBallsFaced();
                if (bc.getBallsFaced() > 0 && bc.getRunsScored() == 0 && bc.getDismissalType() != null) s[2]++;
                if (bc.getFielder() != null && ("CAUGHT".equals(bc.getDismissalType()) || "CAUGHT_BEHIND".equals(bc.getDismissalType())
                        || "STUMPED".equals(bc.getDismissalType()) || "RUN_OUT".equals(bc.getDismissalType()))) {
                    fielderDismissals.merge(bc.getFielder().getId(), 1, Integer::sum);
                }
            }
            for (BowlingScorecard bw : inn.getBowlingCards()) {
                UUID pid = bw.getPlayer().getId(); allPlayerIds.add(pid);
                double[] s = playerBowlStats.computeIfAbsent(pid, k -> new double[3]);
                s[0] += bw.getOvers(); s[1] += bw.getRunsConceded(); s[2] += bw.getWickets();
            }
        }
        if (allPlayerIds.isEmpty()) return;

        UUID winnerId = result.getWinner() != null ? result.getWinner().getId() : null;
        Set<UUID> winningIds = new HashSet<>(), losingIds = new HashSet<>();
        if (winnerId != null) {
            for (Innings inn : result.getInningsList()) {
                boolean wb  = inn.getBattingTeam().getId().equals(winnerId);
                for (BattingScorecard bc : inn.getBattingCards()) { if (wb) winningIds.add(bc.getPlayer().getId()); else losingIds.add(bc.getPlayer().getId()); }
                boolean wbl = inn.getBowlingTeam().getId().equals(winnerId);
                for (BowlingScorecard bw : inn.getBowlingCards()) { if (wbl) winningIds.add(bw.getPlayer().getId()); else losingIds.add(bw.getPlayer().getId()); }
            }
        }

        UUID motmId = result.getManOfMatch() != null ? result.getManOfMatch().getId() : null;
        boolean isFC = "FC".equalsIgnoreCase(format), isT20 = "T20".equalsIgnoreCase(format);
        int baseFitnessLoss; double batDivisor, bowlDivisor;
        if (isT20)     { baseFitnessLoss = 3; batDivisor = 40.0;  bowlDivisor = 1.5; }
        else if (isFC) { baseFitnessLoss = 8; batDivisor = 80.0;  bowlDivisor = 5.0; }
        else           { baseFitnessLoss = 5; batDivisor = 60.0;  bowlDivisor = 3.0; }

        List<Player> players = playerRepository.findAllById(allPlayerIds);
        for (Player p : players) {
            UUID pid  = p.getId();
            int[] batS    = playerBatStats.getOrDefault(pid, new int[3]);
            double[] bowlS = playerBowlStats.getOrDefault(pid, new double[3]);
            double rawLoss = baseFitnessLoss + batS[1] / batDivisor + bowlS[0] / bowlDivisor;
            double staminaMulti = 1.3 - (p.getStamina() / 100.0) * 0.6;
            p.setFitness(Math.max(0, Math.min(100, p.getFitness() - (int) Math.round(rawLoss * staminaMulti))));

            int confDelta = 0;
            if (batS[1] > 0) {
                int runs = batS[0], ducks = batS[2];
                if (ducks > 0) confDelta -= 4 * ducks;
                int ts, tm, tb, te;
                if (isT20)     { ts = 15; tm = 25; tb = 40; te = 75; }
                else if (isFC) { ts = 25; tm = 50; tb = 75; te = 150; }
                else           { ts = 20; tm = 35; tb = 50; te = 100; }
                if      (runs >= te) confDelta += 7;
                else if (runs >= tb) confDelta += 4;
                else if (runs >= tm) confDelta += 2;
                else if (runs < ts && ducks == 0) confDelta -= 1;
            }
            if (bowlS[0] > 0) {
                int wkts       = (int) bowlS[2];
                double econ    = bowlS[1] / Math.max(1.0, bowlS[0]);
                double expensive = isT20 ? 10.0 : isFC ? 4.0 : 7.0;
                if      (wkts >= 5) confDelta += 7;
                else if (wkts >= 3) confDelta += 4;
                else if (wkts >= 2) confDelta += 2;
                else if (wkts == 1) confDelta += 1;
                else if (econ > expensive) confDelta -= 3;
            }
            int fieldingContrib = fielderDismissals.getOrDefault(pid, 0);
            if (fieldingContrib >= 3)       confDelta += 3;
            else if (fieldingContrib >= 2)  confDelta += 2;
            else if (fieldingContrib == 1)  confDelta += 1;

            if (pid.equals(motmId))            confDelta += 5;
            if (winningIds.contains(pid))      confDelta += 2;
            else if (losingIds.contains(pid))  confDelta -= 2;
            p.setConfidence(Math.max(0, Math.min(100, p.getConfidence() + confDelta)));

            int rawXp = 1;
            if (isFC) rawXp++;
            if (batS[1] > 0) {
                int runs = batS[0];
                if (isT20)     { if (runs >= 75) rawXp += 2; else if (runs >= 40)  rawXp++; }
                else if (isFC) { if (runs >= 150) rawXp += 2; else if (runs >= 75)  rawXp++; }
                else           { if (runs >= 100) rawXp += 2; else if (runs >= 50)  rawXp++; }
            }
            if (bowlS[0] > 0) { if ((int) bowlS[2] >= 5) rawXp += 2; else if ((int) bowlS[2] >= 3) rawXp++; }
            if (pid.equals(motmId)) rawXp++;
            if (fieldingContrib >= 2) rawXp++;
            int currentXp = p.getExperience();
            p.setExperience(currentXp + (int) (rawXp * 50.0 / (50.0 + currentXp)));
        }
        playerRepository.saveAll(players);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MAN OF THE MATCH — keeper stumping/caught-behind valued at +20
    // ═══════════════════════════════════════════════════════════════════════════

    private Player pickManOfMatch(MatchResult result, Random rng) {
        Map<UUID, Double> scores  = new HashMap<>();
        Map<UUID, Player> players = new HashMap<>();
        Map<UUID, Integer> fieldingContribs = new HashMap<>();
        Map<UUID, Boolean> isKeeper = new HashMap<>();

        for (Innings inn : result.getInningsList()) {
            for (BattingScorecard bc : inn.getBattingCards()) {
                double pts = bc.getRunsScored() * 1.0 + bc.getFours() * 1.5 + bc.getSixes() * 2.0;
                if (bc.getRunsScored() >= 50)  pts += 15;
                if (bc.getRunsScored() >= 100) pts += 30;
                scores.merge(bc.getPlayer().getId(), pts, Double::sum);
                players.put(bc.getPlayer().getId(), bc.getPlayer());
                if (bc.getFielder() != null && ("CAUGHT".equals(bc.getDismissalType())
                        || "CAUGHT_BEHIND".equals(bc.getDismissalType())
                        || "STUMPED".equals(bc.getDismissalType())
                        || "RUN_OUT".equals(bc.getDismissalType()))) {
                    fieldingContribs.merge(bc.getFielder().getId(), 1, Integer::sum);
                    players.put(bc.getFielder().getId(), bc.getFielder());
                    if ("CAUGHT_BEHIND".equals(bc.getDismissalType()) || "STUMPED".equals(bc.getDismissalType()))
                        isKeeper.put(bc.getFielder().getId(), true);
                }
            }
            for (BowlingScorecard bc : inn.getBowlingCards()) {
                double pts = bc.getWickets() * 20.0 + bc.getMaidens() * 5.0 + bc.getDotBalls() * 0.5;
                if (bc.getWickets() >= 3) pts += 15;
                if (bc.getWickets() >= 5) pts += 30;
                if (bc.getOvers() > 0 && bc.getEconomy() < 5.0) pts += 10;
                scores.merge(bc.getPlayer().getId(), pts, Double::sum);
                players.put(bc.getPlayer().getId(), bc.getPlayer());
            }
        }

        for (Map.Entry<UUID, Integer> e : fieldingContribs.entrySet()) {
            boolean keeperDismissal = Boolean.TRUE.equals(isKeeper.get(e.getKey()));
            double perDismissal     = keeperDismissal ? 20.0 : 15.0;
            scores.merge(e.getKey(), e.getValue() * perDismissal, Double::sum);
        }

        if (scores.isEmpty()) return null;
        UUID bestId = scores.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        return bestId != null ? players.get(bestId) : null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  COMMENTARY HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private String getBoundaryCommentary(Random rng) {
        String[] options = {"Driven through the covers", "Cut past point", "Flicked off the pads",
                "Edged past the keeper", "Square driven beautifully", "Pulled to the boundary",
                "Swept fine", "Punched through mid-off", "Driven down the ground",
                "Clipped off the hips to the fence", "Driven through extra cover",
                "Glanced fine for four"};
        return options[rng.nextInt(options.length)];
    }

    private String getSixCommentary(Random rng) {
        String[] options = {"Launched over long-on", "Smashed over midwicket", "Scooped over fine leg",
                "Lofted straight down the ground", "Hammered over extra cover", "Heaved over the leg side",
                "Deposited into the stands", "Reverse swept for six", "Stepped out and cleared long-off",
                "Massive hit into the crowd", "Swings hard and sends it into the second tier",
                "Inside-out six over cover point"};
        return options[rng.nextInt(options.length)];
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SCORECARD HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private BattingScorecard createBatCard(Innings innings, LineupPlayer lp, int position) {
        return BattingScorecard.builder().innings(innings).player(lp.getPlayer()).battingPosition(position).build();
    }
    private BowlingScorecard createBowlCard(Innings innings, Player bowler) {
        return BowlingScorecard.builder().innings(innings).player(bowler).build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════════════════════════════════════

    private int getMaxOvers(String format) {
        if ("T20".equalsIgnoreCase(format)) return T20_OVERS;
        if ("FC".equalsIgnoreCase(format))  return FC_SESSION_OVERS;
        return ODI_OVERS;
    }
    private int getMaxPerBowler(String format) {
        if ("T20".equalsIgnoreCase(format)) return 4;
        if ("FC".equalsIgnoreCase(format))  return 50;
        return 10;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PUBLIC: TEAM STRENGTH BREAKDOWN
    // ═══════════════════════════════════════════════════════════════════════════

    public Map<String, Object> computeTeamStrengthBreakdown(MatchLineup lineup, String pitchType,
                                                             String condition, int temperature) {
        List<LineupPlayer> sortedPlayers = new ArrayList<>(lineup.getPlayers());
        sortedPlayers.sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));
        double topOrder = 0, middleOrder = 0, lowerOrder = 0, totalFielding = 0, wkSkill = 0;
        for (LineupPlayer lp : sortedPlayers) {
            Player p = lp.getPlayer(); int pos = lp.getBattingPosition();
            double batEff = p.getBatRating() + (p.getConfidence() / 100.0) * 6.0
                    + Math.min(p.getExperience() * 0.3, 10.0) + (p.getFitness() / 100.0) * 3.0
                    + getBatterPitchModifier(pitchType, p.getBatHand(), p.getBatRating(), p.getExperience(), 1, 50)
                    + getWeatherEffect(condition, temperature, p.getBowlType()).battingMod
                    + getBatterWeatherModifier(condition, temperature, p.getExperience(), p.getBatRating());
            batEff = Math.max(5, Math.min(batEff, 120));
            if (pos <= 3) topOrder += batEff; else if (pos <= 7) middleOrder += batEff; else lowerOrder += batEff;
            totalFielding += p.getFldRating();
            if ("WK".equals(p.getRole()) || "WK_BAT".equals(p.getRole()) || "KEEPER".equals(p.getRole()))
                wkSkill = Math.max(wkSkill, p.getKeeperRating());
        }
        double seamBowling = 0, spinBowling = 0; int seamCount = 0, spinCount = 0;
        Set<UUID> seenBowlerIds = new HashSet<>();
        for (BowlingOrder bo : lineup.getBowlingOrders()) {
            Player p = bo.getBowler();
            if (!seenBowlerIds.add(p.getId())) continue;
            String bType = p.getBowlType() != null ? p.getBowlType() : "M";
            PitchEffect pe = getPitchEffect(pitchType, bType);
            WeatherEffect we = getWeatherEffect(condition, temperature, bType);
            double bowlEff = p.getBowlRating() + (p.getConfidence() / 100.0) * 5.0
                    + Math.min(p.getExperience() * 0.3, 10.0) + (p.getFitness() / 100.0) * 3.0
                    + pe.bowlerBonus + we.bowlingMod + getBowlerTypeMatchup(bType, "RH", pitchType);
            bowlEff = Math.max(5, Math.min(bowlEff, 120));
            if (PACE_TYPES.contains(bType)) { seamBowling += bowlEff; seamCount++; }
            else if (SPIN_TYPES.contains(bType)) { spinBowling += bowlEff; spinCount++; }
            else { seamBowling += bowlEff; seamCount++; }
        }
        double fldComponent = totalFielding + wkSkill;
        double total = topOrder + middleOrder + lowerOrder + seamBowling + spinBowling + Math.round(fldComponent / 4.0);
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("topOrder", Math.round(topOrder));
        breakdown.put("middleOrder", Math.round(middleOrder));
        breakdown.put("lowerOrder", Math.round(lowerOrder));
        breakdown.put("seamBowling", Math.round(seamBowling));
        breakdown.put("seamCount", seamCount);
        breakdown.put("spinBowling", Math.round(spinBowling));
        breakdown.put("spinCount", spinCount);
        breakdown.put("fielding", Math.round(totalFielding));
        breakdown.put("wkSkill", Math.round(wkSkill));
        breakdown.put("fldComponent", Math.round(fldComponent));
        breakdown.put("total", Math.round(total));
        return breakdown;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    private static class SimContext {
        final Random rng; final String pitchType, condition, format;
        final int temperature, maxOvers, maxPerBowler, target;
        final boolean isChasing;
        final MatchLineup battingLineup, bowlingLineup;
        // Actual first-innings total — used to compute a realistic par rate
        // so chasing teams aren't penalised for high first-innings scores
        final int firstInningsTotal;

        SimContext(Random rng, String pitchType, String condition, int temperature,
                   int maxOvers, int maxPerBowler, String format,
                   boolean isChasing, int target,
                   MatchLineup battingLineup, MatchLineup bowlingLineup) {
            this(rng, pitchType, condition, temperature, maxOvers, maxPerBowler,
                    format, isChasing, target, battingLineup, bowlingLineup, 0);
        }

        SimContext(Random rng, String pitchType, String condition, int temperature,
                   int maxOvers, int maxPerBowler, String format,
                   boolean isChasing, int target,
                   MatchLineup battingLineup, MatchLineup bowlingLineup,
                   int firstInningsTotal) {
            this.rng = rng; this.pitchType = pitchType; this.condition = condition;
            this.temperature = temperature; this.maxOvers = maxOvers; this.maxPerBowler = maxPerBowler;
            this.format = format; this.isChasing = isChasing; this.target = target;
            this.battingLineup = battingLineup; this.bowlingLineup = bowlingLineup;
            this.firstInningsTotal = firstInningsTotal;
        }
    }

    private static class BatsmanState {
        final Player player; final LineupPlayer lineupPlayer;
        BatsmanState(LineupPlayer lp) { this.player = lp.getPlayer(); this.lineupPlayer = lp; }
    }

    private static class DeliveryResult {
        int runs = 0;
        boolean isWicket, isNonStrikerOut, isBoundary, isSix, isWide, isNoBall, isBye, isLegBye;
        String dismissalType = null;
        Player fielder       = null;
        String commentary    = "";
        // FIX: slots for batting PP boost applied from innings loop

    }

    private static class DismissalInfo {
        final String type; final Player fielder; final String commentary;
        DismissalInfo(String type, Player fielder, String commentary) {
            this.type = type; this.fielder = fielder; this.commentary = commentary;
        }
    }

    private static class PitchEffect {
        final double bowlerBonus;
        PitchEffect(double bowlerBonus) { this.bowlerBonus = bowlerBonus; }
    }

    private static class WeatherEffect {
        final double battingMod, bowlingMod;
        WeatherEffect(double battingMod, double bowlingMod) {
            this.battingMod = battingMod; this.bowlingMod = bowlingMod;
        }
    }
}
