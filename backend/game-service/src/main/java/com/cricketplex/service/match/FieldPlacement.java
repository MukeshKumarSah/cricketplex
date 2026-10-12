package com.cricketplex.service.match;

import com.cricketplex.entity.LineupPlayer;
import com.cricketplex.entity.Player;

import java.util.*;

public final class FieldPlacement {
    private FieldPlacement() {}

    private static final Set<String> CATCHING = Set.of("slip", "gully", "short leg", "point", "cover");
    private static final Set<String> BOUNDARY = Set.of(
            "third man", "fine leg", "long-off", "long-on",
            "deep mid-wicket", "deep square", "deep cover");
    private static final List<String> CATCH_ORDER = List.of(
            "slip", "gully", "short leg", "point", "cover", "extra cover");

    public static final class FieldSet {
        public final Map<String, Player> byPosition;
        public final Player keeper;
        public final Player bowler;
        public final String situation;
        public final double p4Mod;
        public final double p6Mod;
        public final double pWicketMod;

        FieldSet(Map<String, Player> byPosition, Player keeper, Player bowler, String situation,
                 double p4Mod, double p6Mod, double pWicketMod) {
            this.byPosition = byPosition;
            this.keeper = keeper;
            this.bowler = bowler;
            this.situation = situation;
            this.p4Mod = p4Mod;
            this.p6Mod = p6Mod;
            this.pWicketMod = pWicketMod;
        }

        public String positionOf(Player p) {
            if (p == null) return null;
            if (keeper != null && p.getId().equals(keeper.getId())) return "wicket-keeper";
            for (Map.Entry<String, Player> e : byPosition.entrySet()) {
                if (e.getValue() != null && p.getId().equals(e.getValue().getId())) return e.getKey();
            }
            return null;
        }

        public Player pickCatcher(Random rng, String bowlType) {
            boolean spin = bowlType != null && MatchFormatRules.SPIN_TYPES.contains(bowlType);
            Map<String, Double> weights = catchWeights(spin);
            Player picked = weightedPick(rng, weights);
            if (picked != null) return picked;
            List<Player> all = new ArrayList<>(byPosition.values());
            if (all.isEmpty()) return keeper;
            return all.get(rng.nextInt(all.size()));
        }

        public Player pickRunOutFielder(Random rng) {
            Map<String, Double> weights = new LinkedHashMap<>();
            for (String pos : byPosition.keySet()) {
                double w = BOUNDARY.contains(pos) ? 1.4 : (CATCHING.contains(pos) ? 0.6 : 1.0);
                Player p = byPosition.get(pos);
                if (p != null) w *= (0.5 + p.getFldRating() / 100.0);
                weights.put(pos, w);
            }
            Player picked = weightedPick(rng, weights);
            if (picked != null) return picked;
            List<Player> all = new ArrayList<>(byPosition.values());
            return all.isEmpty() ? keeper : all.get(rng.nextInt(all.size()));
        }

        private Map<String, Double> catchWeights(boolean spin) {
            Map<String, Double> w = new LinkedHashMap<>();
            for (String pos : byPosition.keySet()) w.put(pos, 0.08);
            if (spin) {
                bump(w, "short leg", 0.22);
                bump(w, "mid-wicket", 0.18);
                bump(w, "cover", 0.16);
                bump(w, "long-on", 0.16);
                bump(w, "deep mid-wicket", 0.14);
                bump(w, "slip", 0.10);
                bump(w, "point", 0.10);
            } else {
                bump(w, "slip", 0.24);
                bump(w, "gully", 0.12);
                bump(w, "point", 0.16);
                bump(w, "cover", 0.14);
                bump(w, "mid-off", 0.10);
                bump(w, "third man", 0.08);
                bump(w, "long-off", 0.10);
                bump(w, "deep cover", 0.10);
            }
            if ("DEATH".equals(situation) || "SPREAD".equals(situation)) {
                bump(w, "long-off", 0.16);
                bump(w, "long-on", 0.14);
                bump(w, "deep mid-wicket", 0.14);
                bump(w, "deep cover", 0.12);
                bump(w, "extra cover", 0.10);
            }
            w.keySet().removeIf(k -> !byPosition.containsKey(k));
            return w;
        }

        private Player weightedPick(Random rng, Map<String, Double> weights) {
            double total = 0;
            for (Map.Entry<String, Double> e : weights.entrySet()) {
                if (byPosition.containsKey(e.getKey())) total += e.getValue();
            }
            if (total <= 0) return null;
            double roll = rng.nextDouble() * total;
            double cum = 0;
            for (Map.Entry<String, Double> e : weights.entrySet()) {
                if (!byPosition.containsKey(e.getKey())) continue;
                cum += e.getValue();
                if (roll < cum) return byPosition.get(e.getKey());
            }
            return byPosition.values().stream().findFirst().orElse(null);
        }

        private static void bump(Map<String, Double> w, String pos, double v) {
            w.merge(pos, v, Double::sum);
        }
    }

    public static FieldSet place(SimContext ctx, Player bowler, int overNumber, int totalRuns, int totalWickets) {
        Player keeper = ctx.bowlingLineup != null ? ctx.bowlingLineup.getKeeper() : null;
        List<Player> pool = buildPool(ctx, bowler, keeper);
        boolean pace = bowler != null && MatchFormatRules.PACE_TYPES.contains(bowler.getBowlType());
        String situation = detectSituation(ctx, overNumber, totalRuns, totalWickets, pace);
        List<String> template = templateFor(situation, pace);
        Map<String, Player> assigned = assign(pool, template);
        double[] mods = modsFor(situation, assigned);
        return new FieldSet(assigned, keeper, bowler, situation, mods[0], mods[1], mods[2]);
    }

    static String detectSituation(SimContext ctx, int overNumber, int totalRuns, int totalWickets, boolean pace) {
        boolean inMandatoryPP = ("T20".equalsIgnoreCase(ctx.format) && overNumber <= 6)
                || ("ODI".equalsIgnoreCase(ctx.format) && overNumber <= 10);
        if (inMandatoryPP) return "POWERPLAY";

        boolean isDeath = ("T20".equalsIgnoreCase(ctx.format) && overNumber > 16)
                || ("ODI".equalsIgnoreCase(ctx.format) && overNumber > 43);
        if (isDeath) return "DEATH";

        int wicketsLeft = 10 - totalWickets;
        if (wicketsLeft <= 3) return "TAIL";

        if ("FC".equalsIgnoreCase(ctx.format) && pace) {
            int ballAge = ((overNumber - 1) % 80) + 1;
            if (ballAge <= 15) return "ATTACKING";
        }

        if (ctx.isChasing) {
            int runsNeeded = Math.max(0, ctx.target - totalRuns);
            int ballsLeft = Math.max(1, (ctx.maxOvers - overNumber) * 6);
            double rrr = runsNeeded * 6.0 / ballsLeft;
            double par = PitchWeatherCalculator.getPitchAwareParRate(ctx.format, ctx.pitchType);
            if (rrr > par * 1.3) return "ATTACKING";
            if (rrr < par * 0.7) return "SPREAD";
        }

        return "MIDDLE";
    }

    static List<String> templateFor(String situation, boolean pace) {
        return switch (situation) {
            case "POWERPLAY" -> pace
                    ? List.of("slip", "point", "cover", "mid-off", "mid-on", "square leg", "mid-wicket", "third man", "fine leg")
                    : List.of("slip", "short leg", "point", "cover", "mid-off", "mid-on", "square leg", "mid-wicket", "fine leg");
            case "ATTACKING" -> pace
                    ? List.of("slip", "gully", "point", "cover", "mid-off", "mid-on", "square leg", "third man", "fine leg")
                    : List.of("slip", "short leg", "point", "cover", "mid-off", "mid-on", "mid-wicket", "square leg", "fine leg");
            case "TAIL" -> pace
                    ? List.of("slip", "gully", "short leg", "point", "cover", "mid-off", "mid-on", "fine leg", "third man")
                    : List.of("slip", "short leg", "point", "cover", "mid-off", "mid-on", "square leg", "mid-wicket", "fine leg");
            case "DEATH" -> List.of("long-off", "long-on", "deep mid-wicket", "deep square", "deep cover",
                    "third man", "fine leg", "extra cover", "mid-wicket");
            case "SPREAD" -> List.of("point", "cover", "mid-wicket", "mid-on", "third man", "fine leg",
                    "long-off", "deep cover", "deep square");
            default -> pace
                    ? List.of("point", "cover", "extra cover", "mid-off", "mid-on", "mid-wicket", "square leg", "third man", "fine leg")
                    : List.of("point", "cover", "extra cover", "mid-off", "mid-on", "mid-wicket", "square leg", "short leg", "fine leg");
        };
    }

    private static List<Player> buildPool(SimContext ctx, Player bowler, Player keeper) {
        List<Player> pool = new ArrayList<>();
        if (ctx.bowlingLineup == null || ctx.bowlingLineup.getPlayers() == null) return pool;
        UUID bowlerId = bowler != null ? bowler.getId() : null;
        UUID keeperId = keeper != null ? keeper.getId() : null;
        for (LineupPlayer lp : ctx.bowlingLineup.getPlayers()) {
            Player p = lp.getPlayer();
            if (p == null) continue;
            if (bowlerId != null && p.getId().equals(bowlerId)) continue;
            if (keeperId != null && p.getId().equals(keeperId)) continue;
            pool.add(p);
        }
        pool.sort((a, b) -> Double.compare(b.getFldRating(), a.getFldRating()));
        return pool;
    }

    private static Map<String, Player> assign(List<Player> pool, List<String> template) {
        List<String> ordered = new ArrayList<>(template);
        ordered.sort(Comparator.comparingInt(pos -> {
            int i = CATCH_ORDER.indexOf(pos);
            return i >= 0 ? i : 100;
        }));
        Map<String, Player> assigned = new LinkedHashMap<>();
        int i = 0;
        for (String pos : ordered) {
            if (i >= pool.size()) break;
            assigned.put(pos, pool.get(i++));
        }
        for (String pos : template) {
            if (assigned.containsKey(pos)) continue;
            if (i >= pool.size()) break;
            assigned.put(pos, pool.get(i++));
        }
        return assigned;
    }

    private static double[] modsFor(String situation, Map<String, Player> assigned) {
        double p4 = 0, p6 = 0, pWicket = 0;
        switch (situation) {
            case "POWERPLAY" -> { }
            case "DEATH" -> { p4 = 0.015; p6 = 0.010; pWicket = -0.008; }
            case "TAIL" -> { p4 = -0.010; p6 = -0.008; pWicket = 0.020; }
            case "ATTACKING" -> { p4 = 0.005; p6 = 0.005; pWicket = 0.010; }
            case "SPREAD" -> { p4 = 0.010; p6 = 0.000; pWicket = -0.005; }
            default -> { p4 = -0.005; p6 = -0.003; pWicket = 0.008; }
        }
        double catchAvg = avgFld(assigned, CATCHING);
        double boundAvg = avgFld(assigned, BOUNDARY);
        double catchDelta = (catchAvg - 50.0) / 100.0;
        double boundDelta = (boundAvg - 50.0) / 100.0;
        p4 -= boundDelta * 0.012;
        p6 -= boundDelta * 0.006;
        pWicket += catchDelta * 0.015;
        return new double[]{p4, p6, pWicket};
    }

    private static double avgFld(Map<String, Player> assigned, Set<String> group) {
        double sum = 0;
        int n = 0;
        for (Map.Entry<String, Player> e : assigned.entrySet()) {
            if (!group.contains(e.getKey()) || e.getValue() == null) continue;
            sum += e.getValue().getFldRating();
            n++;
        }
        return n > 0 ? sum / n : 50.0;
    }
}
