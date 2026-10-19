package com.cricketplex.util;

import com.cricketplex.entity.MatchResult;

import java.util.List;
import java.util.UUID;

/**
 * Team morale is derived from three independent, non-negative categories.
 * It is never a running total, so consecutive losses cannot drive it to 0.
 *
 * Weights: form 50%, squad (team) 30%, academy 20%.
 * Academy level 1 contributes 25, so overall morale cannot be 0.
 */
public final class MoraleCalculator {

    public static final int RECENT_LIMIT = 10;
    public static final double FORM_WEIGHT = 0.50;
    public static final double SQUAD_WEIGHT = 0.30;
    public static final double ACADEMY_WEIGHT = 0.20;

    private MoraleCalculator() {}

    public record Breakdown(
            int formFactor,
            int squadFactor,
            int academyFactor,
            int morale,
            int wins,
            int draws,
            int losses,
            int matchCount
    ) {}

    public static int squadFactor(double avgConfidence) {
        return clamp((int) Math.round(avgConfidence));
    }

    public static int academyFactor(Integer academyLevel) {
        int level = academyLevel != null ? academyLevel : 1;
        return clamp(Math.max(1, level) * 25);
    }

    public static int formFactor(int wins, int draws, int losses) {
        int n = wins + draws + losses;
        if (n <= 0) return 50;
        return clamp((int) Math.round((wins * 100.0 + draws * 50.0) / n));
    }

    public static int overall(int form, int squad, int academy) {
        int morale = (int) Math.round(
                clamp(form) * FORM_WEIGHT
                        + clamp(squad) * SQUAD_WEIGHT
                        + clamp(academy) * ACADEMY_WEIGHT);
        return Math.max(1, Math.min(100, morale));
    }

    /**
     * Step stored morale toward the derived target. Never more than 3 in one match.
     * High morale drops faster and rises slower; low morale drops slower and rises faster.
     */
    public static int stepToward(Integer stored, int target) {
        int current = stored != null ? stored : 50;
        current = Math.max(1, Math.min(100, current));
        target = Math.max(1, Math.min(100, target));
        if (current == target) return current;
        int step = current >= 75 ? 3 : current >= 50 ? 2 : 1;
        if (target < current) {
            return Math.max(target, current - step);
        }
        step = current < 50 ? 3 : current < 75 ? 2 : 1;
        return Math.min(target, current + step);
    }

    public static Breakdown compute(List<MatchResult> recent, UUID teamId,
                                   double avgConfidence, Integer academyLevel) {
        int wins = 0, draws = 0, losses = 0, count = 0;
        if (recent != null && teamId != null) {
            for (MatchResult mr : recent) {
                if (count >= RECENT_LIMIT) break;
                if (mr == null) continue;
                String rt = mr.getResultType();
                if ("PENDING".equals(rt)) continue;
                if ("TIE".equals(rt) || "DRAW".equals(rt) || "NO_RESULT".equals(rt)) {
                    draws++;
                } else if (mr.getWinner() != null && teamId.equals(mr.getWinner().getId())) {
                    wins++;
                } else {
                    losses++;
                }
                count++;
            }
        }
        int form = formFactor(wins, draws, losses);
        int squad = squadFactor(avgConfidence);
        int academy = academyFactor(academyLevel);
        return new Breakdown(form, squad, academy, overall(form, squad, academy),
                wins, draws, losses, count);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
