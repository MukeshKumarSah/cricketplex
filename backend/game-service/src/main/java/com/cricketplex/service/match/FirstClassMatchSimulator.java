package com.cricketplex.service.match;

import com.cricketplex.entity.*;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.MatchResultRepository;
import com.cricketplex.service.FixtureService;
import com.cricketplex.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class FirstClassMatchSimulator {
    private final MatchResultRepository matchResultRepository;
    private final FixtureRepository fixtureRepository;
    private final WeatherService weatherService;
    private final FixtureService fixtureService;
    private final LineupGenerator lineupGenerator;
    private final DeliverySimulator deliverySimulator;
    private final PostMatchProcessor postMatchProcessor;
    private final FirstClassSupport firstClassSupport;
    private final InningsSimulator inningsSimulator;

    public MatchResult saveFCDay1Complete(Fixture fixture, MatchResult result) {
        result.setResultType("PENDING");
        fixture.setFcDay(1);
        fixture.setStatus("FC_DAY1_COMPLETE");
        if (fixture.getMatchDate() != null) fixture.setMatchDate(fixture.getMatchDate().plusDays(1));
        fixtureRepository.save(fixture);
        return matchResultRepository.save(result);
    }

    public MatchResult finalizeFCMatch(Fixture fixture, MatchResult result, Random rng) {
        result.setManOfMatch(postMatchProcessor.pickManOfMatch(result, rng));
        fixture.setFcDay(2);
        fixture.setStatus("COMPLETED");
        fixtureRepository.save(fixture);
        boolean isSim = fixture.getSimSessionId() != null;
        if (!isSim && fixture.getLeague() != null) {
            postMatchProcessor.updateMoraleAndFans(result, fixture, "FC");
            postMatchProcessor.updatePlayerStats(result, "FC");
            postMatchProcessor.distributeGateMoney(result, fixture);
        }
        MatchResult saved = matchResultRepository.save(result);
        if (!isSim) fixtureService.applyPendingSwap(fixture.getId(), saved);
        return saved;
    }

    public MatchResult determineFCResultAndFinalize(Fixture fixture, MatchResult result,
                                                      Team battingFirst, Team battingSecond, Random rng) {
        List<Innings> inns = result.getInningsList();
        if (inns.size() >= 4) {
            boolean fo = inns.get(2).getBattingTeam().getId().equals(battingSecond.getId());
            firstClassSupport.determineFCResult(result, inns.get(0).getTotalRuns(), inns.get(1).getTotalRuns(),
                    inns.get(2).getTotalRuns(), inns.get(3).getTotalRuns(),
                    battingFirst, battingSecond, fo, false);
        } else if (inns.size() == 3) {
            boolean fo = inns.get(2).getBattingTeam().getId().equals(battingSecond.getId());
            int i1 = inns.get(0).getTotalRuns(), i2 = inns.get(1).getTotalRuns(),
                    i3 = inns.get(2).getTotalRuns();
            int overallLead = fo ? i1 - (i2 + i3) : (i1 + i3) - i2;
            if ((fo && overallLead > 0) || (!fo && overallLead < 0))
                firstClassSupport.determineFCResult(result, i1, i2, i3, 0, battingFirst, battingSecond, fo, true);
            else result.setResultType("DRAW");
        } else {
            result.setResultType("DRAW");
        }
        return finalizeFCMatch(fixture, result, rng);
    }

    public MatchResult simulateFCDay1(Fixture fixture, String format, Team homeTeam, Team awayTeam) {
        MatchLineup homeLineup = lineupGenerator.getOrGenerateLineup(fixture, homeTeam, format);
        MatchLineup awayLineup = lineupGenerator.getOrGenerateLineup(fixture, awayTeam, format);
        Map<String, Object> weather = weatherService.getWeather(homeTeam.getCountry(), fixture.getMatchDate());
        String condition = (String) weather.get("condition");
        int temperature  = (int)    weather.get("temperature");
        String pitchType = fixture.getPitchType();

        boolean isSim  = fixture.getSimSessionId() != null;
        long baseSeed  = fixture.getId().getMostSignificantBits() ^ fixture.getMatchDate().toEpochDay();
        Random rng     = new Random(isSim ? (baseSeed ^ System.nanoTime()) : baseSeed);

        boolean homeToss    = rng.nextBoolean();
        Team tossWinner     = homeToss ? homeTeam : awayTeam;
        String tossDecision = inningsSimulator.decideToss(homeToss ? homeLineup : awayLineup,
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

    public MatchResult simulateFCDay2(Fixture fixture, String format) {
        MatchResult result = matchResultRepository.findByFixtureIdWithInnings(fixture.getId())
                .orElseThrow(() -> new IllegalStateException("No Day 1 result found"));
        Team homeTeam = fixture.getHomeTeam();
        Team awayTeam = fixture.getAwayTeam();
        MatchLineup homeLineup = lineupGenerator.getOrGenerateLineup(fixture, homeTeam, format);
        MatchLineup awayLineup = lineupGenerator.getOrGenerateLineup(fixture, awayTeam, format);
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
        int totalUsed    = inns.stream().mapToInt(firstClassSupport::getOversUsed).sum();
        int dayLimit     = Math.min(150, 300 - totalUsed);
        return runFCInningsLoop(fixture, result, battingFirst, battingSecond,
                bat1Lineup, bat2Lineup, dayLimit, rng, pitchType, condition, temperature, format);
    }

    public MatchResult runFCInningsLoop(Fixture fixture, MatchResult result,
                                          Team battingFirst, Team battingSecond,
                                          MatchLineup bat1Lineup, MatchLineup bat2Lineup,
                                          int dayOversLimit, Random rng, String pitchType,
                                          String condition, int temperature, String format) {
        Map<UUID, MatchFCStrategy> strategiesByTeam = firstClassSupport.loadFCStrategies(fixture);
        List<Innings> inningsList = result.getInningsList();
        int totalMatchOvers    = inningsList.stream().mapToInt(firstClassSupport::getOversUsed).sum();
        int dayOversUsed       = 0;
        int matchOversLimit    = 300;
        int maxOversPerInnings = 300;  // FIX: Allow full 300 overs per innings (unlimited in real FC)
        int maxPerBowler       = 50;

        // Resume interrupted innings
        if (!inningsList.isEmpty()) {
            Innings last = inningsList.get(inningsList.size() - 1);
            if (last.getResumeState() != null) {
                int oversBefore        = firstClassSupport.getOversUsed(last);
                int inningsOversPlayed = firstClassSupport.computeBallsBowled(last.getTotalOvers()) / 6;
                int maxForResume = Math.min(maxOversPerInnings,
                        inningsOversPlayed + Math.min(matchOversLimit - totalMatchOvers, dayOversLimit));
                int inningsNum    = last.getInningsNumber();
                boolean canDeclare = false; int declareAt = 0;
                boolean isChasing  = false; int chaseTarget = 0;

                if (inningsNum == 1) {
                    Integer dec = firstClassSupport.getDeclareInn1(fixture, strategiesByTeam, last.getBattingTeam());
                    if (firstClassSupport.isHumanTeam(last.getBattingTeam()) && dec != null && dec > 0) { canDeclare = true; declareAt = dec; }
                } else if (inningsNum == 2) {
                    int i1r = inningsList.get(0).getTotalRuns();
                    Integer decLead = firstClassSupport.getDeclareInn2Lead(fixture, strategiesByTeam, last.getBattingTeam());
                    if (firstClassSupport.isHumanTeam(last.getBattingTeam()) && decLead != null && decLead > 0) { canDeclare = true; declareAt = i1r + decLead; }
                } else if (inningsNum == 3) {
                    long[] dc = firstClassSupport.computeInn3Declaration(fixture, strategiesByTeam, inningsList, last.getBattingTeam(), battingFirst, battingSecond);
                    canDeclare = dc[0] > 0; declareAt = (int) dc[1];
                } else if (inningsNum == 4) {
                    isChasing = true; chaseTarget = firstClassSupport.computeFCChaseTarget(inningsList, battingSecond);
                }

                MatchLineup batL  = last.getBattingTeam().getId().equals(battingFirst.getId()) ? bat1Lineup : bat2Lineup;
                MatchLineup bowlL = last.getBowlingTeam().getId().equals(battingFirst.getId()) ? bat1Lineup : bat2Lineup;
                SimContext ctx = new SimContext(rng, pitchType, condition, temperature,
                        maxForResume, maxPerBowler, format, isChasing, chaseTarget, batL, bowlL);
                simulateInningsWithDeclaration(last, ctx, canDeclare, declareAt, true);

                int newOvers = firstClassSupport.getOversUsed(last) - oversBefore;
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
                    Integer d1 = firstClassSupport.getDeclareInn1(fixture, strategiesByTeam, bat);
                    if (firstClassSupport.isHumanTeam(bat) && d1 != null && d1 > 0) { canDeclare = true; declareAt = d1; }
                }
                case 2 -> {
                    bat = battingSecond; bowl = battingFirst; batL = bat2Lineup; bowlL = bat1Lineup;
                    Integer d2 = firstClassSupport.getDeclareInn2Lead(fixture, strategiesByTeam, bat);
                    if (firstClassSupport.isHumanTeam(bat) && d2 != null && d2 > 0) { canDeclare = true; declareAt = inningsList.get(0).getTotalRuns() + d2; }
                }
                case 3 -> {
                    int i1 = inningsList.get(0).getTotalRuns(), i2 = inningsList.get(1).getTotalRuns();
                    // FIX: follow-on threshold correct for match length (150-over FC = 2-day = 100 runs)
                    int followOnThreshold = (matchOversLimit <= 200) ? 100 : 200;
                    Boolean foChoice = firstClassSupport.getFollowOn(fixture, strategiesByTeam, battingFirst);
                    boolean followOn  = (i1 - i2) >= followOnThreshold && (foChoice != null ? foChoice : true);
                    if (followOn) { bat = battingSecond; bowl = battingFirst; batL = bat2Lineup; bowlL = bat1Lineup; }
                    else          { bat = battingFirst;  bowl = battingSecond; batL = bat1Lineup; bowlL = bat2Lineup; }
                    long[] dc = firstClassSupport.computeInn3Declaration(fixture, strategiesByTeam, inningsList, bat, battingFirst, battingSecond);
                    canDeclare = dc[0] > 0; declareAt = (int) dc[1];
                }
                case 4 -> {
                    int i1 = inningsList.get(0).getTotalRuns(), i2 = inningsList.get(1).getTotalRuns(),
                            i3 = inningsList.get(2).getTotalRuns();
                    boolean fo = inningsList.get(2).getBattingTeam().getId().equals(battingSecond.getId());
                    int overallLead = fo ? i1 - (i2 + i3) : (i1 + i3) - i2;
                    if ((fo && overallLead > 0) || (!fo && overallLead < 0)) {
                        firstClassSupport.determineFCResult(result, i1, i2, i3, 0, battingFirst, battingSecond, fo, true);
                        return finalizeFCMatch(fixture, result, rng);
                    }
                    chaseTarget = firstClassSupport.computeFCChaseTarget(inningsList, battingSecond);
                    if (chaseTarget <= 0) {
                        firstClassSupport.determineFCResult(result, i1, i2, i3, 0, battingFirst, battingSecond, fo, false);
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

            int oversUsed = firstClassSupport.getOversUsed(inn);
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

    public void simulateInningsWithDeclaration(Innings innings, SimContext ctx,
                                                 boolean canDeclare, int declareAtRuns,
                                                 boolean fcDayMode) {
        List<LineupPlayer> battingOrder = new ArrayList<>(ctx.battingLineup.getPlayers());
        battingOrder.sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));
        List<BowlingOrder> bowlingOrders = new ArrayList<>(ctx.bowlingLineup.getBowlingOrders());
        bowlingOrders.sort(Comparator.comparingInt(BowlingOrder::getOverNumber));
        Map<Integer, BowlingOrder> bowlerPlan = new LinkedHashMap<>();
        for (BowlingOrder bo : bowlingOrders) bowlerPlan.put(bo.getOverNumber(), bo);

        double teamFieldingAvg = deliverySimulator.calculateTeamFielding(ctx.bowlingLineup);
        double keeperSkill     = deliverySimulator.getKeeperRating(ctx.bowlingLineup);
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
            FCResumeState resume = firstClassSupport.parseResumeState(resumeJson);
            totalRuns     = innings.getTotalRuns();
            totalWickets  = innings.getTotalWickets();
            totalExtras   = innings.getExtras();
            ballsBowled   = firstClassSupport.computeBallsBowled(innings.getTotalOvers());
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
            batCards.put(striker.player.getId(),    ScorecardFactory.createBatCard(innings, striker.lineupPlayer,    1));
            batCards.put(nonStriker.player.getId(), ScorecardFactory.createBatCard(innings, nonStriker.lineupPlayer, 2));
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
            if (!foundPlanned) { currentBowler = deliverySimulator.pickFallbackBowler(ctx, bowlerBallCounts, previousBowler, overNumber); currentBowlerAggression = "N"; }

            if (!bowlCards.containsKey(currentBowler.getId()))
                bowlCards.put(currentBowler.getId(), ScorecardFactory.createBowlCard(innings, currentBowler));
            BowlingScorecard bowlCard = bowlCards.get(currentBowler.getId());

            int legalBallsThisOver = 0, runsThisOver = 0;
            boolean maidenPossible = true; int ballInOver = 0;
            int currentBowlerBalls = bowlerBallCounts.getOrDefault(currentBowler.getId(), 0);

            while (legalBallsThisOver < 6) {
                ballInOver++;
                int battingPos = striker.lineupPlayer.getBattingPosition() != null
                        ? striker.lineupPlayer.getBattingPosition() : 5;
                DeliveryResult delivery = deliverySimulator.simulateDelivery(ctx, striker, nonStriker, currentBowler,
                        currentBowlerAggression, teamFieldingAvg, keeperSkill, overNumber, totalRuns,
                        totalWickets, ballsBowled, batCards.get(striker.player.getId()),
                        currentBowlerBalls, isFreeHit, previousBatHand, partnershipBalls,
                        battingPos, false);
                deliverySimulator.applyStrikeFarming(ctx, striker, nonStriker, delivery, legalBallsThisOver);

                if (!BallEventSkip.isSkip()) {
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
                        if (nso) { nonStriker = new BatsmanState(battingOrder.get(nextBatIdx)); batCards.put(nonStriker.player.getId(), ScorecardFactory.createBatCard(innings, nonStriker.lineupPlayer, nextBatIdx + 1)); }
                        else     { striker    = new BatsmanState(battingOrder.get(nextBatIdx)); batCards.put(striker.player.getId(), ScorecardFactory.createBatCard(innings, striker.lineupPlayer, nextBatIdx + 1)); }
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
                innings.setResumeState(firstClassSupport.buildResumeState(striker, nonStriker, nextBatIdx, bowlerBallCounts, previousBowler));
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

}
