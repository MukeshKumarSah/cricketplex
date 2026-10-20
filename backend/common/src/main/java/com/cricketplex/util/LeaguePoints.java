package com.cricketplex.util;

import com.cricketplex.entity.MatchResult;

import java.util.UUID;

/**
 * League table points. T20/ODI: win 2, tie 1, loss 0.
 * FC: win 3, tie 2, draw 1, loss 0.
 */
public final class LeaguePoints {

    private LeaguePoints() {}

    public static boolean isFcFormat(String format) {
        return format != null && "FC".equalsIgnoreCase(format);
    }

    public static int win(boolean isFC) {
        return isFC ? 3 : 2;
    }

    public static int tie(boolean isFC) {
        return isFC ? 2 : 1;
    }

    public static int draw(boolean isFC) {
        return isFC ? 1 : 0;
    }

    /**
     * Apply a completed match to standings rows.
     * Arrays are [played, won, lost, tied, points, drawn].
     */
    public static void applyTable(MatchResult mr, int[] teamA, int[] teamB,
                                  UUID teamAId, UUID teamBId, boolean isFC) {
        if (mr == null || teamA == null || teamB == null) return;
        String rt = mr.getResultType();
        if ("TIE".equals(rt)) {
            teamA[3]++;
            teamB[3]++;
            teamA[4] += tie(isFC);
            teamB[4] += tie(isFC);
        } else if ("DRAW".equals(rt) || "NO_RESULT".equals(rt)) {
            if (isFC && "DRAW".equals(rt)) {
                teamA[5]++;
                teamB[5]++;
                teamA[4] += draw(true);
                teamB[4] += draw(true);
            }
        } else if (mr.getWinner() != null) {
            UUID winnerId = mr.getWinner().getId();
            if (winnerId.equals(teamAId)) {
                teamA[1]++;
                teamB[2]++;
                teamA[4] += win(isFC);
            } else if (winnerId.equals(teamBId)) {
                teamB[1]++;
                teamA[2]++;
                teamB[4] += win(isFC);
            }
        }
    }

    /**
     * Apply a completed match to ranking rows.
     * Arrays are [points, wins].
     */
    public static void applyRanking(MatchResult mr, int[] teamA, int[] teamB,
                                    UUID teamAId, UUID teamBId, boolean isFC) {
        if (mr == null || teamA == null || teamB == null) return;
        String rt = mr.getResultType();
        if ("TIE".equals(rt)) {
            teamA[0] += tie(isFC);
            teamB[0] += tie(isFC);
        } else if ("DRAW".equals(rt)) {
            teamA[0] += draw(isFC);
            teamB[0] += draw(isFC);
        } else if (mr.getWinner() != null) {
            UUID winnerId = mr.getWinner().getId();
            if (winnerId.equals(teamAId)) {
                teamA[0] += win(isFC);
                teamA[1]++;
            } else if (winnerId.equals(teamBId)) {
                teamB[0] += win(isFC);
                teamB[1]++;
            }
        }
    }
}
