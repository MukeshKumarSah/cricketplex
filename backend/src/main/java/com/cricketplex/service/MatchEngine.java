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

    private final MatchResultRepository matchResultRepository;
    private final MatchLineupRepository matchLineupRepository;
    private final PlayerRepository playerRepository;
    private final FixtureRepository fixtureRepository;
    private final WeatherService weatherService;

    // ─── Public entry point ──────────────────────────────────────

    @Transactional
    public MatchResult simulateMatch(UUID fixtureId) {
        Fixture fixture = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> new IllegalArgumentException("Fixture not found"));

        if (!"SCHEDULED".equals(fixture.getStatus())) {
            throw new IllegalStateException("Match already played or in progress");
        }
        if (matchResultRepository.existsByFixtureId(fixtureId)) {
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

        // This engine only handles limited-overs formats
        if (!"T20".equalsIgnoreCase(format) && !"ODI".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException("This match engine only supports T20 and ODI formats");
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

        // Pitch
        String pitchType = fixture.getPitchType();

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
        return matchResultRepository.save(result);
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

                // Create ball event
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
                        bowlCard.setWickets(bowlCard.getWickets() + 1);

                        batCard.setDismissalType(delivery.dismissalType);
                        if (!"RUN_OUT".equals(delivery.dismissalType)) {
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
                            BatsmanState temp = striker;
                            striker = nonStriker;
                            nonStriker = temp;
                        }
                    }
                }

                // Chase target check
                if (ctx.isChasing && totalRuns >= ctx.target) {
                    break outerLoop;
                }
            }

            // End of over: rotate strike
            BatsmanState temp = striker;
            striker = nonStriker;
            nonStriker = temp;

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
                + phaseModifier * 2.0;
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

        // ── Chase pressure (format-aware thresholds) ──
        // Positive = hard chase (bowler confident, batter pressured)
        // Negative = easy chase (batter comfortable, bowler under pressure)
        double chasePressure = 0;
        if (ctx.isChasing) {
            int runsNeeded = ctx.target - totalRuns;
            int ballsLeft = (ctx.maxOvers * 6) - legalBallsBowled;
            if (ballsLeft > 0) {
                double requiredRate = (runsNeeded * 6.0) / ballsLeft;
                if ("T20".equalsIgnoreCase(ctx.format)) {
                    if (requiredRate > 14) chasePressure = 10;
                    else if (requiredRate > 12) chasePressure = 7;
                    else if (requiredRate > 10) chasePressure = 4;
                    else if (requiredRate > 8) chasePressure = 1;
                    else if (requiredRate < 5) chasePressure = -3;
                    else if (requiredRate < 3) chasePressure = -5;
                } else if ("ODI".equalsIgnoreCase(ctx.format)) {
                    if (requiredRate > 10) chasePressure = 10;
                    else if (requiredRate > 8) chasePressure = 7;
                    else if (requiredRate > 6) chasePressure = 4;
                    else if (requiredRate > 5) chasePressure = 1;
                    else if (requiredRate < 3) chasePressure = -3;
                    else if (requiredRate < 2) chasePressure = -5;
                }
            }
        }
        bowlStrength += chasePressure;
        // Batter composure: easy chase = calmer batting, hard chase = reckless
        batStrength -= chasePressure * 0.6;
        batStrength = Math.max(5, Math.min(batStrength, 120));

        // ─── WIDE / NO-BALL CHECK ───
        // T20 has stricter wide rules → more wides called; ODI more lenient
        double baseExtra = "T20".equalsIgnoreCase(ctx.format) ? 0.04 : 0.025;
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

        // Format-specific base probabilities
        // T20: ~8.0 RPO → more boundaries, fewer dots, higher risk
        // ODI: ~5.2 RPO → more dots/singles, fewer boundaries, steadier
        double pDot, p1, p2, p3, p4, p6, pWicket;

        if ("T20".equalsIgnoreCase(ctx.format)) {
            pDot    = 0.30;  // T20 has fewer dots
            p1      = 0.24;
            p2      = 0.10;
            p3      = 0.02;
            p4      = 0.15;  // more boundaries
            p6      = 0.08;  // more sixes
            pWicket = 0.055; // slightly higher risk per ball
        } else { // ODI
            pDot    = 0.40;  // ODIs have more dots
            p1      = 0.28;
            p2      = 0.10;
            p3      = 0.03;
            p4      = 0.08;  // fewer boundaries
            p6      = 0.03;  // fewer sixes
            pWicket = 0.04;  // lower per-ball wicket chance
        }

        // Format-scaled shift factor (T20 skill gaps have bigger impact on boundaries)
        double shiftScale = "T20".equalsIgnoreCase(ctx.format) ? 1.3 : 1.0;
        double shift = (skill_diff / 200.0) * shiftScale;
        pDot -= shift * 0.4;
        p1 += shift * 0.12;
        p2 += shift * 0.06;
        p4 += shift * 0.12;
        p6 += shift * 0.08;
        pWicket -= shift * 0.35;

        // Batting aggression extremes — bigger swings in T20, smaller in ODI
        double aggrScale = "T20".equalsIgnoreCase(ctx.format) ? 1.3 : 0.9;
        if ("A".equals(batter.lineupPlayer.getBatAggression())) {
            p4 += 0.03 * aggrScale;
            p6 += 0.035 * aggrScale;
            pWicket += 0.025 * aggrScale;
            pDot -= 0.06 * aggrScale;
            p1 -= 0.025 * aggrScale;
        } else if ("D".equals(batter.lineupPlayer.getBatAggression())) {
            p4 -= 0.02 * aggrScale;
            p6 -= 0.02 * aggrScale;
            pWicket -= 0.02 * aggrScale;
            pDot += 0.04 * aggrScale;
            p1 += 0.02 * aggrScale;
        }

        // Bowling aggression effects
        if ("A".equals(bowlerAggression)) {
            pWicket += 0.015 * aggrScale;
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
            double collapseThreshold = "T20".equalsIgnoreCase(ctx.format) ? 0.65 : 0.50;
            double collapseFactor = 0;
            if (wktsPerOver > collapseThreshold) {
                collapseFactor = Math.min(1.0, (wktsPerOver - collapseThreshold) / 0.8);
            }

            // Also factor in absolute wickets lost — 5+ down is always pressure
            if (totalWickets >= 7) collapseFactor = Math.max(collapseFactor, 0.8);
            else if (totalWickets >= 5) collapseFactor = Math.max(collapseFactor, 0.5);

            // Aggressive players resist consolidation partially (their flaw)
            double resistFactor = "A".equals(playerAggr) ? 0.55 : ("D".equals(playerAggr) ? 1.3 : 1.0);
            double adjustedCollapse = collapseFactor * resistFactor;

            // Shift toward defensive play: more dots/singles, fewer boundaries
            pDot    += 0.08 * adjustedCollapse;
            p1      += 0.04 * adjustedCollapse;
            p4      -= 0.05 * adjustedCollapse;
            p6      -= 0.04 * adjustedCollapse;
            // Aggressive batsmen who ignore collapse lose more wickets
            if ("A".equals(playerAggr) && collapseFactor > 0.3) {
                pWicket += 0.025 * collapseFactor;
            } else {
                pWicket -= 0.01 * adjustedCollapse; // defensive play reduces wicket risk
            }
        }

        // ── 2. Chase situation awareness ──
        // Adjust batting intent based on required rate vs match situation.
        if (ctx.isChasing) {
            int runsNeeded = ctx.target - totalRuns;
            int totalBalls = ctx.maxOvers * 6;
            int ballsRemaining = totalBalls - legalBallsBowled;
            if (ballsRemaining < 1) ballsRemaining = 1;

            double requiredRate = (runsNeeded * 6.0) / ballsRemaining;
            double currentRate = legalBallsBowled > 0 ? (totalRuns * 6.0) / legalBallsBowled : 0;

            // Par rate: what a normal T20/ODI innings scores at
            double parRate = "T20".equalsIgnoreCase(ctx.format) ? 8.0 : 5.0;

            // How far behind/ahead of the required rate
            double rateDiff = requiredRate - parRate; // positive = need to accelerate

            if (runsNeeded <= 0) {
                // Already won — shouldn't reach here but safety
            } else if (requiredRate < parRate * 0.5) {
                // Very easy chase — play very conservatively
                // E.g., chasing 100 in T20: RRR ~5.0 vs par 8.0
                double easyFactor = Math.min(1.0, (parRate * 0.5 - requiredRate) / (parRate * 0.4));
                pDot    += 0.06 * easyFactor;
                p1      += 0.06 * easyFactor;
                p4      -= 0.06 * easyFactor;
                p6      -= 0.05 * easyFactor;
                pWicket -= 0.015 * easyFactor;
                // Aggressive players still attempt more shots but modestly
                if ("A".equals(playerAggr)) {
                    pDot -= 0.02 * easyFactor;
                    p4   += 0.02 * easyFactor;
                    pWicket += 0.01 * easyFactor; // slight risk
                }
            } else if (requiredRate < parRate * 0.8) {
                // Comfortable chase — slightly conservative
                double comfortFactor = Math.min(1.0, (parRate * 0.8 - requiredRate) / (parRate * 0.3));
                pDot += 0.03 * comfortFactor;
                p1   += 0.03 * comfortFactor;
                p4   -= 0.03 * comfortFactor;
                p6   -= 0.02 * comfortFactor;
                pWicket -= 0.005 * comfortFactor;
            } else if (requiredRate > parRate * 1.5) {
                // Desperate chase — go all out
                double desperateFactor = Math.min(1.0, (requiredRate - parRate * 1.5) / (parRate * 0.5));
                pDot -= 0.08 * desperateFactor;
                p1   -= 0.03 * desperateFactor;
                p4   += 0.04 * desperateFactor;
                p6   += 0.05 * desperateFactor;
                pWicket += 0.03 * desperateFactor;
            } else if (requiredRate > parRate * 1.2) {
                // Need to accelerate
                double pushFactor = Math.min(1.0, (requiredRate - parRate * 1.2) / (parRate * 0.3));
                pDot -= 0.04 * pushFactor;
                p4   += 0.02 * pushFactor;
                p6   += 0.02 * pushFactor;
                pWicket += 0.01 * pushFactor;
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

            // Determine dismissal type based on bowler type, fielding, keeper
            DismissalInfo dismissal = determineDismissal(
                    rng, bowler, batter, teamFieldingAvg, keeperSkill, ctx);
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
            double fieldingAvg, double keeperSkill, SimContext ctx) {

        String bowlType = bowler.getBowlType();
        boolean isPace = bowlType != null && (bowlType.equals("F") || bowlType.equals("FM") || bowlType.equals("MF") || bowlType.equals("M"));
        boolean isSpin = bowlType != null && (bowlType.equals("FS") || bowlType.equals("WS"));

        // Probability weights for dismissal types
        double pBowled = isPace ? 0.22 : (isSpin ? 0.18 : 0.20);
        double pCaught = 0.40;  // most common
        double pLBW = isPace ? 0.15 : (isSpin ? 0.22 : 0.15);
        double pStumped = isSpin ? 0.10 : 0.02;
        double pRunOut = 0.08;
        double pCaughtBehind = isPace ? 0.10 : 0.05;
        double pHitWicket = 0.02;

        // Fielding quality affects catches
        pCaught += fieldingAvg * 0.002;
        pCaughtBehind += keeperSkill * 0.001;
        pStumped += keeperSkill * 0.0015;

        // Pitch effects
        if ("GREEN".equals(ctx.pitchType) || "BOUNCY".equals(ctx.pitchType)) {
            pCaughtBehind += 0.04;
            pBowled += 0.03;
        }
        if ("DUSTY".equals(ctx.pitchType) || "DRY".equals(ctx.pitchType)) {
            pStumped += 0.04;
            pLBW += 0.03;
        }

        double dTotal = pBowled + pCaught + pLBW + pStumped + pRunOut + pCaughtBehind + pHitWicket;
        double dRoll = rng.nextDouble() * dTotal;
        double dCum = 0;

        // Select random fielder from bowling lineup
        List<LineupPlayer> fieldingPlayers = ctx.bowlingLineup.getPlayers();
        Player randomFielder = fieldingPlayers.get(rng.nextInt(fieldingPlayers.size())).getPlayer();
        Player keeper = ctx.bowlingLineup.getKeeper();

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
            if (overNumber <= 6) return 3;
            if (overNumber <= 15) return -1;
            return 5;
        } else if ("ODI".equalsIgnoreCase(format)) {
            // ODI overs 1-10 = powerplay (some runs, but wickets too)
            // ODI overs 11-30 = consolidation (lowest scoring phase)
            // ODI overs 31-40 = buildup (acceleration begins)
            // ODI overs 41-50 = slog/death (big runs)
            if (overNumber <= 10) return 1.5;
            if (overNumber <= 30) return -2;
            if (overNumber <= 40) return 1;
            return 3.5;
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

    // ─── Bot lineup auto-generation ─────────────────────────────

    private MatchLineup getOrGenerateLineup(Fixture fixture, Team team, String format) {
        Optional<MatchLineup> existing = matchLineupRepository.findByFixtureIdAndTeamId(fixture.getId(), team.getId());
        if (existing.isPresent()) return existing.get();

        // Auto-generate for bot teams
        if (!Boolean.TRUE.equals(team.getIsBot())) {
            throw new IllegalStateException("No lineup found for non-bot team: " + team.getTeamName());
        }

        List<Player> squad = playerRepository.findByTeam(team);
        if (squad.size() < 11) {
            throw new IllegalStateException("Bot team " + team.getTeamName() + " has fewer than 11 players");
        }

        // Sort by rating descending
        squad.sort((a, b) -> Integer.compare(b.getRating(), a.getRating()));

        // Pick best 11 ensuring role balance
        List<Player> selected = selectBotPlaying11(squad);

        // Sort selected by role for batting order
        List<Player> battingOrder = buildBotBattingOrder(selected);

        MatchLineup lineup = MatchLineup.builder()
                .fixture(fixture)
                .team(team)
                .bowlingPlan("BALANCED")
                .build();

        // Find keeper
        Player keeper = selected.stream()
                .filter(p -> "KEEPER".equals(p.getRole()))
                .findFirst()
                .orElse(selected.stream()
                        .max(Comparator.comparingInt(Player::getKeeperRating))
                        .orElse(selected.get(0)));
        lineup.setKeeper(keeper);

        // Captain = highest rated
        Player captain = selected.stream()
                .max(Comparator.comparingInt(Player::getRating))
                .orElse(selected.get(0));
        lineup.setCaptain(captain);

        // Build batting order
        for (int i = 0; i < battingOrder.size(); i++) {
            LineupPlayer lp = LineupPlayer.builder()
                    .lineup(lineup)
                    .player(battingOrder.get(i))
                    .battingPosition(i + 1)
                    .batAggression(battingOrder.get(i).getBatAggression())
                    .build();
            lineup.getPlayers().add(lp);
        }

        // Build bowling orders
        List<Player> topBowlers = selected.stream()
                .filter(p -> p.getBowlRating() >= 25)
                .sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating()))
                .limit(5)
                .toList();

        List<Player> bowlers = new ArrayList<>(topBowlers);
        if (bowlers.size() < 5) {
            // Add more players by bowl rating
            List<Player> extras = selected.stream()
                    .filter(p -> !topBowlers.contains(p))
                    .sorted((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating()))
                    .limit(5 - bowlers.size())
                    .toList();
            bowlers.addAll(extras);
        }

        int maxOvers = getMaxOvers(format);
        for (int over = 1; over <= maxOvers; over++) {
            Player bowler = bowlers.get((over - 1) % bowlers.size());
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

    private List<Player> selectBotPlaying11(List<Player> squad) {
        List<Player> selected = new ArrayList<>();
        int batsmen = 0, bowlers = 0, allRounders = 0, keepers = 0;

        for (Player p : squad) {
            if (selected.size() >= 11) break;
            String role = p.getRole();
            switch (role) {
                case "KEEPER" -> {
                    if (keepers < 1) { selected.add(p); keepers++; }
                }
                case "BATSMAN" -> {
                    if (batsmen < 4) { selected.add(p); batsmen++; }
                }
                case "ALL_ROUNDER" -> {
                    if (allRounders < 3) { selected.add(p); allRounders++; }
                }
                case "BOWLER" -> {
                    if (bowlers < 4) { selected.add(p); bowlers++; }
                }
            }
        }

        // Fill remaining with best available
        for (Player p : squad) {
            if (selected.size() >= 11) break;
            if (!selected.contains(p)) {
                selected.add(p);
            }
        }

        return selected.subList(0, Math.min(11, selected.size()));
    }

    private List<Player> buildBotBattingOrder(List<Player> selected) {
        List<Player> order = new ArrayList<>();
        List<Player> pool = new ArrayList<>(selected);

        // Openers: 2 best batsmen
        pool.sort((a, b) -> Integer.compare(b.getBatRating(), a.getBatRating()));
        for (int i = 0; i < Math.min(2, pool.size()); i++) {
            order.add(pool.get(i));
        }
        pool.removeAll(order);

        // #3-#5: next best batsmen/all-rounders
        pool.sort((a, b) -> Integer.compare(b.getBatRating(), a.getBatRating()));
        for (int i = 0; i < Math.min(3, pool.size()); i++) {
            order.add(pool.get(i));
        }
        pool.subList(0, Math.min(3, pool.size())).clear();

        // #6-#7: Keeper + all rounder
        List<Player> middle = new ArrayList<>(pool);
        middle.sort((a, b) -> Integer.compare(
                b.getBatRating() + b.getBowlRating(),
                a.getBatRating() + a.getBowlRating()));
        for (int i = 0; i < Math.min(2, middle.size()); i++) {
            order.add(middle.get(i));
        }
        pool.removeAll(order);

        // #8-#11: bowlers
        pool.sort((a, b) -> Integer.compare(b.getBowlRating(), a.getBowlRating()));
        order.addAll(pool);

        return order;
    }

    // ─── Result determination ───────────────────────────────────

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
        return "T20".equalsIgnoreCase(format) ? 20 : 50;
    }

    private int getMaxPerBowler(String format) {
        return "T20".equalsIgnoreCase(format) ? 4 : 10;
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
}
