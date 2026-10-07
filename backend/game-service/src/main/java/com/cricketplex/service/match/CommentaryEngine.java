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

    public String getBoundaryCommentary(Random rng, SimContext ctx, int overNumber, 
                                         BatsmanState batter, Player bowler, int totalRuns, int totalWickets) {
        // Try user-submitted commentary first
        try {
            String phase = determinePhase(ctx.format, overNumber, ctx.maxOvers);
            String bowlerType = bowler.getBowlType();
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("batsman", batter.player.getFirstName() + " " + batter.player.getLastName());
            placeholders.put("bowler", bowler.getFirstName() + " " + bowler.getLastName());
            placeholders.put("runs", "4");
            placeholders.put("score", String.valueOf(totalRuns));
            placeholders.put("wickets", String.valueOf(totalWickets));
            placeholders.put("overs", String.format("%.1f", overNumber + 0.0));
            
            String userCommentary = getUserCommentary(rng, ctx.format, phase, bowlerType, "4", placeholders);
            if (userCommentary != null) {
                return userCommentary;
            }
        } catch (Exception e) {
            // Silently fallback
        }
        
        // Fallback to hardcoded
        String[] options = {"Driven through the covers", "Cut past point", "Flicked off the pads",
                "Edged past the keeper", "Square driven beautifully", "Pulled to the boundary",
                "Swept fine", "Punched through mid-off", "Driven down the ground",
                "Clipped off the hips to the fence", "Driven through extra cover",
                "Glanced fine for four"};
        return options[rng.nextInt(options.length)];
    }

    public String getSixCommentary(Random rng, SimContext ctx, int overNumber, 
                                    BatsmanState batter, Player bowler, int totalRuns, int totalWickets) {
        // Try user-submitted commentary first
        try {
            String phase = determinePhase(ctx.format, overNumber, ctx.maxOvers);
            String bowlerType = bowler.getBowlType();
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("batsman", batter.player.getFirstName() + " " + batter.player.getLastName());
            placeholders.put("bowler", bowler.getFirstName() + " " + bowler.getLastName());
            placeholders.put("runs", "6");
            placeholders.put("score", String.valueOf(totalRuns));
            placeholders.put("wickets", String.valueOf(totalWickets));
            placeholders.put("overs", String.format("%.1f", overNumber + 0.0));
            
            String userCommentary = getUserCommentary(rng, ctx.format, phase, bowlerType, "6", placeholders);
            if (userCommentary != null) {
                return userCommentary;
            }
        } catch (Exception e) {
            // Silently fallback
        }
        
        // Fallback to hardcoded
        String[] options = {"Launched over long-on", "Smashed over midwicket", "Scooped over fine leg",
                "Lofted straight down the ground", "Hammered over extra cover", "Heaved over the leg side",
                "Deposited into the stands", "Reverse swept for six", "Stepped out and cleared long-off",
                "Massive hit into the crowd", "Swings hard and sends it into the second tier",
                "Inside-out six over cover point"};
        return options[rng.nextInt(options.length)];
    }

    public String getDotBallCommentary(Random rng, SimContext ctx, int overNumber, 
                                        BatsmanState batter, Player bowler, int totalRuns, int totalWickets) {
        try {
            String phase = determinePhase(ctx.format, overNumber, ctx.maxOvers);
            String bowlerType = bowler.getBowlType();
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("batsman", batter.player.getFirstName() + " " + batter.player.getLastName());
            placeholders.put("bowler", bowler.getFirstName() + " " + bowler.getLastName());
            placeholders.put("runs", "0");
            placeholders.put("score", String.valueOf(totalRuns));
            placeholders.put("wickets", String.valueOf(totalWickets));
            placeholders.put("overs", String.format("%.1f", overNumber + 0.0));
            
            String userCommentary = getUserCommentary(rng, ctx.format, phase, bowlerType, "0", placeholders);
            if (userCommentary != null) {
                return userCommentary;
            }
        } catch (Exception e) {
            // Silently fallback
        }
        
        return "Dot ball";
    }

    public String getSingleCommentary(Random rng, SimContext ctx, int overNumber, 
                                       BatsmanState batter, Player bowler, int totalRuns, int totalWickets) {
        try {
            String phase = determinePhase(ctx.format, overNumber, ctx.maxOvers);
            String bowlerType = bowler.getBowlType();
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("batsman", batter.player.getFirstName() + " " + batter.player.getLastName());
            placeholders.put("bowler", bowler.getFirstName() + " " + bowler.getLastName());
            placeholders.put("runs", "1");
            placeholders.put("score", String.valueOf(totalRuns));
            placeholders.put("wickets", String.valueOf(totalWickets));
            placeholders.put("overs", String.format("%.1f", overNumber + 0.0));
            
            String userCommentary = getUserCommentary(rng, ctx.format, phase, bowlerType, "1", placeholders);
            if (userCommentary != null) {
                return userCommentary;
            }
        } catch (Exception e) {
            // Silently fallback
        }
        
        return "Single taken";
    }

    public String getTwoRunsCommentary(Random rng, SimContext ctx, int overNumber, 
                                        BatsmanState batter, Player bowler, int totalRuns, int totalWickets) {
        try {
            String phase = determinePhase(ctx.format, overNumber, ctx.maxOvers);
            String bowlerType = bowler.getBowlType();
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("batsman", batter.player.getFirstName() + " " + batter.player.getLastName());
            placeholders.put("bowler", bowler.getFirstName() + " " + bowler.getLastName());
            placeholders.put("runs", "2");
            placeholders.put("score", String.valueOf(totalRuns));
            placeholders.put("wickets", String.valueOf(totalWickets));
            placeholders.put("overs", String.format("%.1f", overNumber + 0.0));
            
            String userCommentary = getUserCommentary(rng, ctx.format, phase, bowlerType, "2", placeholders);
            if (userCommentary != null) {
                return userCommentary;
            }
        } catch (Exception e) {
            // Silently fallback
        }
        
        return "Pushed for two";
    }

    public String getThreeRunsCommentary(Random rng, SimContext ctx, int overNumber, 
                                          BatsmanState batter, Player bowler, int totalRuns, int totalWickets) {
        try {
            String phase = determinePhase(ctx.format, overNumber, ctx.maxOvers);
            String bowlerType = bowler.getBowlType();
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("batsman", batter.player.getFirstName() + " " + batter.player.getLastName());
            placeholders.put("bowler", bowler.getFirstName() + " " + bowler.getLastName());
            placeholders.put("runs", "3");
            placeholders.put("score", String.valueOf(totalRuns));
            placeholders.put("wickets", String.valueOf(totalWickets));
            placeholders.put("overs", String.format("%.1f", overNumber + 0.0));
            
            String userCommentary = getUserCommentary(rng, ctx.format, phase, bowlerType, "3", placeholders);
            if (userCommentary != null) {
                return userCommentary;
            }
        } catch (Exception e) {
            // Silently fallback
        }
        
        return "Three runs taken";
    }

    public String getWicketCommentary(Random rng, SimContext ctx, int overNumber,
                                       BatsmanState batter, BatsmanState nonStriker, Player bowler, DismissalInfo dismissal,
                                       boolean isNonStrikerOut,
                                       int runsOnBall, int totalRuns, int totalWickets) {
        try {
            String phase = determinePhase(ctx.format, overNumber, ctx.maxOvers);
            String bowlerType = bowler.getBowlType();
            
            Map<String, String> placeholders = new HashMap<>();
            BatsmanState dismissedBatter = isNonStrikerOut ? nonStriker : batter;
            placeholders.put("batsman", dismissedBatter.player.getFirstName() + " " + dismissedBatter.player.getLastName());
            placeholders.put("non_striker", nonStriker.player.getFirstName() + " " + nonStriker.player.getLastName());
            placeholders.put("bowler", bowler.getFirstName() + " " + bowler.getLastName());
            if (dismissal.fielder != null) {
                placeholders.put("fielder", dismissal.fielder.getFirstName() + " " + dismissal.fielder.getLastName());
            }
            placeholders.put("score", String.valueOf(totalRuns));
            placeholders.put("wickets", String.valueOf(totalWickets));
            placeholders.put("overs", String.format("%.1f", overNumber + 0.0));
            placeholders.put("runs", String.valueOf(runsOnBall));
            
            String eventType = dismissal.type;
            if ("RUN_OUT".equals(dismissal.type)) {
                eventType = "RUN_OUT_" + runsOnBall;
            }

            String wicketSituation = null;
            if ("RUN_OUT".equals(dismissal.type)) {
                wicketSituation = isNonStrikerOut ? "run_out_non_striker" : "run_out_striker";
            }

            String userCommentary = getUserCommentary(
                    rng,
                    ctx.format,
                    phase,
                    bowlerType,
                    eventType,
                    wicketSituation,
                    null,
                    null,
                    placeholders
            );
            if (userCommentary == null && "RUN_OUT".equals(dismissal.type)) {
                // Backward compatibility for existing run-out commentary entries.
                userCommentary = getUserCommentary(
                        rng,
                        ctx.format,
                        phase,
                        bowlerType,
                        "RUN_OUT",
                        wicketSituation,
                        null,
                        null,
                        placeholders
                );
            }
            if (userCommentary != null) {
                return userCommentary;
            }
        } catch (Exception e) {
            // Silently fallback
        }
        
        // Fallback to hardcoded
        return dismissal.commentary;
    }

    public String getExtraCommentary(Random rng, SimContext ctx, int overNumber, 
                                      BatsmanState batter, Player bowler, String extraType, int runs,
                                      int totalRuns, int totalWickets) {
        try {
            String phase = determinePhase(ctx.format, overNumber, ctx.maxOvers);
            String bowlerType = bowler.getBowlType();
            
            // Event type for extras: "1WD", "2NB", "3LB", etc.
            String eventType = runs + extraType;
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("batsman", batter.player.getFirstName() + " " + batter.player.getLastName());
            placeholders.put("bowler", bowler.getFirstName() + " " + bowler.getLastName());
            placeholders.put("runs", String.valueOf(runs));
            placeholders.put("score", String.valueOf(totalRuns));
            placeholders.put("wickets", String.valueOf(totalWickets));
            placeholders.put("overs", String.format("%.1f", overNumber + 0.0));
            
            String userCommentary = getUserCommentary(rng, ctx.format, phase, bowlerType, eventType, placeholders);
            if (userCommentary != null) {
                return userCommentary;
            }
        } catch (Exception e) {
            // Silently fallback
        }
        
        // Fallback to hardcoded (return null to use existing logic)
        return null;
    }

    /**
     * Get user-submitted commentary for a specific context
     * Returns null if no matching commentary found (fallback to hardcoded)
     */
    public String getUserCommentary(
            Random rng,
            String matchFormat,
            String phase,
            String bowlerType,
            String eventType,
            Map<String, String> placeholderValues
        ) {
        return getUserCommentary(
            rng,
            matchFormat,
            phase,
            bowlerType,
            eventType,
            null,
            null,
            null,
            placeholderValues
        );
        }

        public String getUserCommentary(
            Random rng,
            String matchFormat,
            String phase,
            String bowlerType,
            String eventType,
            String wicketSituation,
            String batsmanState,
            String matchPressure,
            Map<String, String> placeholderValues
    ) {
        try {
            // Query for matching commentary with optional context filters.
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
                return null; // No user commentary available
            }

            // Pick random from matches
            CommentarySubmission selected = matches.get(rng.nextInt(matches.size()));

            // Replace placeholders
            String commentary = selected.getCommentaryText();
            for (Map.Entry<String, String> entry : placeholderValues.entrySet()) {
                String placeholder = "[" + entry.getKey() + "]";
                String value = entry.getValue() != null ? entry.getValue() : "";
                commentary = commentary.replace(placeholder, value);
            }

            // Increment usage counter asynchronously (don't block simulation)
            try {
                commentaryService.incrementUsage(selected.getId());
            } catch (Exception ignored) {
                // Silently fail - usage counter is not critical
            }

            return commentary;
        } catch (Exception e) {
            // Silently fallback to hardcoded commentary on any error
            log.warn("Failed to fetch user commentary: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Determine phase based on match format and over number
     */
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
            // TEST/FC
            return "middle"; // Could be expanded to session-based phases
        }
    }

    /**
     * Create placeholder map for commentary replacement
     */
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
