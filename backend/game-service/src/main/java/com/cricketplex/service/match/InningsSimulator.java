package com.cricketplex.service.match;

import com.cricketplex.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class InningsSimulator {
    private final DeliverySimulator deliverySimulator;

    public void simulateSuperOver(MatchResult result, Random rng,
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

    public int simulateSuperOverInnings(Random rng, String pitchType, String condition,
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
        double fieldingAvg = deliverySimulator.calculateTeamFielding(lineup);
        double keeperSkill = deliverySimulator.getKeeperRating(lineup);

        SimContext ctx = new SimContext(rng, pitchType, condition, temperature,
                1, 1, format, false, 0, lineup, lineup);

        int totalRuns = 0, wickets = 0, balls = 0;
        Map<UUID, BattingScorecard> dummyCards = new HashMap<>();
        dummyCards.put(striker.player.getId(), BattingScorecard.builder().build());

        while (balls < 6 && wickets < 2) {
            BattingScorecard batCard = dummyCards.computeIfAbsent(striker.player.getId(),
                    k -> BattingScorecard.builder().build());
            DeliveryResult dr = deliverySimulator.simulateDelivery(ctx, striker, nonStriker, bowler, "A",
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

    public int countMatchBoundaries(MatchResult result, Team team) {
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

    public void simulateInnings(Innings innings, SimContext ctx) {
        List<LineupPlayer> battingOrder = new ArrayList<>(ctx.battingLineup.getPlayers());
        battingOrder.sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));

        List<BowlingOrder> bowlingOrders = new ArrayList<>(ctx.bowlingLineup.getBowlingOrders());
        bowlingOrders.sort(Comparator.comparingInt(BowlingOrder::getOverNumber));

        Map<Integer, BowlingOrder> bowlerPlan = new LinkedHashMap<>();
        for (BowlingOrder bo : bowlingOrders) bowlerPlan.put(bo.getOverNumber(), bo);

        double teamFieldingAvg = deliverySimulator.calculateTeamFielding(ctx.bowlingLineup);
        double keeperSkill     = deliverySimulator.getKeeperRating(ctx.bowlingLineup);

        if (battingOrder.size() < 2) return;

        int nextBatIdx   = 2;
        BatsmanState striker    = new BatsmanState(battingOrder.get(0));
        BatsmanState nonStriker = new BatsmanState(battingOrder.get(1));

        Map<UUID, BattingScorecard> batCards  = new LinkedHashMap<>();
        Map<UUID, BowlingScorecard> bowlCards = new LinkedHashMap<>();
        batCards.put(striker.player.getId(),    ScorecardFactory.createBatCard(innings, striker.lineupPlayer,    1));
        batCards.put(nonStriker.player.getId(), ScorecardFactory.createBatCard(innings, nonStriker.lineupPlayer, 2));

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
                currentBowler = deliverySimulator.pickFallbackBowler(ctx, bowlerBallCounts, previousBowler, overNumber);
                currentBowlerAggression = "N";
            }

            if (!bowlCards.containsKey(currentBowler.getId()))
                bowlCards.put(currentBowler.getId(), ScorecardFactory.createBowlCard(innings, currentBowler));
            BowlingScorecard bowlCard = bowlCards.get(currentBowler.getId());

            int legalBallsThisOver = 0, runsThisOver = 0;
            boolean maidenPossible  = true;
            int ballInOver          = 0;
            int currentBowlerBalls  = bowlerBallCounts.getOrDefault(currentBowler.getId(), 0);

            while (legalBallsThisOver < 6) {
                ballInOver++;

                DeliveryResult delivery = deliverySimulator.simulateDelivery(
                        ctx, striker, nonStriker, currentBowler, currentBowlerAggression,
                        teamFieldingAvg, keeperSkill, overNumber, totalRuns, totalWickets,
                        ballsBowled, batCards.get(striker.player.getId()),
                        currentBowlerBalls, isFreeHit, previousBatHand, partnershipBalls,
                        striker.lineupPlayer.getBattingPosition(), battingPpActive);

                deliverySimulator.applyStrikeFarming(ctx, striker, nonStriker, delivery, legalBallsThisOver);

                if (!BallEventSkip.isSkip()) {
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
                                    ScorecardFactory.createBatCard(innings, striker.lineupPlayer, nextBatIdx + 1));
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
                                    ScorecardFactory.createBatCard(innings, nonStriker.lineupPlayer, nextBatIdx + 1));
                        } else {
                            striker = new BatsmanState(battingOrder.get(nextBatIdx));
                            batCards.put(striker.player.getId(),
                                    ScorecardFactory.createBatCard(innings, striker.lineupPlayer, nextBatIdx + 1));
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
    //  TOSS DECISION — FORMAT-AWARE
    // ═══════════════════════════════════════════════════════════════════════════

    public String decideToss(MatchLineup lineup, String pitchType, String weatherCondition,
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

    public void determineResult(MatchResult result, Innings first, Innings second,
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

}
