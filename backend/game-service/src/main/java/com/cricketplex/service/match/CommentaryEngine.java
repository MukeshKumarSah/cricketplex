package com.cricketplex.service.match;

import com.cricketplex.entity.CommentarySubmission;
import com.cricketplex.entity.Player;
import com.cricketplex.entity.Team;
import com.cricketplex.service.CommentaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentaryEngine {
    private final CommentaryService commentaryService;

    public CommentaryPick getBoundaryCommentary(Random rng, SimContext ctx, int overNumber,
                                                BatsmanState batter, Player bowler, int totalRuns, int totalWickets) {
        CommentaryPick user = pickUserCommentary(rng, ctx.format, determinePhase(ctx.format, overNumber, ctx.maxOvers),
                bowler.getBowlType(), "4", null, null, null);
        if (user != null) return user;

        String[] options = {"Driven through the covers", "Cut past point", "Flicked off the pads",
                "Edged past the keeper", "Square driven beautifully", "Pulled to the boundary",
                "Swept fine", "Punched through mid-off", "Driven down the ground",
                "Clipped off the hips to the fence", "Driven through extra cover",
                "Glanced fine for four"};
        return CommentaryPick.fallback(options[rng.nextInt(options.length)]);
    }

    public CommentaryPick getSixCommentary(Random rng, SimContext ctx, int overNumber,
                                           BatsmanState batter, Player bowler, int totalRuns, int totalWickets) {
        CommentaryPick user = pickUserCommentary(rng, ctx.format, determinePhase(ctx.format, overNumber, ctx.maxOvers),
                bowler.getBowlType(), "6", null, null, null);
        if (user != null) return user;

        String[] options = {"Launched over long-on", "Smashed over midwicket", "Scooped over fine leg",
                "Lofted straight down the ground", "Hammered over extra cover", "Heaved over the leg side",
                "Deposited into the stands", "Reverse swept for six", "Stepped out and cleared long-off",
                "Massive hit into the crowd", "Swings hard and sends it into the second tier",
                "Inside-out six over cover point"};
        return CommentaryPick.fallback(options[rng.nextInt(options.length)]);
    }

    public CommentaryPick getDotBallCommentary(Random rng, SimContext ctx, int overNumber,
                                               BatsmanState batter, Player bowler, int totalRuns, int totalWickets) {
        CommentaryPick user = pickUserCommentary(rng, ctx.format, determinePhase(ctx.format, overNumber, ctx.maxOvers),
                bowler.getBowlType(), "0", null, null, null);
        if (user != null) return user;
        return CommentaryPick.fallback("Dot ball");
    }

    public CommentaryPick getSingleCommentary(Random rng, SimContext ctx, int overNumber,
                                              BatsmanState batter, Player bowler, int totalRuns, int totalWickets) {
        CommentaryPick user = pickUserCommentary(rng, ctx.format, determinePhase(ctx.format, overNumber, ctx.maxOvers),
                bowler.getBowlType(), "1", null, null, null);
        if (user != null) return user;
        return CommentaryPick.fallback("Single taken");
    }

    public CommentaryPick getTwoRunsCommentary(Random rng, SimContext ctx, int overNumber,
                                               BatsmanState batter, Player bowler, int totalRuns, int totalWickets) {
        CommentaryPick user = pickUserCommentary(rng, ctx.format, determinePhase(ctx.format, overNumber, ctx.maxOvers),
                bowler.getBowlType(), "2", null, null, null);
        if (user != null) return user;
        return CommentaryPick.fallback("Pushed for two");
    }

    public CommentaryPick getThreeRunsCommentary(Random rng, SimContext ctx, int overNumber,
                                                 BatsmanState batter, Player bowler, int totalRuns, int totalWickets) {
        CommentaryPick user = pickUserCommentary(rng, ctx.format, determinePhase(ctx.format, overNumber, ctx.maxOvers),
                bowler.getBowlType(), "3", null, null, null);
        if (user != null) return user;
        return CommentaryPick.fallback("Three runs taken");
    }

    public CommentaryPick getWicketCommentary(Random rng, SimContext ctx, int overNumber,
                                              BatsmanState batter, BatsmanState nonStriker, Player bowler, DismissalInfo dismissal,
                                              boolean isNonStrikerOut,
                                              int runsOnBall, int totalRuns, int totalWickets) {
        String eventType = dismissal.type;
        if ("RUN_OUT".equals(dismissal.type)) {
            eventType = "RUN_OUT_" + runsOnBall;
        }

        String wicketSituation = null;
        if ("RUN_OUT".equals(dismissal.type)) {
            wicketSituation = isNonStrikerOut ? "run_out_non_striker" : "run_out_striker";
        }

        CommentaryPick user = pickUserCommentary(
                rng, ctx.format, determinePhase(ctx.format, overNumber, ctx.maxOvers),
                bowler.getBowlType(), eventType, wicketSituation, null, null);
        if (user == null && "RUN_OUT".equals(dismissal.type)) {
            user = pickUserCommentary(
                    rng, ctx.format, determinePhase(ctx.format, overNumber, ctx.maxOvers),
                    bowler.getBowlType(), "RUN_OUT", wicketSituation, null, null);
        }
        if (user != null) return user;
        return CommentaryPick.fallback(dismissal.commentary);
    }

    public CommentaryPick getExtraCommentary(Random rng, SimContext ctx, int overNumber,
                                             BatsmanState batter, Player bowler, String extraType, int runs,
                                             int totalRuns, int totalWickets) {
        String eventType = runs + extraType;
        return pickUserCommentary(rng, ctx.format, determinePhase(ctx.format, overNumber, ctx.maxOvers),
                bowler.getBowlType(), eventType, null, null, null);
    }

    public CommentaryPick pickUserCommentary(
            Random rng,
            String matchFormat,
            String phase,
            String bowlerType,
            String eventType,
            String wicketSituation,
            String batsmanState,
            String matchPressure
    ) {
        try {
            List<CommentarySubmission> matches = commentaryService.getMatchingCommentary(
                    matchFormat,
                    phase,
                    bowlerType,
                    eventType,
                    wicketSituation,
                    batsmanState,
                    matchPressure
            );

            if (matches.isEmpty()) {
                return null;
            }

            CommentarySubmission selected = matches.get(rng.nextInt(matches.size()));

            try {
                commentaryService.incrementUsage(selected.getId());
            } catch (Exception ignored) {
            }

            return CommentaryPick.template(selected);
        } catch (Exception e) {
            log.warn("Failed to fetch user commentary: {}", e.getMessage());
            return null;
        }
    }

    public String determinePhase(String format, int overNumber, int maxOvers) {
        if ("T20".equals(format)) {
            if (overNumber <= 6) return "powerplay";
            if (overNumber >= 16) return "death";
            return "middle";
        } else if ("ODI".equals(format)) {
            if (overNumber <= 10) return "powerplay";
            if (overNumber >= 41) return "death";
            return "middle";
        } else {
            return "middle";
        }
    }

    public Map<String, String> createPlaceholderMap(
            BatsmanState batter,
            Player bowler,
            Player fielder,
            Team battingTeam,
            Team bowlingTeam,
            int runs,
            int score,
            int wickets,
            double overs
    ) {
        Map<String, String> map = new HashMap<>();

        if (batter != null && batter.player != null) {
            map.put("batsman", batter.player.getFirstName() + " " + batter.player.getLastName());
        }
        if (bowler != null) {
            map.put("bowler", bowler.getFirstName() + " " + bowler.getLastName());
        }
        if (fielder != null) {
            map.put("fielder", fielder.getFirstName() + " " + fielder.getLastName());
        }
        if (battingTeam != null) {
            map.put("batting_team", battingTeam.getTeamName());
        }
        if (bowlingTeam != null) {
            map.put("bowling_team", bowlingTeam.getTeamName());
        }

        map.put("runs", String.valueOf(runs));
        map.put("score", String.valueOf(score));
        map.put("wickets", String.valueOf(wickets));
        map.put("overs", String.format("%.1f", overs));

        return map;
    }

}
