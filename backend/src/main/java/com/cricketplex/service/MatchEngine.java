package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Comprehensive ball-by-ball match simulation engine.
 *
 * Factors considered per delivery:
 *  - Batter batting skill (batRating) & batting aggression (D/N/A)
 *  - Bowler bowling skill (bowlRating) & bowling aggression (D/N/A)
 *  - Bowler type (F/M/FM/MF/FS/WS) matchups
 *  - Pitch type (GREEN, DUSTY, FLAT, BOUNCY, UNEVEN, DRY, SLOW, STANDARD)
 *  - Weather (Sunny/Overcast/Rain/Windy/Foggy/Hot & Humid)
 *  - Player confidence (0-100)
 *  - Player experience
 *  - Player fitness & stamina
 *  - Fielding skill of bowling team (fldRating)
 *  - Keeper skill (keeperRating)
 *  - Match situation (run chase pressure, death overs, powerplay)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchEngine {

    /**
     * When true, BallEvent rows are NOT written to the DB.
     * Used during dev fast-forward to skip millions of commentary rows.
     * Stats (scorecards, standings, ratings) are unaffected — they rely on
     * BattingScorecard / BowlingScorecard, not BallEvent.
     * ThreadLocal so it is safe to use with parallel simulation threads.
     */
    private static final ThreadLocal<Boolean> SKIP_BALL_EVENTS = ThreadLocal.withInitial(() -> false);

    public static void setSkipBallEvents(boolean skip) {
        SKIP_BALL_EVENTS.set(skip);
    }

    private final MatchResultRepository matchResultRepository;
    private final MatchLineupRepository matchLineupRepository;
    private final MatchFCStrategyRepository matchFCStrategyRepository;
    private final DefaultLineupRepository defaultLineupRepository;
    private final PlayerRepository playerRepository;
    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;
    private final StadiumSeatsRepository stadiumSeatsRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final WeatherService weatherService;
    private final ActivityLogService activityLogService;
    private final FixtureService fixtureService;

    // ─── Public entry point ──────────────────────────────────────

    @Transactional
    public MatchResult simulateMatch(UUID fixtureId) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new IllegalArgumentException("Fixture not found"));

        String fixtureStatus = fixture.getStatus();
        boolean isFcDay2 = "FC_DAY1_COMPLETE".equals(fixtureStatus);

        if (!"SCHEDULED".equals(fixtureStatus) && !isFcDay2) {
            throw new IllegalStateException("Match already played or in progress");
        }
        if ("SCHEDULED".equals(fixtureStatus) && matchResultRepository.existsByFixtureId(fixtureId)) {
            throw new IllegalStateException("Match result already exists");
        }

        League league = fixture.getLeague();
        String format;
        if (league != null) {
            format = league.getFormat();
        } else {
            format = fixture.getFormat();
        }
        if (format == null) {
            throw new IllegalArgumentException("Match format could not be determined");
        }

        // Route to FC match engine if First-Class format
        if ("FC".equalsIgnoreCase(format)) {
            if (isFcDay2) {
                return simulateFCDay2(fixture, format);
            }
            return simulateFCDay1(fixture, format, fixture.getHomeTeam(), fixture.getAwayTeam());
        }

        // Limited-overs formats only below
        if (!"T20".equalsIgnoreCase(format) && !"ODI".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException("Unsupported format: " + format);
        }
        int maxOvers = getMaxOvers(format);
        int maxPerBowler = getMaxPerBowler(format);

        Team homeTeam = fixture.getHomeTeam();
        Team awayTeam = fixture.getAwayTeam();

        // Load lineups (auto-generate for bots if missing)
        MatchLineup homeLineup = getOrGenerateLineup(fixture, homeTeam, format);
        MatchLineup awayLineup = getOrGenerateLineup(fixture, awayTeam, format);

        // Weather
        Map<String, Object> weather = weatherService.getWeather(homeTeam.getCountry(), fixture.getMatchDate());
        String condition = (String) weather.get("condition");
        int temperature = (int) weather.get("temperature");

        // Pitch — use home team's default if fixture is still on STANDARD
        String pitchType = fixture.getPitchType();
        if ("STANDARD".equals(pitchType)) {
            stadiumSeatsRepository.findByTeam(homeTeam)
                    .ifPresent(seats -> {
                        if (seats.getDefaultPitch() != null && !seats.getDefaultPitch().isBlank()) {
                            fixture.setPitchType(seats.getDefaultPitch());
                        }
                    });
            pitchType = fixture.getPitchType();
        }

        // Determine toss
        Random rng = new Random((fixtureId.toString() + fixture.getMatchDate()).hashCode());
        boolean homeToss = rng.nextBoolean();
        Team tossWinner;
        String tossDecision;

        if (homeToss) {
            tossWinner = homeTeam;
            tossDecision = decideToss(homeLineup, pitchType, condition, rng);
        } else {
            tossWinner = awayTeam;
            tossDecision = decideToss(awayLineup, pitchType, condition, rng);
        }

        // Determine batting order
        Team battingFirst, battingSecond;
        MatchLineup battingFirstLineup, battingSecondLineup;
        if ("BAT".equals(tossDecision)) {
            battingFirst = tossWinner;
            battingSecond = (tossWinner.getId().equals(homeTeam.getId())) ? awayTeam : homeTeam;
            battingFirstLineup = (tossWinner.getId().equals(homeTeam.getId())) ? homeLineup : awayLineup;
            battingSecondLineup = (tossWinner.getId().equals(homeTeam.getId())) ? awayLineup : homeLineup;
        } else {
            battingSecond = tossWinner;
            battingFirst = (tossWinner.getId().equals(homeTeam.getId())) ? awayTeam : homeTeam;
            battingSecondLineup = (tossWinner.getId().equals(homeTeam.getId())) ? homeLineup : awayLineup;
            battingFirstLineup = (tossWinner.getId().equals(homeTeam.getId())) ? awayLineup : homeLineup;
        }

        // Build match result
        MatchResult result = MatchResult.builder()
                .fixture(fixture)
                .tossWinner(tossWinner)
                .tossDecision(tossDecision)
                .build();

        // ─── First Innings ───
        Innings firstInnings = Innings.builder()
                .matchResult(result)
                .inningsNumber(1)
                .battingTeam(battingFirst)
                .bowlingTeam(battingSecond)
                .build();

        SimContext ctx1 = new SimContext(rng, pitchType, condition, temperature, maxOvers, maxPerBowler,
                format, false, 0, battingFirstLineup, battingSecondLineup);
        simulateInnings(firstInnings, ctx1);
        result.getInningsList().add(firstInnings);

        int target = firstInnings.getTotalRuns() + 1;

        // ─── Second Innings ───
        Innings secondInnings = Innings.builder()
                .matchResult(result)
                .inningsNumber(2)
                .battingTeam(battingSecond)
                .bowlingTeam(battingFirst)
                .build();

        SimContext ctx2 = new SimContext(rng, pitchType, condition, temperature, maxOvers, maxPerBowler,
                format, true, target, battingSecondLineup, battingFirstLineup);
        simulateInnings(secondInnings, ctx2);
        result.getInningsList().add(secondInnings);

        // ─── Determine result ───
        determineResult(result, firstInnings, secondInnings, battingFirst, battingSecond);

        // ─── Man of the match ───
        result.setManOfMatch(pickManOfMatch(result, rng));

        // ─── Save ───
        fixture.setStatus("COMPLETED");
        fixtureRepository.save(fixture);
        // Skip activity logs, stats updates, and gate money for simulation fixtures
        boolean isSim = fixture.getSimSessionId() != null;
        // NOTE: logMatchActivity is NOT called here — it is deferred to MatchScheduler
        // Pass 2 so the Won/Lost activity only appears after the live viewing window ends.
        // Only update team/player stats for league matches — friendlies and sims have no consequences
        if (!isSim && fixture.getLeague() != null) {
            String resolvedFormat = fixture.getLeague().getFormat();
            updateMoraleAndFans(result, fixture, resolvedFormat);
            updatePlayerStats(result, resolvedFormat);
            distributeGateMoney(result, fixture);
        }
        MatchResult saved = matchResultRepository.save(result);
        // Apply any deferred bot→human swaps — skip for sim fixtures
        if (!isSim) {
            fixtureService.applyPendingSwap(fixture.getId(), saved);
        }
        return saved;
    }

    // ─── Innings simulation ─────────────────────────────────────

    private void simulateInnings(Innings innings, SimContext ctx) {
        List<LineupPlayer> battingOrder = new ArrayList<>(ctx.battingLineup.getPlayers());
        battingOrder.sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));

        List<BowlingOrder> bowlingOrders = new ArrayList<>(ctx.bowlingLineup.getBowlingOrders());
        bowlingOrders.sort(Comparator.comparingInt(BowlingOrder::getOverNumber));

        // Build bowler map: overNumber -> BowlingOrder
        Map<Integer, BowlingOrder> bowlerPlan = new LinkedHashMap<>();
        for (BowlingOrder bo : bowlingOrders) {
            bowlerPlan.put(bo.getOverNumber(), bo);
        }

        // Fielding average of bowling team
        double teamFieldingAvg = calculateTeamFielding(ctx.bowlingLineup);
        double keeperSkill = getKeeperRating(ctx.bowlingLineup);

        if (battingOrder.size() < 2) return; // need at least 2 batters

        // Initialize batsmen
        int nextBatIdx = 2; // 0-indexed: first two are already in
        BatsmanState striker = new BatsmanState(battingOrder.get(0));
        BatsmanState nonStriker = new BatsmanState(battingOrder.get(1));

        // Batting scorecards map
        Map<UUID, BattingScorecard> batCards = new LinkedHashMap<>();
        batCards.put(striker.player.getId(), createBatCard(innings, striker.lineupPlayer, 1));
        batCards.put(nonStriker.player.getId(), createBatCard(innings, nonStriker.lineupPlayer, 2));

        // Bowling scorecards map
        Map<UUID, BowlingScorecard> bowlCards = new LinkedHashMap<>();

        int totalRuns = 0;
        int totalWickets = 0;
        int totalExtras = 0;
        int ballsBowled = 0; // legal balls
        int overNumber = 0;

        Player currentBowler = null;
        String currentBowlerAggression = "N";
        Map<UUID, Integer> bowlerBallCounts = new HashMap<>(); // legal balls per bowler
        Player previousBowler = null;

        // Simulate ball by ball
        outerLoop:
        for (int over = 0; over < ctx.maxOvers; over++) {
            overNumber = over + 1;

            // Select bowler for this over
            BowlingOrder planned = bowlerPlan.get(overNumber);
            if (planned != null) {
                currentBowler = planned.getBowler();
                currentBowlerAggression = planned.getAggression();
            } else {
                // Fallback: pick a bowler who hasn't maxed out, not the previous bowler
                currentBowler = pickFallbackBowler(ctx, bowlerBallCounts, previousBowler);
                currentBowlerAggression = "N";
            }

            if (!bowlCards.containsKey(currentBowler.getId())) {
                bowlCards.put(currentBowler.getId(), createBowlCard(innings, currentBowler));
            }
            BowlingScorecard bowlCard = bowlCards.get(currentBowler.getId());

            int legalBallsThisOver = 0;
            int runsThisOver = 0;
            boolean maidenPossible = true;
            int ballInOver = 0;

            while (legalBallsThisOver < 6) {
                ballInOver++;

                // ─── Calculate delivery outcome ───
                DeliveryResult delivery = simulateDelivery(
                        ctx, striker, currentBowler, currentBowlerAggression,
                        teamFieldingAvg, keeperSkill,
                        overNumber, totalRuns, totalWickets, ballsBowled,
                        batCards.get(striker.player.getId()));

                // Create ball event (skipped during fast-forward to avoid millions of rows)
                if (!Boolean.TRUE.equals(SKIP_BALL_EVENTS.get())) {
                    BallEvent event = BallEvent.builder()
                            .innings(innings)
                            .overNumber(overNumber)
                            .ballNumber(ballInOver)
                            .batsman(striker.player)
                            .bowler(currentBowler)
                            .runs(delivery.runs)
                            .isWicket(delivery.isWicket)
                            .isBoundary(delivery.isBoundary)
                            .isSix(delivery.isSix)
                            .isWide(delivery.isWide)
                            .isNoBall(delivery.isNoBall)
                            .isBye(delivery.isBye)
                            .isLegBye(delivery.isLegBye)
                            .dismissalType(delivery.dismissalType)
                            .fielder(delivery.fielder)
                            .commentary(delivery.commentary)
                            .build();
                    innings.getBallEvents().add(event);
                }

                // Update scores
                totalRuns += delivery.runs;

                if (delivery.isWide || delivery.isNoBall) {
                    // Extra — doesn't count as legal ball
                    totalExtras += delivery.runs;
                    bowlCard.setRunsConceded(bowlCard.getRunsConceded() + delivery.runs);
                    if (delivery.isWide) bowlCard.setWides(bowlCard.getWides() + 1);
                    if (delivery.isNoBall) bowlCard.setNoBalls(bowlCard.getNoBalls() + 1);
                    maidenPossible = false;
                    runsThisOver += delivery.runs;
                } else {
                    // Legal ball
                    legalBallsThisOver++;
                    ballsBowled++;
                    bowlerBallCounts.merge(currentBowler.getId(), 1, Integer::sum);

                    BattingScorecard batCard = batCards.get(striker.player.getId());
                    batCard.setBallsFaced(batCard.getBallsFaced() + 1);

                    if (delivery.isBye || delivery.isLegBye) {
                        totalExtras += delivery.runs;
                        bowlCard.setRunsConceded(bowlCard.getRunsConceded() + 0); // byes don't count against bowler
                        if (delivery.runs == 0) bowlCard.setDotBalls(bowlCard.getDotBalls() + 1);
                    } else {
                        batCard.setRunsScored(batCard.getRunsScored() + delivery.runs);
                        if (delivery.isBoundary) batCard.setFours(batCard.getFours() + 1);
                        if (delivery.isSix) batCard.setSixes(batCard.getSixes() + 1);
                        bowlCard.setRunsConceded(bowlCard.getRunsConceded() + delivery.runs);
                        if (delivery.runs == 0 && !delivery.isWicket) {
                            bowlCard.setDotBalls(bowlCard.getDotBalls() + 1);
                        }
                    }

                    if (delivery.runs > 0) maidenPossible = false;
                    runsThisOver += delivery.runs;

                    // Wicket?
                    if (delivery.isWicket) {
                        maidenPossible = false;
                        totalWickets++;

                        batCard.setDismissalType(delivery.dismissalType);
                        if (!"RUN_OUT".equals(delivery.dismissalType)) {
                            bowlCard.setWickets(bowlCard.getWickets() + 1);
                            batCard.setBowler(currentBowler);
                        }
                        batCard.setFielder(delivery.fielder);

                        // Update strike rate for dismissed batter
                        if (batCard.getBallsFaced() > 0) {
                            batCard.setStrikeRate(
                                    Math.round(batCard.getRunsScored() * 100.0 / batCard.getBallsFaced() * 100.0) / 100.0);
                        }

                        // Next batter
                        if (totalWickets >= 10 || nextBatIdx >= battingOrder.size()) {
                            innings.setAllOut(true);
                            break outerLoop;
                        }

                        striker = new BatsmanState(battingOrder.get(nextBatIdx));
                        nextBatIdx++;
                        batCards.put(striker.player.getId(),
                                createBatCard(innings, striker.lineupPlayer, nextBatIdx));
                    } else {
                        // Rotate strike on odd runs
                        if (delivery.runs % 2 == 1) {
                            boolean keepStrike = !delivery.isBye && !delivery.isLegBye
                                    && tryStrikeFarmingKeepStrike(
                                    ctx, striker, nonStriker, batCard, legalBallsThisOver, overNumber, false, ctx.rng);
                            if (!keepStrike) {
                                BatsmanState temp = striker;
                                striker = nonStriker;
                                nonStriker = temp;
                            }
                        }
                    }
                }

                // Chase target check
                if (ctx.isChasing && totalRuns >= ctx.target) {
                    break outerLoop;
                }
            }

            // End of over: rotate strike (unless better batter farms strike successfully)
            BattingScorecard overEndStrikerCard = batCards.get(striker.player.getId());
            boolean keepEndOverStrike = tryStrikeFarmingKeepStrike(
                    ctx, striker, nonStriker, overEndStrikerCard, legalBallsThisOver, overNumber, true, ctx.rng);
            if (!keepEndOverStrike) {
                BatsmanState temp = striker;
                striker = nonStriker;
                nonStriker = temp;
            }

            // Maiden
            if (maidenPossible && runsThisOver == 0) {
                bowlCard.setMaidens(bowlCard.getMaidens() + 1);
            }

            previousBowler = currentBowler;
        }

        // ─── Finalize innings ───
        innings.setTotalRuns(totalRuns);
        innings.setTotalWickets(totalWickets);
        innings.setExtras(totalExtras);

        // Total overs as decimal (e.g., 19.3)
        int completedOvers = ballsBowled / 6;
        int remainingBalls = ballsBowled % 6;
        innings.setTotalOvers(completedOvers + remainingBalls / 10.0);

        // Finalize batting strike rates
        for (BattingScorecard card : batCards.values()) {
            if (card.getBallsFaced() > 0) {
                card.setStrikeRate(
                        Math.round(card.getRunsScored() * 100.0 / card.getBallsFaced() * 100.0) / 100.0);
            }
            innings.getBattingCards().add(card);
        }

        // Finalize bowling economy
        for (BowlingScorecard card : bowlCards.values()) {
            int bowledBalls = bowlerBallCounts.getOrDefault(card.getPlayer().getId(), 0);
            int fullOvers = bowledBalls / 6;
            int partBalls = bowledBalls % 6;
            card.setOvers(fullOvers + partBalls / 10.0);
            if (bowledBalls > 0) {
                double oversDecimal = bowledBalls / 6.0;
                card.setEconomy(Math.round(card.getRunsConceded() / oversDecimal * 100.0) / 100.0);
            }
            innings.getBowlingCards().add(card);
        }
    }

    // ─── Delivery simulation ────────────────────────────────────

    private DeliveryResult simulateDelivery(
            SimContext ctx, BatsmanState batter, Player bowler, String bowlerAggression,
            double teamFieldingAvg, double keeperSkill,
            int overNumber, int totalRuns, int totalWickets, int legalBallsBowled,
            BattingScorecard batCard) {

        Random rng = ctx.rng;
        DeliveryResult result = new DeliveryResult();

        // ── Base skill factors (0-100 scale) ──
        double batSkill = batter.player.getBatRating();
        double bowlSkill = bowler.getBowlRating();

        // ── Aggression modifiers ──
        double batAggrMod = getAggressionModifier(batter.lineupPlayer.getBatAggression());
        double bowlAggrMod = getAggressionModifier(bowlerAggression);
        int ballsFaced = batCard != null && batCard.getBallsFaced() != null ? batCard.getBallsFaced() : 0;
        double setBatsmanFactor = getSetBatsmanFactor(ctx.format, ballsFaced, batter.lineupPlayer.getBatAggression());

        // ── Confidence (0-100) → modifier ──
        double batConfidence = batter.player.getConfidence() / 100.0;
        double bowlConfidence = bowler.getConfidence() / 100.0;

        // ── Experience bonus (0-10+ scale → small bonus) ──
        double batExpBonus = Math.min(batter.player.getExperience() * 0.3, 10.0);
        double bowlExpBonus = Math.min(bowler.getExperience() * 0.3, 10.0);

        // ── Fitness/stamina decay ──
        double batFitness = batter.player.getFitness() / 100.0;
        double bowlFitness = bowler.getFitness() / 100.0;
        double batStamina = batter.player.getStamina() / 100.0;
        double bowlStamina = bowler.getStamina() / 100.0;
        // Fatigue simulation: fitness drops slightly with balls faced / bowled
        double batFatigue = 1.0 - (batCard.getBallsFaced() * 0.001 * (1.0 - batStamina));
        double bowlFatigue = 1.0;  // already factored via spell

        // ── Pitch modifier ──
        PitchEffect pitchEffect = getPitchEffect(ctx.pitchType, bowler.getBowlType());

        // ── Batter pitch modifier (bat hand, bat skill, experience on different surfaces) ──
        double batPitchMod = getBatterPitchModifier(ctx.pitchType, batter.player.getBatHand(),
                batter.player.getBatRating(), batter.player.getExperience());

        // ── Weather modifier (bowler-type-aware) ──
        WeatherEffect weatherEffect = getWeatherEffect(ctx.condition, ctx.temperature, bowler.getBowlType());

        // ── Batter weather modifier (experience helps in tough conditions) ──
        double batWeatherMod = getBatterWeatherModifier(ctx.condition, ctx.temperature,
                batter.player.getExperience(), batter.player.getBatRating());

        // ── Bowler type matchup ──
        double typeMatchup = getBowlerTypeMatchup(bowler.getBowlType(), batter.player.getBatHand(), ctx.pitchType);

        // ── Phase of innings (powerplay/middle/death for T20/ODI) ──
        double phaseModifier = getPhaseModifier(ctx.format, overNumber, ctx.maxOvers);

        // ── Composite batting strength ──
        double batStrength = batSkill
                + batAggrMod * 8.0             // aggression adds/reduces scoring but affects wicket risk
                + batConfidence * 6.0
                + batExpBonus
                + batFitness * 3.0
                - pitchEffect.bowlerBonus * 0.5
                + batPitchMod                  // batter-specific pitch advantage/penalty
                + weatherEffect.battingMod
                + batWeatherMod                // batter-specific weather advantage/penalty
                + phaseModifier * 2.0
                + setBatsmanFactor * 3.5;     // time-at-crease confidence/tempo
        batStrength *= batFatigue;
        batStrength = Math.max(5, Math.min(batStrength, 120));

        // ── Composite bowling strength ──
        double bowlStrength = bowlSkill
                + bowlAggrMod * 5.0
                + bowlConfidence * 5.0
                + bowlExpBonus
                + bowlFitness * 3.0
                + pitchEffect.bowlerBonus
                + weatherEffect.bowlingMod
                + typeMatchup
                + teamFieldingAvg * 0.15
                + keeperSkill * 0.05;
        bowlStrength *= bowlFatigue;
        bowlStrength = Math.max(5, Math.min(bowlStrength, 120));

        // ── Chase pressure (format-aware thresholds, pitch-adjusted) ──
        // REDUCED: Halved all pressure values to balance first vs second innings
        // Positive = hard chase (bowler confident, batter pressured)
        // Negative = easy chase (batter comfortable, bowler under pressure)
        double chasePressure = 0;
        if (ctx.isChasing) {
            int runsNeeded = ctx.target - totalRuns;
            int ballsLeft = (ctx.maxOvers * 6) - legalBallsBowled;
            if (ballsLeft > 0) {
                double rawRR = (runsNeeded * 6.0) / ballsLeft;
                // Pitch difficulty scales the *perceived* required rate
                // Tough pitches inflate RRR → pressure kicks in earlier
                // Flat pitches deflate RRR → same rate feels easier
                double pitchRRRScale = switch (ctx.pitchType != null ? ctx.pitchType : "STANDARD") {
                    case "DUSTY"  -> 1.20;  // Reduced from 1.25
                    case "DRY"    -> 1.15;  // Reduced from 1.20
                    case "GREEN"  -> 1.10;  // Reduced from 1.15
                    case "BOUNCY" -> 1.08;  // Reduced from 1.10
                    case "UNEVEN" -> 1.10;  // Reduced from 1.15
                    case "SLOW"   -> 1.05;  // Reduced from 1.10
                    case "FLAT"   -> 0.90;  // Reduced from 0.85
                    default       -> 1.0;   // STANDARD — no adjustment
                };
                double requiredRate = rawRR * pitchRRRScale;
                if ("T20".equalsIgnoreCase(ctx.format)) {
                    if (requiredRate > 14) chasePressure = 5;      // Halved from 10
                    else if (requiredRate > 12) chasePressure = 3.5;  // Halved from 7
                    else if (requiredRate > 10) chasePressure = 2;    // Halved from 4
                    else if (requiredRate > 8) chasePressure = 0.5;   // Halved from 1
                    else if (requiredRate < 5) chasePressure = -1.5;  // Halved from -3
                    else if (requiredRate < 3) chasePressure = -2.5;  // Halved from -5
                } else if ("ODI".equalsIgnoreCase(ctx.format)) {
                    if (requiredRate > 10) chasePressure = 5;      // Halved from 10
                    else if (requiredRate > 8) chasePressure = 3.5; // Halved from 7
                    else if (requiredRate > 6) chasePressure = 2;   // Halved from 4
                    else if (requiredRate > 5) chasePressure = 0.5; // Halved from 1
                    else if (requiredRate < 3) chasePressure = -1.5; // Halved from -3
                    else if (requiredRate < 2) chasePressure = -2.5; // Halved from -5
                } else if ("FC".equalsIgnoreCase(ctx.format)) {
                    // FC chases are more relaxed: plenty of overs
                    if (requiredRate > 6) chasePressure = 5;       // Halved from 10
                    else if (requiredRate > 5) chasePressure = 3;  // Halved from 6
                    else if (requiredRate > 4) chasePressure = 1.5;    // Halved from 3
                    else if (requiredRate > 3) chasePressure = 0.5;    // Halved from 1
                    else if (requiredRate < 1.5) chasePressure = -1.5;  // Halved from -3
                    else if (requiredRate < 1) chasePressure = -2.5;    // Halved from -5
                }
            }
        }
        bowlStrength += chasePressure;
        // Batter composure: easy chase = calmer batting, hard chase = cautious (not reckless)
        batStrength -= chasePressure * 0.3;  // Reduced from 0.6 (less extreme penalization)
        batStrength = Math.max(5, Math.min(batStrength, 120));

        // ─── WIDE / NO-BALL CHECK ───
        // T20 has stricter wide rules → more wides called; ODI more lenient; FC most lenient
        double baseExtra = "T20".equalsIgnoreCase(ctx.format) ? 0.04
                : "FC".equalsIgnoreCase(ctx.format) ? 0.015 : 0.025;
        double extraChance = baseExtra + (bowlAggrMod * 0.01) - (bowler.getBowlRating() * 0.0002)
                + (1.0 - bowlFitness) * 0.01;
        extraChance = Math.max(0.01, Math.min(extraChance, 0.10));

        if (rng.nextDouble() < extraChance) {
            boolean isWide = rng.nextDouble() < 0.7; // 70% wides, 30% no-balls
            result.isWide = isWide;
            result.isNoBall = !isWide;
            result.runs = 1;
            // Wides/no-balls can have extra runs
            if (rng.nextDouble() < 0.08) result.runs += rng.nextInt(3) + 1;
            result.commentary = isWide ? "Wide ball" : "No ball";
            return result;
        }

        // ─── SCORING PROBABILITY ───
        double skill_diff = batStrength - bowlStrength; // positive = batter dominant

        // Format-specific base probabilities - REBALANCED
        // T20: ~8.0 RPO → balanced aggressive scoring with realistic wicket rate
        // ODI: ~5.2 RPO → controlled scoring with higher wicket risk (more balls faced)
        // FC: ~3.0 RPO → defensive, patience-oriented, very low wicket rate
        double pDot, p1, p2, p3, p4, p6, pWicket;

        if ("T20".equalsIgnoreCase(ctx.format)) {
            pDot    = 0.32;  // 32% dots
            p1      = 0.23;  // 23% singles
            p2      = 0.09;  // 9% twos
            p3      = 0.02;  // 2% threes
            p4      = 0.12;  // 12% fours (reduced from 0.15)
            p6      = 0.06;  // 6% sixes (reduced from 0.08)
            pWicket = 0.065; // 6.5% wickets (increased from 0.055 to balance boundaries)
        } else if ("FC".equalsIgnoreCase(ctx.format)) {
            // FC: Most defensive, patience-oriented
            pDot    = 0.50;  // 50% dots (patient batting)
            p1      = 0.25;  // 25% singles
            p2      = 0.08;  // 8% twos
            p3      = 0.03;  // 3% threes
            p4      = 0.05;  // 5% fours (rare boundaries)
            p6      = 0.01;  // 1% sixes (very rare)
            pWicket = 0.035; // 3.5% wickets (slightly increased from 0.03 for 300+ ball innings)
        } else { // ODI
            pDot    = 0.42;  // 42% dots (more conservative)
            p1      = 0.29;  // 29% singles
            p2      = 0.09;  // 9% twos
            p3      = 0.02;  // 2% threes
            p4      = 0.06;  // 6% fours (reduced from 0.08)
            p6      = 0.02;  // 2% sixes (reduced from 0.03)
            pWicket = 0.055; // 5.5% wickets (increased from 0.04 to balance boundaries)
        }

        // Format-scaled shift factor (T20 skill gaps have bigger impact on boundaries)
        // FIXED: Better batters score more boundaries AND take more risks (higher wicket rate)
        double shiftScale = "T20".equalsIgnoreCase(ctx.format) ? 1.2 : "FC".equalsIgnoreCase(ctx.format) ? 0.6 : 0.9;
        double shift = (skill_diff / 200.0) * shiftScale;
        pDot -= shift * 0.35;
        p1 += shift * 0.10;
        p2 += shift * 0.05;
        p4 += shift * 0.10;
        p6 += shift * 0.06;
        pWicket += shift * 0.15; // INVERTED: Higher skill = more aggressive play = higher wicket risk

        // Batting aggression extremes — bigger swings in T20, smaller in ODI, smallest in FC
        // BALANCED: Aggressive players get more boundaries (+32-45%) AND proportional wickets (+17-23%)
        double aggrScale = "T20".equalsIgnoreCase(ctx.format) ? 1.3 : "FC".equalsIgnoreCase(ctx.format) ? 0.5 : 0.8;
        if ("A".equals(batter.lineupPlayer.getBatAggression())) {
            p4 += 0.025 * aggrScale;      // Boundary boost (+32-45% depending on format)
            p6 += 0.02 * aggrScale;       // Six boost
            pWicket += 0.012 * aggrScale; // Proportional wicket increase: T20=+1.56%, ODI=+0.96%, FC=+0.6%
            pDot -= 0.04 * aggrScale;
            p1 -= 0.015 * aggrScale;
        } else if ("D".equals(batter.lineupPlayer.getBatAggression())) {
            p4 -= 0.015 * aggrScale;
            p6 -= 0.015 * aggrScale;
            pWicket -= 0.015 * aggrScale; // Defensive players have lower wicket risk
            pDot += 0.03 * aggrScale;
            p1 += 0.015 * aggrScale;
        }

        // Set-batsman effect: smooth non-linear acceleration with diminishing returns.
        // New batters start slightly conservative; set players find boundaries more often,
        // with only a modest wicket-risk bump so innings don't become overly aggressive.
        if (setBatsmanFactor >= 0) {
            pDot -= 0.025 * setBatsmanFactor;
            p1 -= 0.006 * setBatsmanFactor;
            p2 += 0.008 * setBatsmanFactor;
            p4 += 0.012 * setBatsmanFactor;
            p6 += 0.007 * setBatsmanFactor;
            pWicket += 0.004 * setBatsmanFactor;
        } else {
            double settling = Math.abs(setBatsmanFactor);
            pDot += 0.018 * settling;
            p1 += 0.006 * settling;
            p4 -= 0.009 * settling;
            p6 -= 0.006 * settling;
            pWicket += 0.002 * settling;
        }

        // Bowling aggression effects
        if ("A".equals(bowlerAggression)) {
            pWicket += 0.010 * aggrScale; // REDUCED: from 0.015
            p4 += 0.015 * aggrScale;
            p6 += 0.01 * aggrScale;
            pDot -= 0.015 * aggrScale;
            p1 -= 0.01 * aggrScale;
        } else if ("D".equals(bowlerAggression)) {
            pDot += 0.03 * aggrScale;
            p1 -= 0.01 * aggrScale;
            p4 -= 0.01 * aggrScale;
            pWicket -= 0.01 * aggrScale;
        }

        // ─── SITUATION-AWARE MODIFIERS ───
        // These dynamically adjust probabilities based on match state,
        // overriding or augmenting static player aggression settings.

        String playerAggr = batter.lineupPlayer.getBatAggression();
        if (playerAggr == null) playerAggr = "N";

        // ── 1. Wicket-cluster pressure ──
        // When wickets fall rapidly relative to overs bowled, batsmen must consolidate.
        // Metric: wickets per completed over.  >=1.0 wkt/over is a collapse.
        {
            double completedOvers = Math.max(1.0, legalBallsBowled / 6.0);
            double wktsPerOver = totalWickets / completedOvers;

            // collapseFactor: 0 (no pressure) to ~1.0 (extreme collapse)
            double collapseThreshold = "T20".equalsIgnoreCase(ctx.format) ? 0.60
                    : "FC".equalsIgnoreCase(ctx.format) ? 0.35 : 0.45;
            double collapseFactor = 0;
            if (wktsPerOver > collapseThreshold) {
                collapseFactor = Math.min(1.0, (wktsPerOver - collapseThreshold) / 0.7);
            }

            // Also factor in absolute wickets lost — 7+ down is severe pressure
            if (totalWickets >= 8) collapseFactor = Math.max(collapseFactor, 0.75);
            else if (totalWickets >= 6) collapseFactor = Math.max(collapseFactor, 0.45);
            else if (totalWickets >= 4) collapseFactor = Math.max(collapseFactor, 0.2);

            // Aggressive players try harder but increase risk proportionally
            double resistFactor = "A".equals(playerAggr) ? 0.7 : ("D".equals(playerAggr) ? 1.2 : 1.0);
            double adjustedCollapse = collapseFactor * resistFactor;

            // Shift toward defensive play: more dots/singles, fewer boundaries
            pDot    += 0.06 * adjustedCollapse;
            p1      += 0.03 * adjustedCollapse;
            p4      -= 0.04 * adjustedCollapse;
            p6      -= 0.03 * adjustedCollapse;
            // Reduce wicket stacking: defensive play moderately reduces wicket risk
            pWicket -= 0.008 * adjustedCollapse;
        }

        // ── 2. Chase situation awareness ──
        // Adjust batting intent based on required rate vs match situation.
        // REDUCED PRESSURE: More balanced chase adjustments
        if (ctx.isChasing) {
            int runsNeeded = ctx.target - totalRuns;
            int totalBalls = ctx.maxOvers * 6;
            int ballsRemaining = totalBalls - legalBallsBowled;
            if (ballsRemaining < 1) ballsRemaining = 1;

            double requiredRate = (runsNeeded * 6.0) / ballsRemaining;
            double currentRate = legalBallsBowled > 0 ? (totalRuns * 6.0) / legalBallsBowled : 0;

            // Par rate: what a normal T20/ODI/FC innings scores at
            double parRate = "T20".equalsIgnoreCase(ctx.format) ? 8.0
                    : "FC".equalsIgnoreCase(ctx.format) ? 3.0 : 5.0;

            if (runsNeeded <= 0) {
                // Already won — shouldn't reach here but safety
            } else if (requiredRate < parRate * 0.5) {
                // Very easy chase — play conservatively but not fearfully
                double easyFactor = Math.min(1.0, (parRate * 0.5 - requiredRate) / (parRate * 0.4));
                pDot    += 0.03 * easyFactor;
                p1      += 0.03 * easyFactor;
                p4      -= 0.03 * easyFactor;
                p6      -= 0.02 * easyFactor;
                pWicket -= 0.008 * easyFactor;
            } else if (requiredRate < parRate * 0.8) {
                // Comfortable chase — normal play
                double comfortFactor = Math.min(1.0, (parRate * 0.8 - requiredRate) / (parRate * 0.3));
                pDot += 0.015 * comfortFactor;
                p1   += 0.015 * comfortFactor;
                p4   -= 0.015 * comfortFactor;
                p6   -= 0.01 * comfortFactor;
                pWicket -= 0.003 * comfortFactor;
            } else if (requiredRate > parRate * 1.5) {
                // Desperate chase — accelerate with calculated risk
                double desperateFactor = Math.min(1.0, (requiredRate - parRate * 1.5) / (parRate * 0.5));
                pDot -= 0.04 * desperateFactor;  // Reduced from 0.08
                p1   -= 0.015 * desperateFactor; // Reduced from 0.03
                p4   += 0.02 * desperateFactor;  // Reduced from 0.04
                p6   += 0.025 * desperateFactor; // Reduced from 0.05
                pWicket += 0.015 * desperateFactor; // Reduced from 0.03
            } else if (requiredRate > parRate * 1.2) {
                // Need to accelerate — modest pressure
                double pushFactor = Math.min(1.0, (requiredRate - parRate * 1.2) / (parRate * 0.3));
                pDot -= 0.02 * pushFactor;   // Reduced from 0.04
                p4   += 0.01 * pushFactor;   // Reduced from 0.02
                p6   += 0.01 * pushFactor;   // Reduced from 0.02
                pWicket += 0.006 * pushFactor; // Reduced from 0.01
            }
            // else: par range — no adjustment, play normally
        }

        // ── 3. First innings pacing (don't just slog from ball 1) ──
        // In first innings, early overs should be about building a platform,
        // not every ball is a boundary attempt.
        if (!ctx.isChasing) {
            double completedOvers = legalBallsBowled / 6.0;

            // If batting in first 3-4 overs with lots of wickets down, consolidate harder
            if (completedOvers < 4 && totalWickets >= 2) {
                double earlyPressure = Math.min(1.0, totalWickets / 3.0);
                pDot    += 0.04 * earlyPressure;
                p1      += 0.03 * earlyPressure;
                p4      -= 0.03 * earlyPressure;
                p6      -= 0.03 * earlyPressure;
                pWicket -= 0.01 * earlyPressure;
            }

            // If batting well late (few wickets, good score), accelerate
            if ("T20".equalsIgnoreCase(ctx.format) && completedOvers >= 14 && totalWickets <= 3) {
                double accelBonus = 0.3 + (completedOvers - 14) * 0.1; // ramp up 0.3→0.9
                accelBonus = Math.min(accelBonus, 0.9);
                pDot -= 0.03 * accelBonus;
                p4   += 0.02 * accelBonus;
                p6   += 0.02 * accelBonus;
            } else if ("ODI".equalsIgnoreCase(ctx.format) && completedOvers >= 40 && totalWickets <= 4) {
                double accelBonus = 0.3 + (completedOvers - 40) * 0.07;
                accelBonus = Math.min(accelBonus, 0.9);
                pDot -= 0.03 * accelBonus;
                p4   += 0.02 * accelBonus;
                p6   += 0.02 * accelBonus;
            }
            // FC first innings: patience is king — only very late in an innings with few wickets do they push
            if ("FC".equalsIgnoreCase(ctx.format)) {
                // In FC, early wickets in an innings mean even more consolidation
                if (completedOvers < 10 && totalWickets >= 3) {
                    double earlyPressure = Math.min(1.0, totalWickets / 4.0);
                    pDot    += 0.06 * earlyPressure;
                    p1      += 0.02 * earlyPressure;
                    p4      -= 0.04 * earlyPressure;
                    p6      -= 0.02 * earlyPressure;
                    pWicket -= 0.01 * earlyPressure;
                }
            }
        }

        // Clamp probabilities
        pDot = Math.max(0.10, pDot);
        p1 = Math.max(0.05, p1);
        p2 = Math.max(0.02, p2);
        p3 = Math.max(0.005, p3);
        p4 = Math.max(0.02, p4);
        p6 = Math.max(0.01, p6);
        pWicket = Math.max(0.01, Math.min(pWicket, 0.20));

        // Normalize
        double total = pDot + p1 + p2 + p3 + p4 + p6 + pWicket;
        pDot /= total;
        p1 /= total;
        p2 /= total;
        p3 /= total;
        p4 /= total;
        p6 /= total;
        pWicket /= total;

        // ─── ROLL ───
        double roll = rng.nextDouble();
        double cumulative = 0;

        // Bye/leg bye chance (small, on dot-like balls)
        double byeChance = 0.015 - keeperSkill * 0.0001;
        byeChance = Math.max(0.002, byeChance);

        if (roll < (cumulative += pDot)) {
            // Dot ball — check for bye/leg bye
            if (rng.nextDouble() < byeChance) {
                int byeRuns = rng.nextDouble() < 0.7 ? 1 : 2;
                boolean isLegBye = rng.nextBoolean();
                result.runs = byeRuns;
                result.isBye = !isLegBye;
                result.isLegBye = isLegBye;
                result.commentary = (isLegBye ? "Leg bye, " : "Bye, ") + byeRuns + " run" + (byeRuns > 1 ? "s" : "");
            } else {
                result.runs = 0;
                result.commentary = "Dot ball";
            }
        } else if (roll < (cumulative += p1)) {
            result.runs = 1;
            result.commentary = "Single taken";
        } else if (roll < (cumulative += p2)) {
            result.runs = 2;
            result.commentary = "Pushed for two";
        } else if (roll < (cumulative += p3)) {
            result.runs = 3;
            result.commentary = "Three runs taken";
        } else if (roll < (cumulative += p4)) {
            result.runs = 4;
            result.isBoundary = true;
            result.commentary = "FOUR! " + getBoundaryCommentary(rng);
        } else if (roll < (cumulative += p6)) {
            result.runs = 6;
            result.isSix = true;
            result.commentary = "SIX! " + getSixCommentary(rng);
        } else {
            // ─── WICKET ───
            result.isWicket = true;
            result.runs = 0;

            // Determine dismissal type based on bowler type, fielding, keeper, match state
            DismissalInfo dismissal = determineDismissal(
                    rng, bowler, batter, teamFieldingAvg, keeperSkill, ctx,
                    legalBallsBowled, totalRuns, totalWickets);
            result.dismissalType = dismissal.type;
            result.fielder = dismissal.fielder;
            result.commentary = "OUT! " + dismissal.commentary;

            // Run out can have runs scored
            if ("RUN_OUT".equals(dismissal.type)) {
                // Sometimes 1 run is completed before run out
                if (rng.nextDouble() < 0.25) {
                    result.runs = 1;
                }
            }
        }

        return result;
    }

    // ─── Dismissal determination ────────────────────────────────

    private DismissalInfo determineDismissal(
            Random rng, Player bowler, BatsmanState batter,
            double fieldingAvg, double keeperSkill, SimContext ctx,
            int legalBallsBowled, int totalRuns, int totalWickets) {

        String bowlType = bowler.getBowlType();
        boolean isPace = bowlType != null && (bowlType.equals("F") || bowlType.equals("FM") || bowlType.equals("MF") || bowlType.equals("M"));
        boolean isSpin = bowlType != null && (bowlType.equals("FS") || bowlType.equals("WS"));

        // FIXED: Realistic dismissal distribution based on cricket statistics
        // Pace bowlers: Bowled 18% | Caught 50% | LBW 15% | Caught Behind 10% | Run Out 3% | Stumped 1% | Hit Wicket 3%
        // Spin bowlers: Bowled 15% | Caught 50% | LBW 20% | Caught Behind 5%  | Run Out 3% | Stumped 2% | Hit Wicket 5%
        double pBowled = isPace ? 0.18 : (isSpin ? 0.15 : 0.17);
        double pCaught = 0.50;  // most common (~55-65% in reality)
        double pLBW = isPace ? 0.15 : (isSpin ? 0.20 : 0.15);
        double pStumped = isSpin ? 0.02 : 0.01;  // REDUCED from 10%/2% - stumping is rare event
        double pRunOut = 0.03;  // REDUCED from 8% - only increases with pressure
        double pCaughtBehind = isPace ? 0.10 : 0.05;
        double pHitWicket = isPace ? 0.03 : 0.05;  // Spin batters more likely to hit wicket

        // Fielding quality affects catches (modest, not dominant)
        pCaught += fieldingAvg * 0.001;  // REDUCED from 0.002
        pCaughtBehind += keeperSkill * 0.0005;  // REDUCED from 0.001
        pStumped += keeperSkill * 0.0005;  // REDUCED from 0.0015 - keeper doesn't massively boost stumping

        // Pitch effects (subtle, not dramatic)
        if ("GREEN".equals(ctx.pitchType) || "BOUNCY".equals(ctx.pitchType)) {
            pCaughtBehind += 0.04;
            pBowled += 0.03;
        }
        if ("DUSTY".equals(ctx.pitchType) || "DRY".equals(ctx.pitchType)) {
            pStumped += 0.01;  // REDUCED from 0.04 - stumping boost is subtle
            pLBW += 0.02;  // REDUCED from 0.03
        }

        // RUN-OUT PRESSURE: Applies to BOTH first and second innings
        // Second innings (chasing): Pressure to accelerate = desperate running
        // First innings (batting first): Pressure from collapsing wickets = rash running
        double runOutPressure = 0.0;
        if (ctx.isChasing) {
            // Chase: higher required rate = higher run-out risk from desperate running
            double runsNeeded = ctx.target > 0 ? ctx.target - totalRuns : 0;
            double oversCompleted = legalBallsBowled / 6.0;
            double oversRemaining = ctx.maxOvers - oversCompleted;
            double requiredRate = oversRemaining > 0 ? runsNeeded / oversRemaining : 0;
            double parRate = ctx.target > 0 ? ctx.target / ctx.maxOvers : 0;
            if (requiredRate > parRate * 1.5) {
                runOutPressure = 0.04;  // Very desperate chase
            } else if (requiredRate > parRate * 1.2) {
                runOutPressure = 0.02;  // Moderate chase pressure
            }
        } else {
            // First innings: rapid wicket loss creates pressure for risky running
            double oversCompleted = legalBallsBowled / 6.0;
            double wicketRate = oversCompleted > 0 ? totalWickets / oversCompleted : 0;
            if (wicketRate > 1.5) {  // More than 1.5 wickets per over = collapse pressure
                runOutPressure = 0.02;  // Desperate batting, risky running
            } else if (wicketRate > 1.0) {  // 1+ wickets per over
                runOutPressure = 0.01;  // Mild pressure
            }
        }
        pRunOut += runOutPressure;

        // CRITICAL: Normalize probabilities to ensure they sum to 1.0
        double dTotal = pBowled + pCaught + pLBW + pStumped + pRunOut + pCaughtBehind + pHitWicket;
        if (dTotal > 1.0) {
            // Scale down all probabilities proportionally to fit within 1.0
            double scale = 1.0 / dTotal;
            pBowled *= scale;
            pCaught *= scale;
            pLBW *= scale;
            pStumped *= scale;
            pRunOut *= scale;
            pCaughtBehind *= scale;
            pHitWicket *= scale;
            dTotal = 1.0;
        }
        double dRoll = rng.nextDouble() * dTotal;
        double dCum = 0;

        // Select random fielder from bowling lineup (excluding keeper)
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
                    batter.player.getFirstName() + " " + batter.player.getLastName() + " bowled by " +
                            bowler.getFirstName() + " " + bowler.getLastName());
        } else if (dRoll < (dCum += pCaught)) {
            return new DismissalInfo("CAUGHT", randomFielder,
                    "Caught by " + randomFielder.getFirstName() + " " + randomFielder.getLastName() +
                            " off " + bowler.getFirstName() + " " + bowler.getLastName());
        } else if (dRoll < (dCum += pLBW)) {
            return new DismissalInfo("LBW", null,
                    "LBW! Trapped in front by " + bowler.getFirstName() + " " + bowler.getLastName());
        } else if (dRoll < (dCum += pStumped)) {
            return new DismissalInfo("STUMPED", keeper,
                    "Stumped by " + (keeper != null ? keeper.getFirstName() + " " + keeper.getLastName() : "keeper") +
                            " off " + bowler.getFirstName() + " " + bowler.getLastName());
        } else if (dRoll < (dCum += pRunOut)) {
            return new DismissalInfo("RUN_OUT", randomFielder,
                    "Run out! Direct hit by " + randomFielder.getFirstName() + " " + randomFielder.getLastName());
        } else if (dRoll < (dCum += pCaughtBehind)) {
            return new DismissalInfo("CAUGHT_BEHIND", keeper,
                    "Caught behind by " + (keeper != null ? keeper.getFirstName() + " " + keeper.getLastName() : "keeper") +
                            " off " + bowler.getFirstName() + " " + bowler.getLastName());
        } else {
            return new DismissalInfo("HIT_WICKET", null,
                    "Hit wicket! " + batter.player.getFirstName() + " " + batter.player.getLastName() +
                            " dislodged the bails");
        }
    }

    // ─── Pitch effects ──────────────────────────────────────────

    private PitchEffect getPitchEffect(String pitchType, String bowlType) {
        //                         F    FM   MF    M    FS   WS   (null/other)
        // GREEN:  Seam paradise — M hits seam consistently, F gets raw pace+seam,
        //         FM/MF less seam control than pure M, spin irrelevant
        // DUSTY:  Spin heaven — FS grips most, WS gets big turn, pace struggles
        // FLAT:   Batting paradise — everyone suffers, pace hit hardest
        // BOUNCY: Extra bounce — raw pace lethal, medium negated, spin useless
        // UNEVEN: Unpredictable — pace benefits most, MF > M (needs some pace),
        //         WS gets variable bounce/turn, FS decent
        // DRY:    Deteriorating — WS murders (sharp turn), FS good, medium can cutters
        // SLOW:   Low bounce — kills pace, helps spin, medium survives

        String type = bowlType != null ? bowlType : "NONE";
        double bowlerBonus = switch (pitchType != null ? pitchType : "STANDARD") {
            case "GREEN" -> switch (type) {
                case "F"  -> 14;   // express pace + seam = lethal
                case "M"  -> 12;   // medium pace hits seam consistently, very effective
                case "MF" -> 10;   // decent pace + some seam control
                case "FM" -> 9;    // relies more on pace than seam
                case "FS" -> 2;    // minimal help
                case "WS" -> 1;    // almost nothing
                default   -> 6;
            };
            case "DUSTY" -> switch (type) {
                case "FS" -> 14;   // finger spin grips and turns sharply
                case "WS" -> 12;   // wrist spin gets big turn but less control
                case "M"  -> 5;    // medium can use cutters
                case "MF" -> 4;    // some cutters possible
                case "FM" -> 3;    // pace is wasted
                case "F"  -> 2;    // raw pace barely helps on dust
                default   -> 5;
            };
            case "FLAT" -> switch (type) {
                case "F"  -> -5;   // pace comes on nicely for batters
                case "FM" -> -6;   // nothing for anyone
                case "MF" -> -6;
                case "M"  -> -7;   // medium on flat = cannon fodder
                case "FS" -> -5;   // no turn but at least flight
                case "WS" -> -4;   // wrist spin variation can still deceive
                default   -> -6;
            };
            case "BOUNCY" -> switch (type) {
                case "F"  -> 12;   // express pace + extra bounce = deadly
                case "FM" -> 8;    // good pace, uses bounce
                case "MF" -> 7;    // decent
                case "M"  -> 4;    // not enough pace to exploit bounce
                case "FS" -> 1;    // nothing
                case "WS" -> 0;    // useless
                default   -> 5;
            };
            case "UNEVEN" -> switch (type) {
                case "F"  -> 10;   // unpredictable bounce + pace = danger
                case "MF" -> 9;    // variable bounce needs some pace to exploit
                case "FM" -> 8;    // similar to MF
                case "M"  -> 6;    // variable but lacks pace to threaten
                case "WS" -> 7;    // wrist spin extracts variable turn/bounce
                case "FS" -> 6;    // finger spin decent on uneven
                default   -> 7;
            };
            case "DRY" -> switch (type) {
                case "WS" -> 12;   // wrist spin murders on dry — sharp turn, variable bounce
                case "FS" -> 10;   // finger spin grips well
                case "M"  -> 5;    // cutters effective
                case "MF" -> 4;    // some cutters
                case "FM" -> 2;    // pace mostly wasted
                case "F"  -> 1;    // raw pace ineffective on dry
                default   -> 4;
            };
            case "SLOW" -> switch (type) {
                case "FS" -> 7;    // finger spin thrives — low bounce, grip
                case "WS" -> 5;    // wrist spin decent but less bounce to work with
                case "M"  -> 2;    // medium survives, can vary pace
                case "MF" -> 0;    // pace negated
                case "FM" -> -2;   // mostly wasted
                case "F"  -> -4;   // fast bowlers hate slow tracks
                default   -> 1;
            };
            default -> 0; // STANDARD — neutral
        };

        return new PitchEffect(bowlerBonus);
    }

    // ─── Weather effects ────────────────────────────────────────

    private WeatherEffect getWeatherEffect(String condition, int temperature, String bowlType) {
        //                          F    FM   MF    M    FS   WS
        // Sunny:    Hard surface, true bounce — pace nullified, spin unaffected
        // Hot:      Reverse swing helps F/FM, dry ball grips for spin
        // Partly:   Mild conventional swing for seamers
        // Overcast: Swing paradise — F kings, FM good, M decent with cutters, spin irrelevant
        // Light Rain: Seam movement — F/FM/MF benefit, wet ball kills spin
        // Heavy Rain: Slippery ball — pace gets some seam, spin can't grip at all
        // Windy:    Drift helps spin, swing aids faster bowlers
        // Foggy:    Visibility issues — pace threatening, spin deceptive with flight

        String type = bowlType != null ? bowlType : "NONE";
        double battingMod = 0;
        double bowlingMod = 0;

        switch (condition != null ? condition : "Sunny") {
            case "Sunny":
                battingMod = 3;
                bowlingMod = switch (type) {
                    case "F"  -> -3;   // hard surface, ball comes on nicely for batters
                    case "FM" -> -2;   // same issue
                    case "MF" -> -1;   // slightly less affected
                    case "M"  -> -1;   // nothing happening
                    case "FS" -> 0;    // unaffected
                    case "WS" -> 0;    // unaffected
                    default   -> -1;
                };
                break;
            case "Hot & Humid":
                battingMod = 1;
                bowlingMod = switch (type) {
                    case "F"  -> 2;    // reverse swing kicks in with old ball
                    case "FM" -> 1;    // some reverse
                    case "MF" -> 0;    // not enough pace for reverse
                    case "M"  -> -1;   // struggles
                    case "FS" -> 2;    // dry ball grips well, finger spin effective
                    case "WS" -> 1;    // dry ball helps wrist spin too
                    default   -> 0;
                };
                break;
            case "Partly Cloudy":
                battingMod = 1;
                bowlingMod = switch (type) {
                    case "F"  -> 3;    // mild conventional swing
                    case "FM" -> 2;    // some swing
                    case "MF" -> 2;    // decent movement
                    case "M"  -> 1;    // slight help
                    case "FS" -> 1;    // minimal
                    case "WS" -> 0;    // nothing special
                    default   -> 1;
                };
                break;
            case "Overcast":
                battingMod = -3;
                bowlingMod = switch (type) {
                    case "F"  -> 10;   // king of overcast — heavy swing + pace
                    case "FM" -> 7;    // good swing, pace helps
                    case "MF" -> 6;    // decent swing
                    case "M"  -> 5;    // cutters + wobble seam effective
                    case "FS" -> 1;    // almost nothing
                    case "WS" -> 0;    // irrelevant
                    default   -> 4;
                };
                break;
            case "Light Rain":
                battingMod = -4;
                bowlingMod = switch (type) {
                    case "F"  -> 8;    // seam movement + skiddy surface
                    case "FM" -> 6;    // good seam
                    case "MF" -> 5;    // decent
                    case "M"  -> 4;    // wet ball cutters
                    case "FS" -> 0;    // wet ball kills finger spin grip
                    case "WS" -> -1;   // can't grip at all
                    default   -> 3;
                };
                break;
            case "Heavy Rain":
                battingMod = -6;
                bowlingMod = switch (type) {
                    case "F"  -> 5;    // slippery but pace still threatens
                    case "FM" -> 4;    // some threat
                    case "MF" -> 3;    // manages
                    case "M"  -> 2;    // reduced effectiveness
                    case "FS" -> -2;   // can't grip the wet ball
                    case "WS" -> -3;   // worst affected — zero grip
                    default   -> 2;
                };
                break;
            case "Windy":
                battingMod = -1;
                bowlingMod = switch (type) {
                    case "F"  -> 4;    // wind + pace = late swing
                    case "FM" -> 3;    // good swing with wind
                    case "MF" -> 2;    // some help
                    case "M"  -> 1;    // wind drifts cutters
                    case "FS" -> 2;    // drift helps flight and dip
                    case "WS" -> 3;    // wind drift makes wrist spin unpredictable
                    default   -> 2;
                };
                break;
            case "Foggy":
                battingMod = -5;
                bowlingMod = switch (type) {
                    case "F"  -> 4;    // pace + visibility = scary
                    case "FM" -> 3;    // threatening
                    case "MF" -> 2;    // decent
                    case "M"  -> 1;    // not threatening enough
                    case "FS" -> 3;    // flight deception in poor visibility
                    case "WS" -> 3;    // variations hard to pick in fog
                    default   -> 2;
                };
                break;
        }

        // Temperature extremes affect fatigue
        if (temperature > 38) {
            battingMod -= 2;
            bowlingMod -= 1;
        } else if (temperature < 12) {
            bowlingMod -= 1;
        }

        return new WeatherEffect(battingMod, bowlingMod);
    }

    // ─── Batter pitch modifier ──────────────────────────────────

    private double getBatterPitchModifier(String pitchType, String batHand, int batRating, int experience) {
        boolean isLH = "LH".equals(batHand);
        // High skill batters (60+) adapt better to difficult pitches
        double skillAdapt = batRating >= 60 ? 2.0 : (batRating >= 40 ? 1.0 : 0);
        // Experienced batters (15+) read conditions better
        double expAdapt = experience >= 15 ? 2.0 : (experience >= 8 ? 1.0 : -1.0);

        return switch (pitchType != null ? pitchType : "STANDARD") {
            // GREEN: LH angled stance plays swing better; skilled batters use technique
            case "GREEN" -> (isLH ? 3 : 0) + skillAdapt * 0.5 + expAdapt * 0.5;
            // DUSTY: LH exposed to off-spin/finger-spin turning in; skilled survive
            case "DUSTY" -> (isLH ? -4 : -1) + skillAdapt + expAdapt * 0.5;
            // FLAT: Batting paradise — high-rated batters feast, low-rated still score
            case "FLAT" -> 4 + skillAdapt * 1.5;
            // BOUNCY: Extra bounce troubles everyone; LH slightly worse (angled bat)
            case "BOUNCY" -> (isLH ? -2 : -1) + skillAdapt + expAdapt;
            // UNEVEN: Unpredictable — experience and technique crucial
            case "UNEVEN" -> -2 + skillAdapt + expAdapt;
            // DRY: Turn + variable bounce; LH struggles vs spin turning in
            case "DRY" -> (isLH ? -3 : 0) + skillAdapt * 0.8 + expAdapt * 0.5;
            // SLOW: Low bounce = easier to play; high-rated can manipulate pace
            case "SLOW" -> 2 + skillAdapt;
            // STANDARD: Neutral
            default -> 0;
        };
    }

    // ─── Batter weather modifier ────────────────────────────────

    private double getBatterWeatherModifier(String condition, int temperature, int experience, int batRating) {
        // Experienced (15+) and skilled (50+) batters cope better in tough conditions
        double expFactor = experience >= 15 ? 2.0 : (experience >= 8 ? 0.5 : -1.5);
        double skillFactor = batRating >= 50 ? 1.0 : (batRating >= 30 ? 0 : -1.0);

        return switch (condition != null ? condition : "Sunny") {
            // Sunny: Everyone bats well, no special advantage
            case "Sunny" -> 0;
            // Hot: Stamina matters, slight fatigue penalty baked elsewhere
            case "Hot & Humid" -> 0;
            // Partly Cloudy: Benign, minimal impact
            case "Partly Cloudy" -> 0;
            // Overcast: Swing makes batting hard — experience helps read movement
            case "Overcast" -> -1 + expFactor * 0.8 + skillFactor * 0.5;
            // Light Rain: Slippery, seaming — experienced batters survive
            case "Light Rain" -> -2 + expFactor + skillFactor * 0.5;
            // Heavy Rain: Extremely tough — only elite experienced batters cope
            case "Heavy Rain" -> -3 + expFactor * 1.2 + skillFactor * 0.5;
            // Windy: Timing disrupted — skilled batters adjust, beginners mistime
            case "Windy" -> -1 + skillFactor + expFactor * 0.3;
            // Foggy: Can't see the ball well — experience is king
            case "Foggy" -> -2 + expFactor * 1.0 + skillFactor * 0.3;
            default -> 0;
        };
    }

    // ─── Bowler type matchup ────────────────────────────────────

    private double getBowlerTypeMatchup(String bowlType, String batHand, String pitchType) {
        if (bowlType == null) return 0;
        double bonus = 0;

        // Spin vs left-handers (traditional weakness)
        if ("LH".equals(batHand)) {
            if ("FS".equals(bowlType)) bonus += 3;  // finger spin to left-hand
            if ("WS".equals(bowlType)) bonus -= 1;  // wrist spin less effective
        }

        // Pace on bouncy/green
        if (("F".equals(bowlType) || "FM".equals(bowlType)) &&
                ("GREEN".equals(pitchType) || "BOUNCY".equals(pitchType))) {
            bonus += 3;
        }

        // Spin on dusty/dry
        if (("FS".equals(bowlType) || "WS".equals(bowlType)) &&
                ("DUSTY".equals(pitchType) || "DRY".equals(pitchType))) {
            bonus += 4;
        }

        // Medium pace (M/MF) — less impactful overall
        if ("M".equals(bowlType) || "MF".equals(bowlType)) {
            bonus -= 1;
        }

        return bonus;
    }

    // ─── Innings phase modifier ─────────────────────────────────

    private double getPhaseModifier(String format, int overNumber, int maxOvers) {
        if ("T20".equalsIgnoreCase(format)) {
            // T20 overs 1-6 = powerplay (fielding restrictions → runs flow)
            // T20 overs 7-15 = middle (consolidation, spinners dominate)
            // T20 overs 16-20 = death (slog, high scoring + high risk)
            if (overNumber <= 6) return 2.5;    // Slightly reduced from 3
            if (overNumber <= 15) return -0.5;  // Slightly reduced from -1
            return 4;                           // Slightly reduced from 5
        } else if ("ODI".equalsIgnoreCase(format)) {
            // ODI overs 1-10 = powerplay (aggressive runs, low risk tolerance)
            // ODI overs 11-30 = consolidation (steady phase, normal batting)
            // ODI overs 31-40 = buildup (controlled acceleration)
            // ODI overs 41-50 = death (aggressive push with calculated risk)
            // FIXED: Removed harsh -2 penalty from overs 11-30
            if (overNumber <= 10) return 1.0;   // Reduced from 1.5 (powerplay caution)
            if (overNumber <= 30) return 0;     // Changed from -2 (neutral, no penalty)
            if (overNumber <= 40) return 0.5;   // Reduced from 1
            return 2.5;                         // Reduced from 3.5
        } else if ("FC".equalsIgnoreCase(format)) {
            // FC: Each session is 50 overs. Phase modifiers within a session:
            // Session overs 1-10 = new ball/opening: bowlers have advantage, wickets likely
            // Session overs 11-30 = settling: batsmen consolidate, balanced conditions
            // Session overs 31-45 = middle: steady scoring, slight batsman advantage
            // Session overs 46-50 = end of session: slightly tired bowlers, batting boost
            int sessionOver = ((overNumber - 1) % 50) + 1;
            if (sessionOver <= 10) return 0.8;   // Reduced from 1.0 (new ball slightly less harsh)
            if (sessionOver <= 30) return -1.0;  // Reduced from -2.5 (balanced consolidation)
            if (sessionOver <= 45) return 0;     // Changed from -0.5 (neutral middle phase)
            return 1.0;                          // Reduced from 1.5 (end of session push)
        }
        // Fallback (shouldn't reach here for this engine)
        return 0;
    }

    // ─── Aggression modifier ────────────────────────────────────

    private double getAggressionModifier(String aggression) {
        return switch (aggression != null ? aggression : "N") {
            case "A" -> 1.5;   // Aggressive
            case "D" -> -1.0;  // Defensive
            default -> 0.0;    // Normal
        };
    }

    // ─── Team fielding & keeper ─────────────────────────────────

    /**
     * Non-linear "set batter" progression by format.
     * Returns roughly -0.15 (very new batter) to +0.85 (fully set, diminishing returns).
     * The curve avoids treating 50 and 100 balls as the same batting state.
     */
    private double getSetBatsmanFactor(String format, int ballsFaced, String aggression) {
        int balls = Math.max(0, ballsFaced);
        double set;

        if ("T20".equalsIgnoreCase(format)) {
            if (balls <= 6) set = -0.10;
            else if (balls <= 15) set = 0.22 * (balls - 6) / 9.0;
            else if (balls <= 30) set = 0.22 + 0.30 * (balls - 15) / 15.0;
            else set = 0.52 + 0.10 * (1.0 - Math.exp(-(balls - 30) / 16.0));
        } else if ("FC".equalsIgnoreCase(format)) {
            if (balls <= 15) set = -0.15;
            else if (balls <= 50) set = 0.32 * (balls - 15) / 35.0;
            else if (balls <= 120) set = 0.32 + 0.38 * (balls - 50) / 70.0;
            else set = 0.70 + 0.12 * (1.0 - Math.exp(-(balls - 120) / 55.0));
        } else { // ODI
            // Anchors:
            // ~30 balls = set, ~50 balls = controlled acceleration, 90+ = measured push.
            if (balls <= 10) set = -0.12;
            else if (balls <= 30) set = 0.22 * (balls - 10) / 20.0;
            else if (balls <= 50) set = 0.22 + 0.24 * (balls - 30) / 20.0;
            else if (balls <= 90) set = 0.46 + 0.26 * (balls - 50) / 40.0;
            else if (balls <= 120) set = 0.72 + 0.08 * (balls - 90) / 30.0;
            else set = 0.80 + 0.04 * (1.0 - Math.exp(-(balls - 120) / 45.0));
        }

        // Aggressive batters capitalize slightly more once set; defensive slightly less.
        if ("A".equals(aggression)) set *= 1.06;
        else if ("D".equals(aggression)) set *= 0.94;

        return Math.max(-0.18, Math.min(set, 0.88));
    }

    /**
     * Strike farming: stronger batter attempts to keep strike when partner is much weaker.
     * Trigger threshold is weaker batter <= 40% of striker skill.
     * Uses probabilistic attempt + success so farming is never guaranteed.
     */
    private boolean tryStrikeFarmingKeepStrike(
            SimContext ctx,
            BatsmanState striker,
            BatsmanState nonStriker,
            BattingScorecard strikerCard,
            int legalBallsThisOver,
            int overNumber,
            boolean endOfOver,
            Random rng) {

        if (striker == null || nonStriker == null || rng == null) return false;

        double strikerSkill = Math.max(1.0, striker.player.getBatRating());
        double partnerSkill = Math.max(1.0, nonStriker.player.getBatRating());
        if (strikerSkill <= partnerSkill) return false; // only the better batter farms

        double ratio = partnerSkill / strikerSkill;
        if (ratio > 0.40) return false; // not a big enough skill gap

        // 0 at threshold (40%), up to 1 as partner gets much weaker.
        double gapIntensity = Math.max(0.0, Math.min(1.0, (0.40 - ratio) / 0.40));

        int ballsFaced = strikerCard != null && strikerCard.getBallsFaced() != null
                ? strikerCard.getBallsFaced() : 0;
        double setFactor = getSetBatsmanFactor(ctx.format, ballsFaced, striker.lineupPlayer.getBatAggression());
        double positiveSet = Math.max(0.0, setFactor);

        // At 40% gap they still try, but not always. Bigger gap -> stronger intent.
        double attemptChance = 0.45 + (0.30 * gapIntensity);
        double successChance = 0.40 + (0.35 * gapIntensity);

        // Format/phase tuning:
        // T20 -> strongest farming (especially death overs)
        // ODI -> balanced farming
        // FC  -> conservative farming (especially early session)
        if ("T20".equalsIgnoreCase(ctx.format)) {
            attemptChance += 0.05;
            successChance += 0.04;
            if (overNumber >= 16) {
                attemptChance += 0.08;
                successChance += 0.06;
            } else if (overNumber <= 6) {
                attemptChance -= 0.04;
                successChance -= 0.03;
            }
        } else if ("FC".equalsIgnoreCase(ctx.format)) {
            attemptChance -= 0.08;
            successChance -= 0.07;
            int sessionOver = ((Math.max(1, overNumber) - 1) % 50) + 1;
            if (sessionOver <= 15) {
                attemptChance -= 0.05;
                successChance -= 0.05;
            } else if (sessionOver >= 46) {
                attemptChance += 0.03;
                successChance += 0.02;
            }
        } else { // ODI
            if (overNumber >= 41) {
                attemptChance += 0.06;
                successChance += 0.05;
            } else if (overNumber <= 10) {
                attemptChance -= 0.03;
                successChance -= 0.02;
            }
        }

        if (endOfOver || legalBallsThisOver >= 5) attemptChance += 0.12;
        attemptChance += 0.06 * positiveSet;
        if ("D".equals(striker.lineupPlayer.getBatAggression())) attemptChance += 0.04;
        if ("A".equals(striker.lineupPlayer.getBatAggression())) attemptChance -= 0.02;
        attemptChance = Math.max(0.15, Math.min(attemptChance, 0.88));

        if (rng.nextDouble() >= attemptChance) return false;

        // Farming can fail: execution pressure / ball quality.
        if (endOfOver || legalBallsThisOver >= 5) successChance += 0.08;
        successChance += 0.05 * positiveSet;
        successChance = Math.max(0.20, Math.min(successChance, 0.90));

        return rng.nextDouble() < successChance;
    }

    private double calculateTeamFielding(MatchLineup bowlingLineup) {
        double sum = 0;
        int count = 0;
        for (LineupPlayer lp : bowlingLineup.getPlayers()) {
            sum += lp.getPlayer().getFldRating();
            count++;
        }
        return count > 0 ? sum / count : 30;
    }

    private double getKeeperRating(MatchLineup bowlingLineup) {
        Player keeper = bowlingLineup.getKeeper();
        if (keeper != null) return keeper.getKeeperRating();
        // Fallback: find best keeper in lineup
        return bowlingLineup.getPlayers().stream()
                .mapToDouble(lp -> lp.getPlayer().getKeeperRating())
                .max().orElse(20);
    }

    // ─── Fallback bowler selection ──────────────────────────────

    private Player pickFallbackBowler(SimContext ctx, Map<UUID, Integer> bowlerBallCounts, Player previousBowler) {
        int maxBalls = ctx.maxPerBowler * 6;
        List<LineupPlayer> candidates = new ArrayList<>();

        for (LineupPlayer lp : ctx.bowlingLineup.getPlayers()) {
            Player p = lp.getPlayer();
            if (previousBowler != null && p.getId().equals(previousBowler.getId())) continue;
            int bowled = bowlerBallCounts.getOrDefault(p.getId(), 0);
            if (bowled >= maxBalls) continue;
            // Prefer actual bowlers/all-rounders
            if (p.getBowlRating() >= 15) {
                candidates.add(lp);
            }
        }

        // If no bowlers available, use anyone except previous
        if (candidates.isEmpty()) {
            for (LineupPlayer lp : ctx.bowlingLineup.getPlayers()) {
                Player p = lp.getPlayer();
                if (previousBowler != null && p.getId().equals(previousBowler.getId())) continue;
                int bowled = bowlerBallCounts.getOrDefault(p.getId(), 0);
                if (bowled < maxBalls) {
                    candidates.add(lp);
                }
            }
        }

        if (candidates.isEmpty()) {
            // Last resort: anyone at all
            return ctx.bowlingLineup.getPlayers().get(0).getPlayer();
        }

        // Sort by bowling rating descending, pick best available
        candidates.sort((a, b) -> Integer.compare(b.getPlayer().getBowlRating(), a.getPlayer().getBowlRating()));
        return candidates.get(0).getPlayer();
    }

    // ─── Toss decision logic ────────────────────────────────────

    private String decideToss(MatchLineup lineup, String pitchType, String weatherCondition, Random rng) {
        // If lineup has explicit toss choice, use it
        if (lineup.getBatOrBowl() != null && !lineup.getBatOrBowl().isEmpty()) {
            return lineup.getBatOrBowl().toUpperCase();
        }

        // AI decision for bots based on conditions
        int batScore = 0;

        // Pitch analysis
        switch (pitchType != null ? pitchType : "STANDARD") {
            case "FLAT" -> batScore += 3;
            case "GREEN", "BOUNCY" -> batScore -= 3;
            case "DUSTY", "DRY" -> batScore -= 1; // deteriorates = bat first
            case "UNEVEN" -> batScore -= 2;
            default -> batScore += 0;
        }

        // Weather
        if ("Overcast".equals(weatherCondition) || "Light Rain".equals(weatherCondition)) {
            batScore -= 2;
        } else if ("Sunny".equals(weatherCondition)) {
            batScore += 1;
        }

        // Small random factor
        batScore += rng.nextInt(3) - 1;

        return batScore >= 0 ? "BAT" : "BOWL";
    }

    // ─── FC (First-Class) Day-Based Match Simulation ──────────

    /** Resume state for mid-innings day breaks. */
    private static class FCResumeState {
        UUID strikerId;
        UUID nonStrikerId;
        int nextBatIdx;
        UUID previousBowlerId;
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
        r.strikerId = UUID.fromString(parts[0]);
        r.nonStrikerId = UUID.fromString(parts[1]);
        r.nextBatIdx = Integer.parseInt(parts[2]);
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
        int full = (int) Math.floor(totalOvers);
        int partial = (int) Math.round((totalOvers - full) * 10);
        return full * 6 + partial;
    }

    private boolean isHumanTeam(Team team) {
        return team.getOwner() != null;
    }

    private Map<UUID, MatchFCStrategy> loadFCStrategies(Fixture fixture) {
        Map<UUID, MatchFCStrategy> byTeam = new HashMap<>();
        for (MatchFCStrategy strategy : matchFCStrategyRepository.findByFixtureId(fixture.getId())) {
            byTeam.put(strategy.getTeam().getId(), strategy);
        }
        return byTeam;
    }

    private Integer getDeclareInn1(Fixture fixture, Map<UUID, MatchFCStrategy> strategiesByTeam, Team team) {
        MatchFCStrategy strategy = strategiesByTeam.get(team.getId());
        return strategy != null ? strategy.getDeclareInn1() : fixture.getFcDeclareInn1();
    }

    private Integer getDeclareInn2Lead(Fixture fixture, Map<UUID, MatchFCStrategy> strategiesByTeam, Team team) {
        MatchFCStrategy strategy = strategiesByTeam.get(team.getId());
        return strategy != null ? strategy.getDeclareInn2Lead() : fixture.getFcDeclareInn2Lead();
    }

    private Integer getDeclareInn3Lead(Fixture fixture, Map<UUID, MatchFCStrategy> strategiesByTeam, Team team) {
        MatchFCStrategy strategy = strategiesByTeam.get(team.getId());
        return strategy != null ? strategy.getDeclareInn3Lead() : fixture.getFcDeclareInn3Lead();
    }

    private Boolean getFollowOn(Fixture fixture, Map<UUID, MatchFCStrategy> strategiesByTeam, Team team) {
        MatchFCStrategy strategy = strategiesByTeam.get(team.getId());
        return strategy != null ? strategy.getFollowOn() : fixture.getFcFollowOn();
    }

    private MatchResult saveFCDay1Complete(Fixture fixture, MatchResult result) {
        result.setResultType("PENDING");
        fixture.setFcDay(1);
        fixture.setStatus("FC_DAY1_COMPLETE");
        fixtureRepository.save(fixture);
        return matchResultRepository.save(result);
    }

    /**
     * Save FC match when Day 2 overs are exhausted but match not complete.
     * Increments fcDay to 2 so scheduler doesn't retry Day 2 indefinitely.
     */
    private MatchResult saveFCDay2Paused(Fixture fixture, MatchResult result) {
        result.setResultType("PENDING");
        fixture.setFcDay(2);  // Mark Day 2 as attempted
        fixture.setStatus("FC_DAY1_COMPLETE");  // Keep status but fcDay prevents retry
        fixtureRepository.save(fixture);
        return matchResultRepository.save(result);
    }

    private MatchResult finalizeFCMatch(Fixture fixture, MatchResult result, Random rng) {
        result.setManOfMatch(pickManOfMatch(result, rng));
        fixture.setFcDay(2);
        fixture.setStatus("COMPLETED");
        fixtureRepository.save(fixture);
        boolean isSim = fixture.getSimSessionId() != null;
        // NOTE: logMatchActivity is NOT called here — deferred to MatchScheduler Pass 2.
        if (!isSim && fixture.getLeague() != null) {
            updateMoraleAndFans(result, fixture, "FC");
            updatePlayerStats(result, "FC");
            distributeGateMoney(result, fixture);
        }
        MatchResult saved = matchResultRepository.save(result);
        if (!isSim) {
            fixtureService.applyPendingSwap(fixture.getId(), saved);
        }
        return saved;
    }

    /** Compute 3rd-innings declaration target. Returns {canDeclare ? 1 : 0, declareAtRuns}. */
    private long[] computeInn3Declaration(Fixture fixture, Map<UUID, MatchFCStrategy> strategiesByTeam,
                                          List<Innings> inningsList, Team battingTeam, Team battingFirst, Team battingSecond) {
        int i1 = inningsList.get(0).getTotalRuns();
        int i2 = inningsList.get(1).getTotalRuns();
        boolean fo = battingTeam.getId().equals(battingSecond.getId());
        boolean human = isHumanTeam(battingTeam);
        Integer declareLead = getDeclareInn3Lead(fixture, strategiesByTeam, battingTeam);
        if (human && declareLead != null && declareLead > 0) {
            int da = fo ? (i1 - i2 + declareLead)
                        : (i2 - i1 + declareLead);
            return new long[]{1, Math.max(1, da)};
        }
        if (!human) {
            int aiLead = 250;
            int da = fo ? (i1 - i2 + aiLead) : (i2 - i1 + aiLead);
            return new long[]{1, Math.max(1, da)};
        }
        return new long[]{0, 0};
    }

    /** Compute chase target for 4th innings. */
    private int computeFCChaseTarget(List<Innings> inningsList, Team battingSecond) {
        int i1 = inningsList.get(0).getTotalRuns();
        int i2 = inningsList.get(1).getTotalRuns();
        int i3 = inningsList.get(2).getTotalRuns();
        boolean fo = inningsList.get(2).getBattingTeam().getId().equals(battingSecond.getId());
        return fo ? (i2 + i3) - i1 + 1 : (i1 + i3) - i2 + 1;
    }

    /** Determine FC result from completed innings and finalize the match. */
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
            int i1 = inns.get(0).getTotalRuns(), i2 = inns.get(1).getTotalRuns(), i3 = inns.get(2).getTotalRuns();
            int overallLead = fo ? i1 - (i2 + i3) : (i1 + i3) - i2;
            if ((fo && overallLead > 0) || (!fo && overallLead < 0)) {
                determineFCResult(result, i1, i2, i3, 0, battingFirst, battingSecond, fo, true);
            } else {
                result.setResultType("DRAW");
            }
        } else {
            result.setResultType("DRAW");
        }
        return finalizeFCMatch(fixture, result, rng);
    }

    /**
     * FC Day 1: Fresh match, simulate up to 150 overs (3 sessions).
     */
    private MatchResult simulateFCDay1(Fixture fixture, String format, Team homeTeam, Team awayTeam) {
        MatchLineup homeLineup = getOrGenerateLineup(fixture, homeTeam, format);
        MatchLineup awayLineup = getOrGenerateLineup(fixture, awayTeam, format);

        Map<String, Object> weather = weatherService.getWeather(homeTeam.getCountry(), fixture.getMatchDate());
        String condition = (String) weather.get("condition");
        int temperature = (int) weather.get("temperature");
        String pitchType = fixture.getPitchType();

        Random rng = new Random((fixture.getId().toString() + fixture.getMatchDate()).hashCode());
        boolean homeToss = rng.nextBoolean();
        Team tossWinner = homeToss ? homeTeam : awayTeam;
        MatchLineup tossLineup = homeToss ? homeLineup : awayLineup;
        String tossDecision = decideToss(tossLineup, pitchType, condition, rng);

        Team battingFirst, battingSecond;
        MatchLineup bat1Lineup, bat2Lineup;
        if ("BAT".equals(tossDecision)) {
            battingFirst = tossWinner;
            battingSecond = tossWinner.getId().equals(homeTeam.getId()) ? awayTeam : homeTeam;
            bat1Lineup = tossWinner.getId().equals(homeTeam.getId()) ? homeLineup : awayLineup;
            bat2Lineup = tossWinner.getId().equals(homeTeam.getId()) ? awayLineup : homeLineup;
        } else {
            battingSecond = tossWinner;
            battingFirst = tossWinner.getId().equals(homeTeam.getId()) ? awayTeam : homeTeam;
            bat2Lineup = tossWinner.getId().equals(homeTeam.getId()) ? homeLineup : awayLineup;
            bat1Lineup = tossWinner.getId().equals(homeTeam.getId()) ? awayLineup : homeLineup;
        }

        MatchResult result = MatchResult.builder()
                .fixture(fixture)
                .tossWinner(tossWinner)
                .tossDecision(tossDecision)
                .build();

        return runFCInningsLoop(fixture, result, battingFirst, battingSecond,
                bat1Lineup, bat2Lineup, 150, rng, pitchType, condition, temperature, format);
    }

    /**
     * FC Day 2: Resume from Day 1 state, simulate remaining overs.
     */
    private MatchResult simulateFCDay2(Fixture fixture, String format) {
        MatchResult result = matchResultRepository.findByFixtureIdWithInnings(fixture.getId())
                .orElseThrow(() -> new IllegalStateException("No Day 1 result found"));

        Team homeTeam = fixture.getHomeTeam();
        Team awayTeam = fixture.getAwayTeam();
        MatchLineup homeLineup = getOrGenerateLineup(fixture, homeTeam, format);
        MatchLineup awayLineup = getOrGenerateLineup(fixture, awayTeam, format);

        List<Innings> inns = result.getInningsList();
        if (inns.isEmpty()) throw new IllegalStateException("No innings from Day 1");

        Team battingFirst = inns.get(0).getBattingTeam();
        Team battingSecond = inns.get(0).getBowlingTeam();
        MatchLineup bat1Lineup = battingFirst.getId().equals(homeTeam.getId()) ? homeLineup : awayLineup;
        MatchLineup bat2Lineup = battingSecond.getId().equals(homeTeam.getId()) ? homeLineup : awayLineup;

        Random rng = new Random((fixture.getId().toString() + fixture.getMatchDate() + "D2").hashCode());
        String pitchType = fixture.getPitchType();
        Map<String, Object> weather = weatherService.getWeather(homeTeam.getCountry(), fixture.getMatchDate());
        String condition = (String) weather.get("condition");
        int temperature = (int) weather.get("temperature");

        int totalUsed = inns.stream().mapToInt(this::getOversUsed).sum();
        int dayLimit = Math.min(150, 300 - totalUsed);

        return runFCInningsLoop(fixture, result, battingFirst, battingSecond,
                bat1Lineup, bat2Lineup, dayLimit, rng, pitchType, condition, temperature, format);
    }

    /**
     * Core FC innings loop: resumes any interrupted innings, then plays remaining.
     * Shared between Day 1 and Day 2.
     */
    private MatchResult runFCInningsLoop(Fixture fixture, MatchResult result,
                                          Team battingFirst, Team battingSecond,
                                          MatchLineup bat1Lineup, MatchLineup bat2Lineup,
                                          int dayOversLimit, Random rng, String pitchType,
                                          String condition, int temperature, String format) {
        Map<UUID, MatchFCStrategy> strategiesByTeam = loadFCStrategies(fixture);
        List<Innings> inningsList = result.getInningsList();
        int totalMatchOvers = inningsList.stream().mapToInt(this::getOversUsed).sum();
        int dayOversUsed = 0;
        int matchOversLimit = 300;
        int maxOversPerInnings = 150;
        int maxPerBowler = 50;

        // ── Resume interrupted innings from previous day ──
        if (!inningsList.isEmpty()) {
            Innings last = inningsList.get(inningsList.size() - 1);
            if (last.getResumeState() != null) {
                int oversBefore = getOversUsed(last);
                int inningsOversPlayed = computeBallsBowled(last.getTotalOvers()) / 6;
                int maxForResume = Math.min(maxOversPerInnings,
                        inningsOversPlayed + Math.min(matchOversLimit - totalMatchOvers, dayOversLimit));

                int inningsNum = last.getInningsNumber();
                boolean canDeclare = false;
                int declareAt = 0;
                boolean isChasing = false;
                int chaseTarget = 0;

                if (inningsNum == 1) {
                    Integer declare = getDeclareInn1(fixture, strategiesByTeam, last.getBattingTeam());
                    if (isHumanTeam(last.getBattingTeam()) && declare != null && declare > 0) {
                        canDeclare = true; declareAt = declare;
                    }
                } else if (inningsNum == 2) {
                    int i1r = inningsList.get(0).getTotalRuns();
                    Integer declareLead = getDeclareInn2Lead(fixture, strategiesByTeam, last.getBattingTeam());
                    if (isHumanTeam(last.getBattingTeam()) && declareLead != null && declareLead > 0) {
                        canDeclare = true; declareAt = i1r + declareLead;
                    }
                } else if (inningsNum == 3) {
                    long[] dc = computeInn3Declaration(fixture, strategiesByTeam, inningsList, last.getBattingTeam(), battingFirst, battingSecond);
                    canDeclare = dc[0] > 0; declareAt = (int) dc[1];
                } else if (inningsNum == 4) {
                    isChasing = true;
                    chaseTarget = computeFCChaseTarget(inningsList, battingSecond);
                }

                MatchLineup batL = last.getBattingTeam().getId().equals(battingFirst.getId()) ? bat1Lineup : bat2Lineup;
                MatchLineup bowlL = last.getBowlingTeam().getId().equals(battingFirst.getId()) ? bat1Lineup : bat2Lineup;

                SimContext ctx = new SimContext(rng, pitchType, condition, temperature,
                        maxForResume, maxPerBowler, format, isChasing, chaseTarget, batL, bowlL);
                simulateInningsWithDeclaration(last, ctx, canDeclare, declareAt, true);

                int newOvers = getOversUsed(last) - oversBefore;
                dayOversUsed += newOvers;
                totalMatchOvers += newOvers;

                if (dayOversUsed >= dayOversLimit || last.getResumeState() != null) {
                    return saveFCDay2Paused(fixture, result);  // Day 2 overs exhausted, don't retry
                }
                if (inningsNum == 4) {
                    return determineFCResultAndFinalize(fixture, result, battingFirst, battingSecond, rng);
                }
            }
        }

        // ── Play new innings ──
        while (inningsList.size() < 4 && dayOversUsed < dayOversLimit && totalMatchOvers < matchOversLimit) {
            int next = inningsList.size() + 1;
            Team bat = null, bowl = null;
            MatchLineup batL = null, bowlL = null;
            boolean canDeclare = false;
            int declareAt = 0;
            boolean isChasing = false;
            int chaseTarget = 0;

            switch (next) {
                case 1:
                    bat = battingFirst; bowl = battingSecond;
                    batL = bat1Lineup; bowlL = bat2Lineup;
                    Integer declareInn1 = getDeclareInn1(fixture, strategiesByTeam, bat);
                    if (isHumanTeam(bat) && declareInn1 != null && declareInn1 > 0) {
                        canDeclare = true; declareAt = declareInn1;
                    }
                    break;
                case 2:
                    bat = battingSecond; bowl = battingFirst;
                    batL = bat2Lineup; bowlL = bat1Lineup;
                    Integer declareInn2Lead = getDeclareInn2Lead(fixture, strategiesByTeam, bat);
                    if (isHumanTeam(bat) && declareInn2Lead != null && declareInn2Lead > 0) {
                        canDeclare = true;
                        declareAt = inningsList.get(0).getTotalRuns() + declareInn2Lead;
                    }
                    break;
                case 3: {
                    int i1 = inningsList.get(0).getTotalRuns();
                    int i2 = inningsList.get(1).getTotalRuns();
                    int lead = i1 - i2;
                    Boolean followOnChoice = getFollowOn(fixture, strategiesByTeam, battingFirst);
                    boolean followOn = lead >= 200
                            && (followOnChoice != null ? followOnChoice : true);
                    if (followOn) {
                        bat = battingSecond; bowl = battingFirst;
                        batL = bat2Lineup; bowlL = bat1Lineup;
                    } else {
                        bat = battingFirst; bowl = battingSecond;
                        batL = bat1Lineup; bowlL = bat2Lineup;
                    }
                    long[] dc = computeInn3Declaration(fixture, strategiesByTeam, inningsList, bat, battingFirst, battingSecond);
                    canDeclare = dc[0] > 0; declareAt = (int) dc[1];
                    break;
                }
                case 4: {
                    int i1 = inningsList.get(0).getTotalRuns();
                    int i2 = inningsList.get(1).getTotalRuns();
                    int i3 = inningsList.get(2).getTotalRuns();
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

                    if (fo) {
                        bat = battingFirst; bowl = battingSecond;
                        batL = bat1Lineup; bowlL = bat2Lineup;
                    } else {
                        bat = battingSecond; bowl = battingFirst;
                        batL = bat2Lineup; bowlL = bat1Lineup;
                    }
                    isChasing = true;
                    break;
                }
                default: break;
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
            dayOversUsed += oversUsed;

            if (dayOversUsed >= dayOversLimit || inn.getResumeState() != null) {
                return saveFCDay2Paused(fixture, result);  // Day 2 overs exhausted, don't retry
            }
        }

        return determineFCResultAndFinalize(fixture, result, battingFirst, battingSecond, rng);
    }

    /**
     * FC innings simulation with declaration support and day-break resume.
     * When fcDayMode=true, saves resume state if innings is interrupted by max overs.
     */
    private void simulateInningsWithDeclaration(Innings innings, SimContext ctx,
                                                 boolean canDeclare, int declareAtRuns,
                                                 boolean fcDayMode) {
        List<LineupPlayer> battingOrder = new ArrayList<>(ctx.battingLineup.getPlayers());
        battingOrder.sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));

        List<BowlingOrder> bowlingOrders = new ArrayList<>(ctx.bowlingLineup.getBowlingOrders());
        bowlingOrders.sort(Comparator.comparingInt(BowlingOrder::getOverNumber));

        Map<Integer, BowlingOrder> bowlerPlan = new LinkedHashMap<>();
        for (BowlingOrder bo : bowlingOrders) {
            bowlerPlan.put(bo.getOverNumber(), bo);
        }

        double teamFieldingAvg = calculateTeamFielding(ctx.bowlingLineup);
        double keeperSkill = getKeeperRating(ctx.bowlingLineup);

        if (battingOrder.size() < 2) return;

        // ── Resume-aware initialization ──
        int nextBatIdx;
        BatsmanState striker, nonStriker;
        Map<UUID, BattingScorecard> batCards;
        Map<UUID, BowlingScorecard> bowlCards;
        int totalRuns, totalWickets, totalExtras, ballsBowled;
        Map<UUID, Integer> bowlerBallCounts;
        Player previousBowler;
        int startOver;
        Set<UUID> existingBatCardIds;
        Set<UUID> existingBowlCardIds;

        String resumeJson = innings.getResumeState();
        if (resumeJson != null) {
            // ── RESUME from day break ──
            FCResumeState resume = parseResumeState(resumeJson);
            totalRuns = innings.getTotalRuns();
            totalWickets = innings.getTotalWickets();
            totalExtras = innings.getExtras();
            ballsBowled = computeBallsBowled(innings.getTotalOvers());
            startOver = ballsBowled / 6;

            batCards = new LinkedHashMap<>();
            for (BattingScorecard bc : innings.getBattingCards()) {
                batCards.put(bc.getPlayer().getId(), bc);
            }
            bowlCards = new LinkedHashMap<>();
            for (BowlingScorecard bc : innings.getBowlingCards()) {
                bowlCards.put(bc.getPlayer().getId(), bc);
            }
            existingBatCardIds = new HashSet<>(batCards.keySet());
            existingBowlCardIds = new HashSet<>(bowlCards.keySet());

            LineupPlayer strikerLP = battingOrder.stream()
                    .filter(lp -> lp.getPlayer().getId().equals(resume.strikerId)).findFirst().orElse(null);
            LineupPlayer nonStrikerLP = battingOrder.stream()
                    .filter(lp -> lp.getPlayer().getId().equals(resume.nonStrikerId)).findFirst().orElse(null);
            if (strikerLP == null || nonStrikerLP == null) return;
            striker = new BatsmanState(strikerLP);
            nonStriker = new BatsmanState(nonStrikerLP);
            nextBatIdx = resume.nextBatIdx;
            bowlerBallCounts = new HashMap<>(resume.bowlerBallCounts);
            previousBowler = resume.previousBowlerId != null
                    ? ctx.bowlingLineup.getPlayers().stream()
                        .map(LineupPlayer::getPlayer)
                        .filter(p -> p.getId().equals(resume.previousBowlerId))
                        .findFirst().orElse(null)
                    : null;

            innings.setResumeState(null);
        } else {
            // ── FRESH start ──
            startOver = 0;
            nextBatIdx = 2;
            striker = new BatsmanState(battingOrder.get(0));
            nonStriker = new BatsmanState(battingOrder.get(1));
            batCards = new LinkedHashMap<>();
            batCards.put(striker.player.getId(), createBatCard(innings, striker.lineupPlayer, 1));
            batCards.put(nonStriker.player.getId(), createBatCard(innings, nonStriker.lineupPlayer, 2));
            bowlCards = new LinkedHashMap<>();
            totalRuns = 0; totalWickets = 0; totalExtras = 0; ballsBowled = 0;
            bowlerBallCounts = new HashMap<>();
            previousBowler = null;
            existingBatCardIds = Collections.emptySet();
            existingBowlCardIds = Collections.emptySet();
        }

        Player currentBowler = null;
        String currentBowlerAggression = "N";
        int overNumber = startOver;
        boolean allOut = false;
        boolean declared = false;
        boolean chaseWon = false;

        outerLoop:
        for (int over = startOver; over < ctx.maxOvers; over++) {
            overNumber = over + 1;

            // Bowler selection: plan rotation with wrap-around and fallback
            int planSize = Math.max(1, bowlerPlan.size());
            int basePlanOver = bowlerPlan.isEmpty() ? overNumber :
                    ((overNumber - 1) % planSize) + 1;
            BowlingOrder planned = bowlerPlan.get(basePlanOver);
            boolean foundPlanned = false;
            if (planned != null) {
                Player plannedBowler = planned.getBowler();
                int bowled = bowlerBallCounts.getOrDefault(plannedBowler.getId(), 0);
                boolean sameAsPrevious = previousBowler != null && plannedBowler.getId().equals(previousBowler.getId());
                if (bowled < ctx.maxPerBowler * 6 && !sameAsPrevious) {
                    currentBowler = plannedBowler;
                    currentBowlerAggression = planned.getAggression();
                    foundPlanned = true;
                } else if (sameAsPrevious && !bowlerPlan.isEmpty()) {
                    for (int shift = 1; shift < planSize; shift++) {
                        int shiftedOver = ((basePlanOver - 1 + shift) % planSize) + 1;
                        BowlingOrder alt = bowlerPlan.get(shiftedOver);
                        if (alt != null) {
                            Player altBowler = alt.getBowler();
                            int altBowled = bowlerBallCounts.getOrDefault(altBowler.getId(), 0);
                            if (altBowled < ctx.maxPerBowler * 6 &&
                                (previousBowler == null || !altBowler.getId().equals(previousBowler.getId()))) {
                                currentBowler = altBowler;
                                currentBowlerAggression = alt.getAggression();
                                foundPlanned = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (!foundPlanned) {
                currentBowler = pickFallbackBowler(ctx, bowlerBallCounts, previousBowler);
                currentBowlerAggression = "N";
            }

            if (!bowlCards.containsKey(currentBowler.getId())) {
                bowlCards.put(currentBowler.getId(), createBowlCard(innings, currentBowler));
            }
            BowlingScorecard bowlCard = bowlCards.get(currentBowler.getId());

            int legalBallsThisOver = 0, runsThisOver = 0;
            boolean maidenPossible = true;
            int ballInOver = 0;

            while (legalBallsThisOver < 6) {
                ballInOver++;

                DeliveryResult delivery = simulateDelivery(
                        ctx, striker, currentBowler, currentBowlerAggression,
                        teamFieldingAvg, keeperSkill,
                        overNumber, totalRuns, totalWickets, ballsBowled,
                        batCards.get(striker.player.getId()));

                if (!Boolean.TRUE.equals(SKIP_BALL_EVENTS.get())) {
                    BallEvent event = BallEvent.builder()
                            .innings(innings).overNumber(overNumber).ballNumber(ballInOver)
                            .batsman(striker.player).bowler(currentBowler)
                            .runs(delivery.runs).isWicket(delivery.isWicket)
                            .isBoundary(delivery.isBoundary).isSix(delivery.isSix)
                            .isWide(delivery.isWide).isNoBall(delivery.isNoBall)
                            .isBye(delivery.isBye).isLegBye(delivery.isLegBye)
                            .dismissalType(delivery.dismissalType)
                            .fielder(delivery.fielder).commentary(delivery.commentary)
                            .build();
                    innings.getBallEvents().add(event);
                }

                totalRuns += delivery.runs;

                if (delivery.isWide || delivery.isNoBall) {
                    totalExtras += delivery.runs;
                    bowlCard.setRunsConceded(bowlCard.getRunsConceded() + delivery.runs);
                    if (delivery.isWide) bowlCard.setWides(bowlCard.getWides() + 1);
                    if (delivery.isNoBall) bowlCard.setNoBalls(bowlCard.getNoBalls() + 1);
                    maidenPossible = false;
                    runsThisOver += delivery.runs;
                } else {
                    legalBallsThisOver++;
                    ballsBowled++;
                    bowlerBallCounts.merge(currentBowler.getId(), 1, Integer::sum);

                    BattingScorecard batCard = batCards.get(striker.player.getId());
                    batCard.setBallsFaced(batCard.getBallsFaced() + 1);

                    if (delivery.isBye || delivery.isLegBye) {
                        totalExtras += delivery.runs;
                        if (delivery.runs == 0) bowlCard.setDotBalls(bowlCard.getDotBalls() + 1);
                    } else {
                        batCard.setRunsScored(batCard.getRunsScored() + delivery.runs);
                        if (delivery.isBoundary) batCard.setFours(batCard.getFours() + 1);
                        if (delivery.isSix) batCard.setSixes(batCard.getSixes() + 1);
                        bowlCard.setRunsConceded(bowlCard.getRunsConceded() + delivery.runs);
                        if (delivery.runs == 0 && !delivery.isWicket) {
                            bowlCard.setDotBalls(bowlCard.getDotBalls() + 1);
                        }
                    }

                    if (delivery.runs > 0) maidenPossible = false;
                    runsThisOver += delivery.runs;

                    if (delivery.isWicket) {
                        maidenPossible = false;
                        totalWickets++;
                        batCard.setDismissalType(delivery.dismissalType);
                        if (!"RUN_OUT".equals(delivery.dismissalType)) {
                            bowlCard.setWickets(bowlCard.getWickets() + 1);
                            batCard.setBowler(currentBowler);
                        }
                        batCard.setFielder(delivery.fielder);
                        if (batCard.getBallsFaced() > 0) {
                            batCard.setStrikeRate(Math.round(batCard.getRunsScored() * 100.0 / batCard.getBallsFaced() * 100.0) / 100.0);
                        }
                        if (totalWickets >= 10 || nextBatIdx >= battingOrder.size()) {
                            innings.setAllOut(true);
                            allOut = true;
                            break outerLoop;
                        }
                        striker = new BatsmanState(battingOrder.get(nextBatIdx));
                        nextBatIdx++;
                        batCards.put(striker.player.getId(), createBatCard(innings, striker.lineupPlayer, nextBatIdx));
                    } else {
                        if (delivery.runs % 2 == 1) {
                            boolean keepStrike = !delivery.isBye && !delivery.isLegBye
                                    && tryStrikeFarmingKeepStrike(
                                    ctx, striker, nonStriker, batCard, legalBallsThisOver, overNumber, false, ctx.rng);
                            if (!keepStrike) {
                                BatsmanState temp = striker; striker = nonStriker; nonStriker = temp;
                            }
                        }
                    }
                }

                // Chase target check
                if (ctx.isChasing && totalRuns >= ctx.target) {
                    chaseWon = true;
                    break outerLoop;
                }
            }

            // End of over
            BattingScorecard overEndStrikerCard = batCards.get(striker.player.getId());
            boolean keepEndOverStrike = tryStrikeFarmingKeepStrike(
                    ctx, striker, nonStriker, overEndStrikerCard, legalBallsThisOver, overNumber, true, ctx.rng);
            if (!keepEndOverStrike) {
                BatsmanState temp = striker; striker = nonStriker; nonStriker = temp;
            }
            if (maidenPossible && runsThisOver == 0) bowlCard.setMaidens(bowlCard.getMaidens() + 1);
            previousBowler = currentBowler;

            // Declaration check at end of each over
            if (canDeclare && declareAtRuns > 0 && totalRuns >= declareAtRuns) {
                innings.setDeclared(true);
                declared = true;
                break;
            }
        }

        // ── Save resume state if innings interrupted by day overs limit ──
        if (fcDayMode && !allOut && !declared && !chaseWon && totalWickets < 10) {
            int completedOversNow = ballsBowled / 6;
            if (completedOversNow >= ctx.maxOvers) {
                innings.setResumeState(
                        buildResumeState(striker, nonStriker, nextBatIdx, bowlerBallCounts, previousBowler));
            }
        }

        // ── Finalize innings ──
        innings.setTotalRuns(totalRuns);
        innings.setTotalWickets(totalWickets);
        innings.setExtras(totalExtras);
        int completedOvers = ballsBowled / 6;
        int remainingBalls = ballsBowled % 6;
        innings.setTotalOvers(completedOvers + remainingBalls / 10.0);

        for (BattingScorecard card : batCards.values()) {
            if (card.getBallsFaced() > 0) {
                card.setStrikeRate(Math.round(card.getRunsScored() * 100.0 / card.getBallsFaced() * 100.0) / 100.0);
            }
            if (!existingBatCardIds.contains(card.getPlayer().getId())) {
                innings.getBattingCards().add(card);
            }
        }
        for (BowlingScorecard card : bowlCards.values()) {
            int bowledBalls = bowlerBallCounts.getOrDefault(card.getPlayer().getId(), 0);
            int fullOvers = bowledBalls / 6;
            int partBalls = bowledBalls % 6;
            card.setOvers(fullOvers + partBalls / 10.0);
            if (bowledBalls > 0) {
                card.setEconomy(Math.round(card.getRunsConceded() / (bowledBalls / 6.0) * 100.0) / 100.0);
            }
            if (!existingBowlCardIds.contains(card.getPlayer().getId())) {
                innings.getBowlingCards().add(card);
            }
        }
    }

    /**
     * Determine FC result based on all innings scores.
     */
    private void determineFCResult(MatchResult result, int inn1, int inn2, int inn3, int inn4,
                                   Team battingFirst, Team battingSecond,
                                   boolean followOn, boolean inningsDefeat) {
        if (inningsDefeat) {
            if (followOn) {
                // Team B batted twice (inn2 + inn3), still behind Team A (inn1)
                int margin = inn1 - (inn2 + inn3);
                if (margin > 0) {
                    result.setWinner(battingFirst);
                    result.setResultType("INNINGS");
                    result.setResultMargin(margin);
                } else {
                    result.setResultType("DRAW"); // shouldn't happen but safety
                }
            } else {
                // Team A batted twice (inn1 + inn3), still behind Team B (inn2)
                int margin = inn2 - (inn1 + inn3);
                if (margin > 0) {
                    result.setWinner(battingSecond);
                    result.setResultType("INNINGS");
                    result.setResultMargin(margin);
                } else {
                    result.setResultType("DRAW");
                }
            }
            return;
        }

        // 4th innings was played
        int team1Total, team2Total;
        if (followOn) {
            team1Total = inn1;
            team2Total = inn2 + inn3;
        } else {
            team1Total = inn1 + inn3;
            team2Total = inn2;
        }

        int target = team1Total - team2Total + 1;
        Team chasingTeam = followOn ? battingFirst : battingSecond;
        Team settingTeam = followOn ? battingSecond : battingFirst;

        if (inn4 >= target) {
            // Chasing team won
            Innings lastInnings = result.getInningsList().stream()
                    .filter(i -> i.getInningsNumber() == 4).findFirst().orElse(null);
            int wicketsLost = lastInnings != null ? lastInnings.getTotalWickets() : 0;
            result.setWinner(chasingTeam);
            result.setResultType("WICKETS");
            result.setResultMargin(10 - wicketsLost);
        } else {
            // Did the chasing innings get all out or run out of overs?
            Innings lastInnings = result.getInningsList().stream()
                    .filter(i -> i.getInningsNumber() == 4).findFirst().orElse(null);

            if (lastInnings != null && (Boolean.TRUE.equals(lastInnings.getAllOut()) || lastInnings.getTotalWickets() >= 10)) {
                // Bowling team wins by runs
                int margin = target - inn4 - 1;
                result.setWinner(settingTeam);
                result.setResultType("RUNS");
                result.setResultMargin(margin);
            } else {
                // Ran out of overs → DRAW
                result.setResultType("DRAW");
            }
        }
    }

    private int getOversUsed(Innings innings) {
        // Use totalOvers field — works whether ball events were stored or skipped.
        double totalOvers = innings.getTotalOvers() != null ? innings.getTotalOvers() : 0.0;
        int full = (int) totalOvers;
        int partial = (int) Math.round((totalOvers - full) * 10);
        return full + (partial > 0 ? 1 : 0);
    }

    // ─── Bot lineup auto-generation ─────────────────────────────

    private MatchLineup getOrGenerateLineup(Fixture fixture, Team team, String format) {
        Optional<MatchLineup> existing = matchLineupRepository.findByFixtureIdAndTeamId(fixture.getId(), team.getId());
        if (existing.isPresent()) return existing.get();

        Optional<DefaultLineup> savedDefault = defaultLineupRepository.findByTeamIdAndFormat(team.getId(), format);
        if (savedDefault.isPresent()) {
            MatchLineup fromDefault = buildLineupFromDefault(fixture, team, format, savedDefault.get());
            if (fromDefault != null) {
                return fromDefault;
            }
        }

        // Auto-generate lineup for any team without one (bot or user who forgot)
        log.info("No lineup found for team {} — auto-generating", team.getTeamName());

        List<Player> squad = playerRepository.findByTeam(team);
        if (squad.size() < 11) {
            throw new IllegalStateException("Team " + team.getTeamName() + " has fewer than 11 players (" + squad.size() + ")");
        }

        // Pick best 11 using: 5 BAT + 1 KP + 2 AR + 3 BOWL
        List<Player> selected = autoSelectPlaying11(squad);

        // Build batting order
        List<Player> battingOrder = buildAutoBattingOrder(selected);

        MatchLineup lineup = MatchLineup.builder()
                .fixture(fixture)
                .team(team)
                .bowlingPlan("BALANCED")
                .build();

        // Keeper = best keeper-rated among KEEPERs selected, fallback to highest keeperRating
        Player keeper = selected.stream()
                .filter(p -> "KEEPER".equals(p.getRole()))
                .max(Comparator.comparingInt(Player::getKeeperRating))
                .orElse(selected.stream()
                        .max(Comparator.comparingInt(Player::getKeeperRating))
                        .orElse(selected.get(0)));
        lineup.setKeeper(keeper);

        // Captain = highest overall rating
        Player captain = selected.stream()
                .max(Comparator.comparingInt(Player::getRating))
                .orElse(selected.get(0));
        lineup.setCaptain(captain);

        // Build batting positions
        for (int i = 0; i < battingOrder.size(); i++) {
            LineupPlayer lp = LineupPlayer.builder()
                    .lineup(lineup)
                    .player(battingOrder.get(i))
                    .battingPosition(i + 1)
                    .batAggression(battingOrder.get(i).getBatAggression())
                    .build();
            lineup.getPlayers().add(lp);
        }

        // Bowling: for FC use all bowlers with bowlRating >= 15 (min 5), for T20/ODI top 5
        List<Player> topBowlers;
        if ("FC".equalsIgnoreCase(format)) {
            topBowlers = selected.stream()
                    .filter(p -> p.getBowlRating() >= 15)
                    .sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating()))
                    .toList();
            if (topBowlers.size() < 5) {
                topBowlers = selected.stream()
                        .sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating()))
                        .limit(5)
                        .toList();
            }
        } else {
            topBowlers = selected.stream()
                    .sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating()))
                    .limit(5)
                    .toList();
        }

        // FC uses 100-over plan that cycles; T20/ODI use full match overs
        int bowlingPlanOvers = "FC".equalsIgnoreCase(format) ? 100 : getMaxOvers(format);
        for (int over = 1; over <= bowlingPlanOvers; over++) {
            Player bowler = topBowlers.get((over - 1) % topBowlers.size());
            BowlingOrder bo = BowlingOrder.builder()
                    .lineup(lineup)
                    .overNumber(over)
                    .bowler(bowler)
                    .aggression(bowler.getBowlAggression())
                    .build();
            lineup.getBowlingOrders().add(bo);
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

        MatchLineup lineup = MatchLineup.builder()
                .fixture(fixture)
                .team(team)
                .bowlingPlan(Objects.toString(data.getOrDefault("bowlingPlan", "BALANCED"), "BALANCED"))
                .build();
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
                if (chosen == null) {
                    chosen = pickBestRemainingPlayer(squad, usedPlayerIds);
                }
                if (chosen == null) break;
                if (playerId != null) {
                    replacementByOriginalId.put(playerId, chosen);
                }
            }

            usedPlayerIds.add(chosen.getId());
            selected.add(chosen);

            int battingPosition = ((Number) slot.getOrDefault("battingPosition", i + 1)).intValue();
            String batAgg = Objects.toString(slot.getOrDefault("batAggression", chosen.getBatAggression()), "N");

            LineupPlayer lp = LineupPlayer.builder()
                    .lineup(lineup)
                    .player(chosen)
                    .battingPosition(battingPosition)
                    .batAggression(batAgg)
                    .build();
            lineup.getPlayers().add(lp);
        }

        while (lineup.getPlayers().size() < 11) {
            Player next = pickBestRemainingPlayer(squad, usedPlayerIds);
            if (next == null) break;
            usedPlayerIds.add(next.getId());
            selected.add(next);
            LineupPlayer lp = LineupPlayer.builder()
                    .lineup(lineup)
                    .player(next)
                    .battingPosition(lineup.getPlayers().size() + 1)
                    .batAggression(next.getBatAggression())
                    .build();
            lineup.getPlayers().add(lp);
        }
        lineup.getPlayers().sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));
        for (int i = 0; i < lineup.getPlayers().size(); i++) {
            lineup.getPlayers().get(i).setBattingPosition(i + 1);
        }

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
            keeper = selected.stream()
                    .filter(p -> "KEEPER".equals(p.getRole()))
                    .max(Comparator.comparingInt(Player::getKeeperRating))
                    .orElse(selected.stream()
                            .max(Comparator.comparingInt(Player::getKeeperRating))
                            .orElse(null));
        }
        lineup.setKeeper(keeper);

        List<Map<String, Object>> rawBowling = (List<Map<String, Object>>) data.get("bowlingOrders");
        int planOvers = "FC".equalsIgnoreCase(format) ? 100 : getMaxOvers(format);
        if (rawBowling != null) {
            rawBowling.sort(Comparator.comparingInt(b -> ((Number) b.getOrDefault("overNumber", 999)).intValue()));
            for (Map<String, Object> bo : rawBowling) {
                int overNumber = ((Number) bo.getOrDefault("overNumber", 0)).intValue();
                if (overNumber < 1 || overNumber > planOvers) continue;

                String originalBowlerId = Objects.toString(bo.get("bowlerId"), null);
                Player bowler = null;
                if (originalBowlerId != null) {
                    Player direct = squadById.get(originalBowlerId);
                    if (direct != null && selectedById.containsKey(direct.getId())) {
                        bowler = direct;
                    } else {
                        Player replacement = replacementByOriginalId.get(originalBowlerId);
                        if (replacement != null && selectedById.containsKey(replacement.getId())) {
                            bowler = replacement;
                        } else {
                            String requiredRole = resolveRole(originalBowlerId);
                            bowler = pickSelectedByRole(selected, requiredRole);
                        }
                    }
                }
                if (bowler == null) {
                    bowler = selected.stream()
                            .max(Comparator.comparingInt(Player::getBowlRating))
                            .orElse(null);
                }
                if (bowler == null) continue;

                BowlingOrder order = BowlingOrder.builder()
                        .lineup(lineup)
                        .overNumber(overNumber)
                        .bowler(bowler)
                        .aggression(Objects.toString(bo.getOrDefault("aggression", bowler.getBowlAggression()), "N"))
                        .build();
                lineup.getBowlingOrders().add(order);
            }
        }

        if (lineup.getBowlingOrders().isEmpty()) {
            List<Player> topBowlers = selected.stream()
                    .sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating()))
                    .limit(5)
                    .toList();
            if (topBowlers.isEmpty()) return null;
            for (int over = 1; over <= planOvers; over++) {
                Player bowler = topBowlers.get((over - 1) % topBowlers.size());
                lineup.getBowlingOrders().add(BowlingOrder.builder()
                        .lineup(lineup)
                        .overNumber(over)
                        .bowler(bowler)
                        .aggression(bowler.getBowlAggression())
                        .build());
            }
        }

        lineup.getBowlingOrders().sort(Comparator.comparingInt(BowlingOrder::getOverNumber));
        return matchLineupRepository.save(lineup);
    }

    private String resolveRole(String playerId) {
        if (playerId == null) return null;
        try {
            return playerRepository.findById(UUID.fromString(playerId)).map(Player::getRole).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Player pickReplacementByRole(List<Player> squad, Set<UUID> usedIds, String role) {
        if (role == null || role.isBlank()) return null;
        Comparator<Player> comparator = switch (role) {
            case "BATSMAN" -> Comparator.comparingInt(Player::getBatRating);
            case "BOWLER" -> Comparator.comparingInt(Player::getBowlRating);
            case "KEEPER" -> Comparator.comparingInt(Player::getKeeperRating);
            case "ALL_ROUNDER" -> Comparator.comparingInt(p -> p.getBatRating() + p.getBowlRating());
            default -> Comparator.comparingInt(Player::getRating);
        };
        return squad.stream()
                .filter(p -> !usedIds.contains(p.getId()) && role.equalsIgnoreCase(p.getRole()))
                .max(comparator)
                .orElse(null);
    }

    private Player pickBestRemainingPlayer(List<Player> squad, Set<UUID> usedIds) {
        return squad.stream()
                .filter(p -> !usedIds.contains(p.getId()))
                .max(Comparator.comparingInt(Player::getRating))
                .orElse(null);
    }

    private Player pickSelectedByRole(List<Player> selected, String role) {
        if (role == null || role.isBlank()) return null;
        Comparator<Player> comparator = switch (role) {
            case "BATSMAN" -> Comparator.comparingInt(Player::getBatRating);
            case "BOWLER" -> Comparator.comparingInt(Player::getBowlRating);
            case "KEEPER" -> Comparator.comparingInt(Player::getKeeperRating);
            case "ALL_ROUNDER" -> Comparator.comparingInt(p -> p.getBatRating() + p.getBowlRating());
            default -> Comparator.comparingInt(Player::getRating);
        };
        return selected.stream()
                .filter(p -> role.equalsIgnoreCase(p.getRole()))
                .max(comparator)
                .orElse(null);
    }

    /**
     * Auto-select playing 11: 5 BATSMAN + 1 KEEPER + 2 ALL_ROUNDER + 3 BOWLER.
     * Selection criteria:
     *  - 5 batsmen with highest batRating
     *  - 1 keeper with highest keeperRating
     *  - 2 all-rounders with highest combined (batRating + bowlRating)
     *  - 3 bowlers with highest bowlRating
     * Falls back to fill any shortfall from remaining players.
     */
    private List<Player> autoSelectPlaying11(List<Player> squad) {
        List<Player> selected = new ArrayList<>();
        Set<UUID> pickedIds = new HashSet<>();

        // 1. Best keeper by keeperRating
        squad.stream()
                .filter(p -> "KEEPER".equals(p.getRole()))
                .max(Comparator.comparingInt(Player::getKeeperRating))
                .ifPresent(p -> { selected.add(p); pickedIds.add(p.getId()); });

        // Fallback: if no KEEPER found, pick player with highest keeperRating
        if (selected.isEmpty()) {
            squad.stream()
                    .max(Comparator.comparingInt(Player::getKeeperRating))
                    .ifPresent(p -> { selected.add(p); pickedIds.add(p.getId()); });
        }

        // 2. Top 5 batsmen by batRating
        squad.stream()
                .filter(p -> "BATSMAN".equals(p.getRole()) && !pickedIds.contains(p.getId()))
                .sorted((a, b) -> Integer.compare(b.getBatRating(), a.getBatRating()))
                .limit(5)
                .forEach(p -> { selected.add(p); pickedIds.add(p.getId()); });

        // 3. Top 2 all-rounders by combined bat+bowl
        squad.stream()
                .filter(p -> "ALL_ROUNDER".equals(p.getRole()) && !pickedIds.contains(p.getId()))
                .sorted((a, b) -> Integer.compare(
                        b.getBatRating() + b.getBowlRating(),
                        a.getBatRating() + a.getBowlRating()))
                .limit(2)
                .forEach(p -> { selected.add(p); pickedIds.add(p.getId()); });

        // 4. Top 3 bowlers by bowlRating
        squad.stream()
                .filter(p -> "BOWLER".equals(p.getRole()) && !pickedIds.contains(p.getId()))
                .sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating()))
                .limit(3)
                .forEach(p -> { selected.add(p); pickedIds.add(p.getId()); });

        // 5. Fill remaining slots if any role was short
        if (selected.size() < 11) {
            squad.stream()
                    .filter(p -> !pickedIds.contains(p.getId()))
                    .sorted((a, b) -> Integer.compare(b.getRating(), a.getRating()))
                    .limit(11 - selected.size())
                    .forEach(p -> { selected.add(p); pickedIds.add(p.getId()); });
        }

        return selected.subList(0, Math.min(11, selected.size()));
    }

    /**
     * Build batting order from selected 11:
     * 1-5: batsmen sorted by batRating desc
     * 6: keeper
     * 7-8: all-rounders sorted by batRating desc
     * 9-11: bowlers sorted by bowlRating desc (tail)
     */
    private List<Player> buildAutoBattingOrder(List<Player> selected) {
        List<Player> order = new ArrayList<>();

        // Batsmen first (by batting skill)
        List<Player> bats = selected.stream()
                .filter(p -> "BATSMAN".equals(p.getRole()))
                .sorted((a, b) -> Integer.compare(b.getBatRating(), a.getBatRating()))
                .toList();
        order.addAll(bats);

        // Keeper
        selected.stream()
                .filter(p -> "KEEPER".equals(p.getRole()))
                .findFirst()
                .ifPresent(order::add);

        // All-rounders (by bat skill)
        List<Player> ars = selected.stream()
                .filter(p -> "ALL_ROUNDER".equals(p.getRole()))
                .sorted((a, b) -> Integer.compare(b.getBatRating(), a.getBatRating()))
                .toList();
        order.addAll(ars);

        // Bowlers (by bowl skill — tailenders)
        List<Player> bowls = selected.stream()
                .filter(p -> "BOWLER".equals(p.getRole()))
                .sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating()))
                .toList();
        order.addAll(bowls);

        // Any remaining players not yet categorized
        selected.stream()
                .filter(p -> !order.contains(p))
                .forEach(order::add);

        return order;
    }

    // ─── Result determination ───────────────────────────────────

    public void logMatchActivity(MatchResult result, Fixture fixture) {
        Team home = fixture.getHomeTeam();
        Team away = fixture.getAwayTeam();
        String fmt = fixture.getLeague() != null ? fixture.getLeague().getFormat() : fixture.getFormat();
        String prefix = fmt != null ? "(" + fmt + ") " : "";

        if ("DRAW".equals(result.getResultType()) || "TIE".equals(result.getResultType())) {
            String text = prefix + "Match vs %s ended in a " + result.getResultType().toLowerCase() + ".";
            activityLogService.log(home, "match-lost", String.format(text, away.getTeamName()));
            activityLogService.log(away, "match-lost", String.format(text, home.getTeamName()));
        } else if (result.getWinner() != null) {
            Team winner = result.getWinner();
            Team loser = winner.getId().equals(home.getId()) ? away : home;
            String margin = result.getResultMargin() + " " + result.getResultType().toLowerCase();
            activityLogService.log(winner, "match-won", prefix + "Won vs " + loser.getTeamName() + " by " + margin + ".");
            activityLogService.log(loser, "match-lost", prefix + "Lost vs " + winner.getTeamName() + " by " + margin + ".");
        }
    }

    // ─── Gate Money ─────────────────────────────────────────────

    /**
     * Distribute gate money (ticket revenue) from a league match.
     *
     * Attendance depends on:
     *   - Home team stadium capacity (standing / economy / standard / premium)
     *   - Combined fan power: homeFans + awayFans × 0.3
     *   - Average morale of both teams (morale multiplier 0.6 – 1.4)
     *
     * Ticket prices: Standing $2, Economy $4, Standard $7, Premium $10
     *
     * Fill-rate multipliers (guarantees premium < standard < economy < standing):
     *   Standing  ×1.00   Economy ×0.75   Standard ×0.50   Premium ×0.25
     *
     * Revenue split: home 60-65% (based on fan ratio), away 35-40%.
     */
    private void distributeGateMoney(MatchResult matchResult, Fixture fixture) {
        Team home = fixture.getHomeTeam();
        Team away = fixture.getAwayTeam();

        // Fetch home team's stadium (match is played at home ground)
        StadiumSeats seats = stadiumSeatsRepository.findByTeam(home)
                .orElse(StadiumSeats.builder().team(home)
                        .standing(2000).economy(1500).standard(1000).premium(500).build());

        int standingCap = seats.getStanding();
        int economyCap  = seats.getEconomy();
        int standardCap = seats.getStandard();
        int premiumCap  = seats.getPremium();
        int totalCap    = standingCap + economyCap + standardCap + premiumCap;
        if (totalCap <= 0) return;

        // ═══ DEMAND CALCULATION ═══
        // Attendance is driven by how many people WANT to come,
        // not by stadium size. Stadium only caps it.

        // 1. Fan base (core demand — home fans fully, away fans 30% travel factor)
        int homeFans = home.getFans() != null ? home.getFans() : 1000;
        int awayFans = away.getFans() != null ? away.getFans() : 1000;
        double baseDemand = homeFans + awayFans * 0.30;

        // 2. Morale multiplier (0.70× to 1.30×)
        int homeMorale = home.getMorale() != null ? home.getMorale() : 50;
        int awayMorale = away.getMorale() != null ? away.getMorale() : 50;
        double avgMorale = (homeMorale + awayMorale) / 2.0;
        double moraleMult = 0.70 + (avgMorale / 100.0) * 0.60;

        // 3. Division multiplier (higher division = bigger draw, more media, more hype)
        double divMult = 1.0;
        if (fixture.getLeague() != null && fixture.getLeague().getDivision() != null) {
            int div = fixture.getLeague().getDivision();
            divMult = switch (div) {
                case 1 -> 1.50;
                case 2 -> 1.25;
                case 3 -> 1.00;
                default -> 0.85;
            };
        }

        // 4. Team quality multiplier (format-specific ELO as proxy for league position)
        //    Rating 800 → 0.85×, 1000 → 1.0×, 1200 → 1.15×, 1400+ → 1.30×
        String fmt = fixture.getLeague() != null ? fixture.getLeague().getFormat()
                : (fixture.getFormat() != null ? fixture.getFormat() : "T20");
        int homeRating = getRatingForFormat(home, fmt);
        int awayRating = getRatingForFormat(away, fmt);
        double avgRating = (homeRating + awayRating) / 2.0;
        double ratingMult = 0.85 + (avgRating - 800.0) / 800.0 * 0.45;
        ratingMult = Math.max(0.70, Math.min(ratingMult, 1.40));

        // 5. Random factor (±15% to feel organic, not identical every match)
        double randomFactor = 0.85 + Math.random() * 0.30;

        // Total demand = people who want to attend
        int totalDemand = (int) Math.round(baseDemand * moraleMult * divMult * ratingMult * randomFactor);

        // ═══ DYNAMIC SEAT DEMAND ═══
        // Crowd preferences depend on:
        //  - affordability bias (most fans still prefer cheaper seats)
        //  - match hype (bigger games shift some demand upward)
        //  - stadium shape (grounds with more lower-tier seating should benefit)
        double moraleAppeal = Math.max(0.0, Math.min(1.0, (moraleMult - 0.70) / 0.60));
        double divisionAppeal = Math.max(0.0, Math.min(1.0, (divMult - 0.85) / 0.65));
        double ratingAppeal = Math.max(0.0, Math.min(1.0, (ratingMult - 0.70) / 0.70));
        double hypeScore = (moraleAppeal * 0.25) + (divisionAppeal * 0.35) + (ratingAppeal * 0.40);

        // Low-hype matches skew affordable; bigger matches pull more upper-tier demand.
        double[] lowHype = {0.52, 0.28, 0.14, 0.06};
        double[] highHype = {0.35, 0.32, 0.22, 0.11};
        double[] baseWeights = new double[4];
        for (int i = 0; i < 4; i++) {
            baseWeights[i] = lowHype[i] + (highHype[i] - lowHype[i]) * hypeScore;
        }

        // Blend in actual stadium composition so affordable-heavy grounds aren't punished.
        double[] capWeights = {
                standingCap / (double) totalCap,
                economyCap / (double) totalCap,
                standardCap / (double) totalCap,
                premiumCap / (double) totalCap
        };
        double[] finalWeights = new double[4];
        for (int i = 0; i < 4; i++) {
            finalWeights[i] = baseWeights[i] * 0.70 + capWeights[i] * 0.30;
        }
        normalize(finalWeights);

        int[] capacities = {standingCap, economyCap, standardCap, premiumCap};
        int[] attendance = new int[4];
        int[] currentDemand = allocateDemand(totalDemand, finalWeights);
        int[] remainingCap = Arrays.copyOf(capacities, capacities.length);

        // Two-way overflow: some fans trade up, some settle for cheaper seats.
        double[][] overflowMatrix = {
                {0.00, 0.35, 0.10, 0.00}, // Standing overflow -> Economy / Standard
                {0.20, 0.00, 0.25, 0.05}, // Economy overflow -> Standing / Standard / Premium
                {0.00, 0.20, 0.00, 0.20}, // Standard overflow -> Economy / Premium
                {0.00, 0.15, 0.45, 0.00}  // Premium overflow -> Economy / Standard
        };

        for (int wave = 0; wave < 4 && sum(currentDemand) > 0; wave++) {
            int[] unmet = new int[4];
            for (int i = 0; i < 4; i++) {
                int fill = Math.min(currentDemand[i], remainingCap[i]);
                attendance[i] += fill;
                remainingCap[i] -= fill;
                unmet[i] = currentDemand[i] - fill;
            }

            if (sum(unmet) == 0) break;

            double[] nextRaw = new double[4];
            for (int from = 0; from < 4; from++) {
                if (unmet[from] <= 0) continue;
                for (int to = 0; to < 4; to++) {
                    if (overflowMatrix[from][to] <= 0) continue;
                    nextRaw[to] += unmet[from] * overflowMatrix[from][to];
                }
            }
            currentDemand = roundDemand(nextRaw);
        }

        int standingAtt = attendance[0];
        int economyAtt = attendance[1];
        int standardAtt = attendance[2];
        int premiumAtt = attendance[3];

        int totalAtt = standingAtt + economyAtt + standardAtt + premiumAtt;

        // Store attendance on match result
        matchResult.setAttendance(totalAtt);
        matchResult.setAttendanceBreakdown(String.format(
                "%d,%d,%d,%d,%d,%d,%d,%d",
                standingAtt, standingCap, economyAtt, economyCap,
                standardAtt, standardCap, premiumAtt, premiumCap));

        // Revenue: Standing $2, Economy $4, Standard $7, Premium $10
        long totalRevenue = (long) standingAtt * 2
                          + (long) economyAtt * 4
                          + (long) standardAtt * 7
                          + (long) premiumAtt * 10;
        if (totalRevenue <= 0) return;

        // Split: home 60-65% based on fan ratio; away gets the rest (35-40%)
        double homeFanRatio = homeFans / (double) (homeFans + awayFans);
        double homeSharePct = 0.60 + homeFanRatio * 0.05; // 60%–65%
        long homeShare = Math.round(totalRevenue * homeSharePct);
        long awayShare = totalRevenue - homeShare;

        // Credit funds
        home.setFunds(home.getFunds() + homeShare);
        away.setFunds(away.getFunds() + awayShare);
        teamRepository.save(home);
        teamRepository.save(away);

        // Log transactions
        String desc = String.format("Gate money: %,d attendance, $%,d total revenue (%s vs %s)",
                totalAtt, totalRevenue, home.getTeamName(), away.getTeamName());
        transactionLogRepository.save(TransactionLog.builder()
                .team(home).type("GATE_MONEY").description(desc + " [Home 🏟️]")
                .amount(homeShare).balanceAfter(home.getFunds()).build());
        transactionLogRepository.save(TransactionLog.builder()
                .team(away).type("GATE_MONEY").description(desc + " [Away]")
                .amount(awayShare).balanceAfter(away.getFunds()).build());
    }

    private int getRatingForFormat(Team team, String format) {
        return switch (format.toUpperCase()) {
            case "ODI" -> team.getOdiRating() != null ? team.getOdiRating() : 1000;
            case "FC", "TEST" -> team.getFcRating() != null ? team.getFcRating() : 1000;
            default -> team.getT20Rating() != null ? team.getT20Rating() : 1000;
        };
    }

    private void normalize(double[] weights) {
        double total = 0.0;
        for (double weight : weights) total += weight;
        if (total <= 0.0) {
            Arrays.fill(weights, 0.25);
            return;
        }
        for (int i = 0; i < weights.length; i++) {
            weights[i] /= total;
        }
    }

    private int[] allocateDemand(int total, double[] weights) {
        int[] result = new int[weights.length];
        double[] fractions = new double[weights.length];
        int allocated = 0;
        for (int i = 0; i < weights.length; i++) {
            double raw = total * weights[i];
            result[i] = (int) Math.floor(raw);
            fractions[i] = raw - result[i];
            allocated += result[i];
        }
        while (allocated < total) {
            int best = 0;
            for (int i = 1; i < fractions.length; i++) {
                if (fractions[i] > fractions[best]) best = i;
            }
            result[best]++;
            fractions[best] = -1.0;
            allocated++;
        }
        return result;
    }

    private int[] roundDemand(double[] raw) {
        int[] result = new int[raw.length];
        double[] fractions = new double[raw.length];
        int targetTotal = (int) Math.round(Arrays.stream(raw).sum());
        int allocated = 0;
        for (int i = 0; i < raw.length; i++) {
            result[i] = (int) Math.floor(raw[i]);
            fractions[i] = raw[i] - result[i];
            allocated += result[i];
        }
        while (allocated < targetTotal) {
            int best = 0;
            for (int i = 1; i < fractions.length; i++) {
                if (fractions[i] > fractions[best]) best = i;
            }
            if (fractions[best] <= 0) break;
            result[best]++;
            fractions[best] = -1.0;
            allocated++;
        }
        return result;
    }

    private int sum(int[] values) {
        int total = 0;
        for (int value : values) total += value;
        return total;
    }

    /**
     * Update morale and fans for both teams after each match.
     * Morale: stored per-team, shifts per match. Win +8, Draw +2, Loss −6. Clamped 0–100.
     * Fans: gain diminishes toward 150K ceiling; losses penalize based on quadratic scaling.
     */
    private void updateMoraleAndFans(MatchResult result, Fixture fixture, String format) {
        Team home = fixture.getHomeTeam();
        Team away = fixture.getAwayTeam();
        String rt = result.getResultType();
        boolean isDraw = "DRAW".equals(rt) || "TIE".equals(rt) || "NO_RESULT".equals(rt);

        if (isDraw) {
            applyMoraleDelta(home, 2);
            applyMoraleDelta(away, 2);
            applyFanDelta(home, "DRAW");
            applyFanDelta(away, "DRAW");
            applyEloUpdate(home, away, 0.5, 0.5, format);
        } else if (result.getWinner() != null) {
            Team winner = result.getWinner();
            Team loser = winner.getId().equals(home.getId()) ? away : home;
            applyMoraleDelta(winner, 8);
            applyMoraleDelta(loser, -6);
            applyFanDelta(winner, "WIN");
            applyFanDelta(loser, "LOSS");
            applyEloUpdate(winner, loser, 1.0, 0.0, format);
        }

        teamRepository.save(home);
        teamRepository.save(away);
    }

    /**
     * Apply morale change after a match.
     * Primary: resultDelta (Win +8, Draw +2, Loss −6).
     * Secondary: squadPull — 15% nudge toward avg squad confidence.
     * Tertiary: academyPull — 10% nudge toward academy benchmark (level × 25).
     */
    private void applyMoraleDelta(Team team, int resultDelta) {
        int current = team.getMorale() != null ? team.getMorale() : 50;

        // Squad confidence pull — 15% weight toward avg confidence
        List<Player> squad = playerRepository.findByTeam(team);
        double avgConfidence = 50.0;
        if (!squad.isEmpty()) {
            avgConfidence = squad.stream().mapToInt(Player::getConfidence).average().orElse(50.0);
        }
        double squadPull = (avgConfidence - current) * 0.15;

        // Academy pull — 10% weight toward academy benchmark (level × 25)
        int academyBenchmark = (team.getAcademyLevel() != null ? team.getAcademyLevel() : 1) * 25;
        double academyPull = (academyBenchmark - current) * 0.10;

        int newMorale = (int) Math.round(current + resultDelta + squadPull + academyPull);
        team.setMorale(Math.max(0, Math.min(100, newMorale)));
    }

    private void applyFanDelta(Team team, String outcome) {
        int currentFans = team.getFans() != null ? team.getFans() : 1000;
        double ceiling = 150000.0;
        double ratio = Math.min(1.0, currentFans / ceiling);
        int gain, penalty;
        switch (outcome) {
            case "WIN":
                gain = Math.max(20, (int)(500 * (1.0 - ratio)));
                penalty = 0;
                break;
            case "DRAW":
                gain = Math.max(10, (int)(200 * (1.0 - ratio)));
                penalty = (int)(currentFans * 0.001 * ratio);
                break;
            default: // LOSS
                gain = Math.max(5, (int)(100 * (1.0 - ratio)));
                penalty = (int)(currentFans * 0.004 * ratio);
                break;
        }
        team.setFans(Math.max(100, currentFans + gain - penalty));
    }

    /**
     * Elo rating update (K=20) for the format-specific rating.
     * E_A = 1 / (1 + 10^((R_B - R_A) / 400))
     * New R_A = R_A + K * (S_A - E_A)
     */
    private void applyEloUpdate(Team teamA, Team teamB, double scoreA, double scoreB, String format) {
        int K = 20;
        int ratingA = getFormatRating(teamA, format);
        int ratingB = getFormatRating(teamB, format);

        double expectedA = 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
        double expectedB = 1.0 - expectedA;

        int newRatingA = (int) Math.round(ratingA + K * (scoreA - expectedA));
        int newRatingB = (int) Math.round(ratingB + K * (scoreB - expectedB));

        // Floor at 100 to prevent nonsensical ratings
        setFormatRating(teamA, format, Math.max(100, newRatingA));
        setFormatRating(teamB, format, Math.max(100, newRatingB));
    }

    private int getFormatRating(Team team, String format) {
        if ("T20".equalsIgnoreCase(format)) return team.getT20Rating() != null ? team.getT20Rating() : 1000;
        if ("FC".equalsIgnoreCase(format)) return team.getFcRating() != null ? team.getFcRating() : 1000;
        return team.getOdiRating() != null ? team.getOdiRating() : 1000; // ODI default
    }

    private void setFormatRating(Team team, String format, int rating) {
        if ("T20".equalsIgnoreCase(format)) team.setT20Rating(rating);
        else if ("FC".equalsIgnoreCase(format)) team.setFcRating(rating);
        else team.setOdiRating(rating);
    }

    // ─── Post-match player fitness & confidence ──────────────────

    /**
     * Update fitness and confidence for every player who participated.
     *
     * FITNESS LOSS (per match):
     *   Base loss by format: T20 = −3, ODI = −5, FC = −8
     *   + Batting load:  ballsFaced / divisor  (T20: 40, ODI: 60, FC: 80)
     *   + Bowling load:  oversBowled / divisor (T20: 1.5, ODI: 3, FC: 5)
     *   Stamina scales the loss: totalLoss × (1.3 − stamina/100 × 0.6)
     *     → stamina 100 = ×0.7 of base, stamina 0 = ×1.3 of base
     *   Clamped to [0, 100].
     *
     * CONFIDENCE CHANGE (format-aware):
     *   Batting T20:  duck = −4, <15 = −1, 25+ = +2, 40+ = +4, 75+ = +7
     *   Batting ODI:  duck = −4, <20 = −1, 35+ = +2, 50+ = +4, 100+ = +7
     *   Batting FC:   duck = −4, <25 = −1, 50+ = +2, 75+ = +4, 150+ = +7
     *   Bowling:  0 wkts & expensive (T20 >10, ODI >7, FC >4) = −3, 1 wkt = +1, 2 wkt = +2, 3+ = +4, 5+ = +7
     *   Man of Match: +5
     *   Win: +2, Loss: −2, Draw: 0
     *   Clamped to [0, 100].
     *
     * EXPERIENCE GAIN (diminishing returns):
     *   Raw XP: Base +1, FC +1, batting/bowling bonuses, MoM +1 (range 1–7).
     *   Effective = floor(rawXP × 50 / (50 + currentXP)).
     *   At XP 0 → full gain. At XP 50 → half. At XP 100 → third.
     *   Soft cap ~100: average performers stop gaining; only standouts push past.
     */
    private void updatePlayerStats(MatchResult result, String format) {
        // Collect per-player batting/bowling aggregates across all innings
        Map<UUID, int[]> playerBatStats = new HashMap<>();  // [totalRuns, totalBallsFaced, ducks]
        Map<UUID, double[]> playerBowlStats = new HashMap<>(); // [totalOvers, totalRunsConceded, totalWickets]
        Set<UUID> allPlayerIds = new HashSet<>();

        for (Innings inn : result.getInningsList()) {
            for (BattingScorecard bc : inn.getBattingCards()) {
                UUID pid = bc.getPlayer().getId();
                allPlayerIds.add(pid);
                int[] stats = playerBatStats.computeIfAbsent(pid, k -> new int[3]);
                stats[0] += bc.getRunsScored();
                stats[1] += bc.getBallsFaced();
                if (bc.getBallsFaced() > 0 && bc.getRunsScored() == 0 && bc.getDismissalType() != null) {
                    stats[2]++; // duck
                }
            }
            for (BowlingScorecard bw : inn.getBowlingCards()) {
                UUID pid = bw.getPlayer().getId();
                allPlayerIds.add(pid);
                double[] stats = playerBowlStats.computeIfAbsent(pid, k -> new double[3]);
                stats[0] += bw.getOvers();
                stats[1] += bw.getRunsConceded();
                stats[2] += bw.getWickets();
            }
        }

        if (allPlayerIds.isEmpty()) return;

        // Determine win/loss for confidence
        UUID winnerId = result.getWinner() != null ? result.getWinner().getId() : null;
        Set<UUID> winningTeamPlayerIds = new HashSet<>();
        Set<UUID> losingTeamPlayerIds = new HashSet<>();
        if (winnerId != null) {
            for (Innings inn : result.getInningsList()) {
                boolean isWinnerBatting = inn.getBattingTeam().getId().equals(winnerId);
                for (BattingScorecard bc : inn.getBattingCards()) {
                    if (isWinnerBatting) winningTeamPlayerIds.add(bc.getPlayer().getId());
                    else losingTeamPlayerIds.add(bc.getPlayer().getId());
                }
                boolean isWinnerBowling = inn.getBowlingTeam().getId().equals(winnerId);
                for (BowlingScorecard bw : inn.getBowlingCards()) {
                    if (isWinnerBowling) winningTeamPlayerIds.add(bw.getPlayer().getId());
                    else losingTeamPlayerIds.add(bw.getPlayer().getId());
                }
            }
        }

        UUID motmId = result.getManOfMatch() != null ? result.getManOfMatch().getId() : null;

        // Format-specific constants
        int baseFitnessLoss;
        double batDivisor, bowlDivisor;
        boolean isFC = "FC".equalsIgnoreCase(format);
        boolean isT20 = "T20".equalsIgnoreCase(format);
        if (isT20) {
            baseFitnessLoss = 3; batDivisor = 40.0; bowlDivisor = 1.5;
        } else if (isFC) {
            baseFitnessLoss = 8; batDivisor = 80.0; bowlDivisor = 5.0;
        } else { // ODI
            baseFitnessLoss = 5; batDivisor = 60.0; bowlDivisor = 3.0;
        }

        // Load all players and update
        List<Player> players = playerRepository.findAllById(allPlayerIds);
        for (Player p : players) {
            UUID pid = p.getId();

            // ── Fitness loss ──
            int[] batS = playerBatStats.getOrDefault(pid, new int[3]);
            double[] bowlS = playerBowlStats.getOrDefault(pid, new double[3]);
            double batLoad = batS[1] / batDivisor;    // balls faced
            double bowlLoad = bowlS[0] / bowlDivisor;  // overs bowled
            double rawLoss = baseFitnessLoss + batLoad + bowlLoad;
            double staminaMulti = 1.3 - (p.getStamina() / 100.0) * 0.6; // 0.7 to 1.3
            int fitnessLoss = (int) Math.round(rawLoss * staminaMulti);
            p.setFitness(Math.max(0, Math.min(100, p.getFitness() - fitnessLoss)));

            // ── Confidence change (format-aware thresholds) ──
            int confDelta = 0;

            // Batting performance — thresholds scale by format
            //   T20: duck −4, <15 −1, 25+ +2, 40+ +4, 75+ +7
            //   ODI: duck −4, <20 −1, 35+ +2, 50+ +4, 100+ +7
            //   FC:  duck −4, <25 −1, 50+ +2, 75+ +4, 150+ +7
            if (batS[1] > 0) {
                int runs = batS[0];
                int ducks = batS[2];
                if (ducks > 0) confDelta -= 4 * ducks;

                int tierSmall, tierMid, tierBig, tierElite;
                if (isT20)     { tierSmall = 15; tierMid = 25; tierBig = 40; tierElite = 75; }
                else if (isFC) { tierSmall = 25; tierMid = 50; tierBig = 75; tierElite = 150; }
                else           { tierSmall = 20; tierMid = 35; tierBig = 50; tierElite = 100; } // ODI

                if (runs >= tierElite)     confDelta += 7;
                else if (runs >= tierBig)  confDelta += 4;
                else if (runs >= tierMid)  confDelta += 2;
                else if (runs < tierSmall && ducks == 0) confDelta -= 1;
            }

            // Bowling performance — economy threshold scales by format
            //   T20: expensive > 10, ODI: > 7, FC: > 4
            if (bowlS[0] > 0) {
                int wkts = (int) bowlS[2];
                double econ = bowlS[1] / Math.max(1.0, bowlS[0]);
                double expensiveThreshold = isT20 ? 10.0 : isFC ? 4.0 : 7.0;

                if (wkts >= 5)      confDelta += 7;
                else if (wkts >= 3) confDelta += 4;
                else if (wkts >= 2) confDelta += 2;
                else if (wkts == 1) confDelta += 1;
                else if (econ > expensiveThreshold) confDelta -= 3;
            }

            // Man of Match bonus
            if (pid.equals(motmId)) confDelta += 5;

            // Win/loss swing
            if (winningTeamPlayerIds.contains(pid)) confDelta += 2;
            else if (losingTeamPlayerIds.contains(pid)) confDelta -= 2;

            p.setConfidence(Math.max(0, Math.min(100, p.getConfidence() + confDelta)));

            // ── Experience gain (diminishing returns) ──
            // Raw XP computed from base + format + performance + MoM,
            // then scaled by 80/(80+currentXP) so gains shrink as XP grows.
            // Soft cap ~100: average players plateau around 80-100 after 3-4 seasons;
            // only consistent standout performers push past.
            int rawXp = 1;
            if (isFC) rawXp += 1;

            // Batting performance XP
            if (batS[1] > 0) {
                int runs = batS[0];
                if (isT20) {
                    if (runs >= 75) rawXp += 2; else if (runs >= 40) rawXp += 1;
                } else if (isFC) {
                    if (runs >= 150) rawXp += 2; else if (runs >= 75) rawXp += 1;
                } else { // ODI
                    if (runs >= 100) rawXp += 2; else if (runs >= 50) rawXp += 1;
                }
            }

            // Bowling performance XP
            if (bowlS[0] > 0) {
                int wkts = (int) bowlS[2];
                if (wkts >= 5) rawXp += 2; else if (wkts >= 3) rawXp += 1;
            }

            // Man of Match
            if (pid.equals(motmId)) rawXp += 1;

            // Diminishing returns: effective = floor(rawXp × 50 / (50 + currentXP))
            int currentXp = p.getExperience();
            int effectiveXp = (int) (rawXp * 50.0 / (50.0 + currentXp));
            p.setExperience(currentXp + effectiveXp);
        }

        playerRepository.saveAll(players);
    }

    private void determineResult(MatchResult result, Innings first, Innings second,
                                 Team battingFirst, Team battingSecond) {
        int firstTotal = first.getTotalRuns();
        int secondTotal = second.getTotalRuns();

        if (secondTotal > firstTotal) {
            // Chasing team won
            result.setWinner(battingSecond);
            result.setResultType("WICKETS");
            result.setResultMargin(10 - second.getTotalWickets());
        } else if (firstTotal > secondTotal) {
            // Batting first team won
            result.setWinner(battingFirst);
            result.setResultType("RUNS");
            result.setResultMargin(firstTotal - secondTotal);
        } else {
            // Tie
            result.setResultType("TIE");
            result.setResultMargin(0);
        }
    }

    // ─── Man of the Match ───────────────────────────────────────

    private Player pickManOfMatch(MatchResult result, Random rng) {
        Map<UUID, Double> scores = new HashMap<>();

        for (Innings inn : result.getInningsList()) {
            for (BattingScorecard bc : inn.getBattingCards()) {
                double pts = bc.getRunsScored() * 1.0
                        + bc.getFours() * 1.5
                        + bc.getSixes() * 2.0;
                if (bc.getRunsScored() >= 50) pts += 15;
                if (bc.getRunsScored() >= 100) pts += 30;
                scores.merge(bc.getPlayer().getId(), pts, Double::sum);
            }
            for (BowlingScorecard bc : inn.getBowlingCards()) {
                double pts = bc.getWickets() * 20.0
                        + bc.getMaidens() * 5.0
                        + bc.getDotBalls() * 0.5;
                if (bc.getWickets() >= 3) pts += 15;
                if (bc.getWickets() >= 5) pts += 30;
                // Economy bonus (lower is better)
                if (bc.getOvers() > 0 && bc.getEconomy() < 5.0) pts += 10;
                scores.merge(bc.getPlayer().getId(), pts, Double::sum);
            }
        }

        if (scores.isEmpty()) return null;

        // Find the highest scoring player
        UUID bestId = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (bestId == null) return null;

        // Find the actual player entity from innings data
        for (Innings inn : result.getInningsList()) {
            for (BattingScorecard bc : inn.getBattingCards()) {
                if (bc.getPlayer().getId().equals(bestId)) return bc.getPlayer();
            }
        }
        return null;
    }

    // ─── Commentary helpers ─────────────────────────────────────

    private String getBoundaryCommentary(Random rng) {
        String[] options = {
                "Driven through the covers", "Cut past point", "Flicked off the pads",
                "Edged past the keeper", "Square driven beautifully", "Pulled to the boundary",
                "Swept fine", "Punched through mid-off", "Driven down the ground",
                "Clipped off the hips to the fence"
        };
        return options[rng.nextInt(options.length)];
    }

    private String getSixCommentary(Random rng) {
        String[] options = {
                "Launched over long-on", "Smashed over midwicket", "Scooped over fine leg",
                "Lofted straight down the ground", "Hammered over extra cover",
                "Heaved over the leg side", "Deposited into the stands",
                "Reverse swept for six", "Stepped out and cleared long-off",
                "Massive hit into the crowd"
        };
        return options[rng.nextInt(options.length)];
    }

    // ─── Scorecard helpers ──────────────────────────────────────

    private BattingScorecard createBatCard(Innings innings, LineupPlayer lp, int position) {
        return BattingScorecard.builder()
                .innings(innings)
                .player(lp.getPlayer())
                .battingPosition(position)
                .build();
    }

    private BowlingScorecard createBowlCard(Innings innings, Player bowler) {
        return BowlingScorecard.builder()
                .innings(innings)
                .player(bowler)
                .build();
    }

    // ─── Utility ────────────────────────────────────────────────

    private int getMaxOvers(String format) {
        if ("T20".equalsIgnoreCase(format)) return 20;
        if ("FC".equalsIgnoreCase(format)) return 50; // per session in FC
        return 50; // ODI
    }

    private int getMaxPerBowler(String format) {
        if ("T20".equalsIgnoreCase(format)) return 4;
        if ("FC".equalsIgnoreCase(format)) return 50; // per innings (no formal cap, fatigue handles it)
        return 10; // ODI
    }

    // ─── Inner classes ──────────────────────────────────────────

    private static class SimContext {
        final Random rng;
        final String pitchType;
        final String condition;
        final int temperature;
        final int maxOvers;
        final int maxPerBowler;
        final String format;
        final boolean isChasing;
        final int target;
        final MatchLineup battingLineup;
        final MatchLineup bowlingLineup;

        SimContext(Random rng, String pitchType, String condition, int temperature,
                   int maxOvers, int maxPerBowler, String format,
                   boolean isChasing, int target,
                   MatchLineup battingLineup, MatchLineup bowlingLineup) {
            this.rng = rng;
            this.pitchType = pitchType;
            this.condition = condition;
            this.temperature = temperature;
            this.maxOvers = maxOvers;
            this.maxPerBowler = maxPerBowler;
            this.format = format;
            this.isChasing = isChasing;
            this.target = target;
            this.battingLineup = battingLineup;
            this.bowlingLineup = bowlingLineup;
        }
    }

    private static class BatsmanState {
        final Player player;
        final LineupPlayer lineupPlayer;

        BatsmanState(LineupPlayer lp) {
            this.player = lp.getPlayer();
            this.lineupPlayer = lp;
        }
    }

    private static class DeliveryResult {
        int runs = 0;
        boolean isWicket = false;
        boolean isBoundary = false;
        boolean isSix = false;
        boolean isWide = false;
        boolean isNoBall = false;
        boolean isBye = false;
        boolean isLegBye = false;
        String dismissalType = null;
        Player fielder = null;
        String commentary = "";
    }

    private static class DismissalInfo {
        final String type;
        final Player fielder;
        final String commentary;

        DismissalInfo(String type, Player fielder, String commentary) {
            this.type = type;
            this.fielder = fielder;
            this.commentary = commentary;
        }
    }

    private static class PitchEffect {
        final double bowlerBonus;

        PitchEffect(double bowlerBonus) {
            this.bowlerBonus = bowlerBonus;
        }
    }

    private static class WeatherEffect {
        final double battingMod;
        final double bowlingMod;

        WeatherEffect(double battingMod, double bowlingMod) {
            this.battingMod = battingMod;
            this.bowlingMod = bowlingMod;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PUBLIC: Team Strength Breakdown (for summary display)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Compute effective team strength breakdown for summary display.
     * Uses the same pitch/weather/experience/confidence formulas as delivery simulation,
     * but excludes per-delivery factors (fatigue, phase, chase pressure).
     */
    public Map<String, Object> computeTeamStrengthBreakdown(
            MatchLineup lineup,
            String pitchType, String condition, int temperature) {

        // Sort all 11 lineup players by batting position — never just players who batted
        List<LineupPlayer> sortedPlayers = new ArrayList<>(lineup.getPlayers());
        sortedPlayers.sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));

        double topOrder = 0, middleOrder = 0, lowerOrder = 0;
        double totalFielding = 0;
        double wkSkill = 0;

        for (LineupPlayer lp : sortedPlayers) {
            Player p = lp.getPlayer();
            int pos = lp.getBattingPosition();

            // Effective batting = same formula as ME minus fatigue/phase/chase
            double batEff = p.getBatRating()
                    + (p.getConfidence() / 100.0) * 6.0
                    + Math.min(p.getExperience() * 0.3, 10.0)
                    + (p.getFitness() / 100.0) * 3.0
                    + getBatterPitchModifier(pitchType, p.getBatHand(), p.getBatRating(), p.getExperience())
                    + getWeatherEffect(condition, temperature, p.getBowlType()).battingMod
                    + getBatterWeatherModifier(condition, temperature, p.getExperience(), p.getBatRating());
            batEff = Math.max(5, Math.min(batEff, 120));

            if (pos <= 3) topOrder += batEff;
            else if (pos <= 7) middleOrder += batEff;
            else lowerOrder += batEff;

            totalFielding += p.getFldRating();
            if ("WK".equals(p.getRole()) || "WK_BAT".equals(p.getRole())
                    || "KEEPER".equals(p.getRole())) {
                wkSkill = Math.max(wkSkill, p.getKeeperRating());
            }
        }

        // Bowling: all distinct planned bowlers from the bowling order (not just those who bowled)
        double seamBowling = 0, spinBowling = 0;
        int seamCount = 0, spinCount = 0;
        Set<String> seamTypes = Set.of("F", "FM", "MF", "M");
        Set<String> spinTypes = Set.of("FS", "WS");
        Set<UUID> seenBowlerIds = new HashSet<>();

        for (BowlingOrder bo : lineup.getBowlingOrders()) {
            Player p = bo.getBowler();
            if (!seenBowlerIds.add(p.getId())) continue; // deduplicate
            String bType = p.getBowlType() != null ? p.getBowlType() : "M";
            PitchEffect pe = getPitchEffect(pitchType, bType);
            WeatherEffect we = getWeatherEffect(condition, temperature, bType);
            double typeMatch = getBowlerTypeMatchup(bType, "RH", pitchType); // avg vs RH

            double bowlEff = p.getBowlRating()
                    + (p.getConfidence() / 100.0) * 5.0
                    + Math.min(p.getExperience() * 0.3, 10.0)
                    + (p.getFitness() / 100.0) * 3.0
                    + pe.bowlerBonus
                    + we.bowlingMod
                    + typeMatch;
            bowlEff = Math.max(5, Math.min(bowlEff, 120));

            if (seamTypes.contains(bType)) { seamBowling += bowlEff; seamCount++; }
            else if (spinTypes.contains(bType)) { spinBowling += bowlEff; spinCount++; }
            else { seamBowling += bowlEff; seamCount++; }
        }

        double fldComponent = totalFielding + wkSkill;
        double total = topOrder + middleOrder + lowerOrder + seamBowling + spinBowling
                + Math.round(fldComponent / 4.0);

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
}
