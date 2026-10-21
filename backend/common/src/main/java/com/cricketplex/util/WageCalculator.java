package com.cricketplex.util;

import com.cricketplex.entity.Player;

public final class WageCalculator {

    private static final double POWER = 1.5;
    private static final double SCALE = 30_000.0;

    private WageCalculator() {}

    public static int compute(Player player) {
        if (player == null) return 1;
        return compute(
                player.getBatRating(),
                player.getBowlRating(),
                player.getKeeperRating(),
                player.getFldRating(),
                player.getStamina(),
                player.getExperience(),
                player.getAge());
    }

    public static int compute(double bat, double bowl, double wk, double fld,
                              double sta, int exp, int age) {
        double primary = Math.max(bat, bowl);
        double secondary = Math.min(bat, bowl);
        double total = curve(primary)
                + curve(secondary) / 4.0
                + curve(wk) / 6.0
                + curve(fld) / 20.0
                + curve(sta) / 30.0;
        double expMult = 1.0 + exp / 1000.0;
        double ageMult = 1.0 + (35.0 - age) / 170.0;
        double score = total * expMult * ageMult;
        if (!Double.isFinite(score)) return 1;
        return Math.max(1, (int) Math.round(score));
    }

    static double curve(double x) {
        return SCALE * Math.pow(Math.max(0.0, x) / 100.0, POWER);
    }
}
