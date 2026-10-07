package com.cricketplex.service.match;

import com.cricketplex.entity.*;
import com.cricketplex.repository.PlayerRepository;
import com.cricketplex.repository.StadiumSeatsRepository;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.TransactionLogRepository;
import com.cricketplex.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class PostMatchProcessor {
    private final ActivityLogService activityLogService;
    private final StadiumSeatsRepository stadiumSeatsRepository;
    private final TeamRepository teamRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final PlayerRepository playerRepository;

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

    public void distributeGateMoney(MatchResult matchResult, Fixture fixture) {
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

    public int getRatingForFormat(Team team, String format) {
        return switch (format.toUpperCase()) {
            case "ODI"       -> team.getOdiRating() != null ? team.getOdiRating() : 1000;
            case "FC","TEST" -> team.getFcRating()  != null ? team.getFcRating()  : 1000;
            default          -> team.getT20Rating() != null ? team.getT20Rating() : 1000;
        };
    }

    public void normalize(double[] weights) {
        double total = 0.0;
        for (double w : weights) total += w;
        if (total <= 0.0) { Arrays.fill(weights, 0.25); return; }
        for (int i = 0; i < weights.length; i++) weights[i] /= total;
    }
    public int[] allocateDemand(int total, double[] weights) {
        int[] result = new int[weights.length]; double[] fractions = new double[weights.length]; int allocated = 0;
        for (int i = 0; i < weights.length; i++) { double raw = total * weights[i]; result[i] = (int) Math.floor(raw); fractions[i] = raw - result[i]; allocated += result[i]; }
        while (allocated < total) { int best = 0; for (int i = 1; i < fractions.length; i++) if (fractions[i] > fractions[best]) best = i; result[best]++; fractions[best] = -1.0; allocated++; }
        return result;
    }
    public int[] roundDemand(double[] raw) {
        int[] result = new int[raw.length]; double[] fractions = new double[raw.length]; int targetTotal = (int) Math.round(Arrays.stream(raw).sum()); int allocated = 0;
        for (int i = 0; i < raw.length; i++) { result[i] = (int) Math.floor(raw[i]); fractions[i] = raw[i] - result[i]; allocated += result[i]; }
        while (allocated < targetTotal) { int best = 0; for (int i = 1; i < fractions.length; i++) if (fractions[i] > fractions[best]) best = i; if (fractions[best] <= 0) break; result[best]++; fractions[best] = -1.0; allocated++; }
        return result;
    }
    public int sum(int[] values) { int t = 0; for (int v : values) t += v; return t; }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MORALE & FANS
    // ═══════════════════════════════════════════════════════════════════════════

    public void updateMoraleAndFans(MatchResult result, Fixture fixture, String format) {
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

    public void applyMoraleDelta(Team team, int resultDelta) {
        int current = team.getMorale() != null ? team.getMorale() : 50;
        List<Player> squad = playerRepository.findByTeam(team);
        double avgConfidence = squad.isEmpty() ? 50.0 : squad.stream().mapToDouble(Player::getConfidence).average().orElse(50.0);
        double squadPull     = (avgConfidence - current) * 0.15;
        int academyBenchmark = (team.getAcademyLevel() != null ? team.getAcademyLevel() : 1) * 25;
        double academyPull   = (academyBenchmark - current) * 0.10;
        team.setMorale(Math.max(0, Math.min(100, (int) Math.round(current + resultDelta + squadPull + academyPull))));
    }

    public void applyFanDelta(Team team, String outcome) {
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

    public void applyEloUpdate(Team teamA, Team teamB, double scoreA, double scoreB, String format) {
        int K = 20, rA = getFormatRating(teamA, format), rB = getFormatRating(teamB, format);
        double eA = 1.0 / (1.0 + Math.pow(10.0, (rB - rA) / 400.0));
        setFormatRating(teamA, format, Math.max(100, (int) Math.round(rA + K * (scoreA - eA))));
        setFormatRating(teamB, format, Math.max(100, (int) Math.round(rB + K * (scoreB - (1.0 - eA)))));
    }

    public int getFormatRating(Team team, String format) {
        if ("T20".equalsIgnoreCase(format)) return team.getT20Rating() != null ? team.getT20Rating() : 1000;
        if ("FC".equalsIgnoreCase(format))  return team.getFcRating()  != null ? team.getFcRating()  : 1000;
        return team.getOdiRating() != null ? team.getOdiRating() : 1000;
    }
    public void setFormatRating(Team team, String format, int rating) {
        if ("T20".equalsIgnoreCase(format))     team.setT20Rating(rating);
        else if ("FC".equalsIgnoreCase(format))  team.setFcRating(rating);
        else                                      team.setOdiRating(rating);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PLAYER STATS UPDATE (post-match)
    // ═══════════════════════════════════════════════════════════════════════════

    public void updatePlayerStats(MatchResult result, String format) {
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

    public Player pickManOfMatch(MatchResult result, Random rng) {
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

}
