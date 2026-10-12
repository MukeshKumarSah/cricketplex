package com.cricketplex.service.match;

import com.cricketplex.entity.BallEvent;
import com.cricketplex.entity.CommentarySubmission;
import com.cricketplex.entity.Innings;
import com.cricketplex.entity.Player;
import com.cricketplex.util.PlaceholderValidator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class BallCommentaryResolver {

    public String resolve(BallEvent be, Innings inn, int scoreBefore, int wicketsBefore) {
        String stored = be.getCommentary() != null ? be.getCommentary() : "";
        CommentarySubmission sub = be.getCommentarySubmission();
        if (sub == null || sub.getCommentaryText() == null || sub.getCommentaryText().isBlank()) {
            return stored;
        }

        Map<String, String> ph = new HashMap<>();
        if (be.getBatsman() != null) {
            ph.put("batsman", playerName(be.getBatsman()));
        }
        if (be.getNonStriker() != null) {
            ph.put("non_striker", playerName(be.getNonStriker()));
        }
        if (be.getBowler() != null) {
            ph.put("bowler", playerName(be.getBowler()));
        }
        if (be.getFielder() != null) {
            String fielderName = playerName(be.getFielder());
            ph.put("fielder", fielderName);
            if ("STUMPED".equals(be.getDismissalType()) || "CAUGHT_BEHIND".equals(be.getDismissalType())) {
                ph.put("keeper", fielderName);
            }
        }
        if (inn != null && inn.getBattingTeam() != null) {
            ph.put("batting_team", inn.getBattingTeam().getTeamName());
        }
        if (inn != null && inn.getBowlingTeam() != null) {
            ph.put("bowling_team", inn.getBowlingTeam().getTeamName());
        }
        ph.put("runs", String.valueOf(be.getRuns() != null ? be.getRuns() : 0));
        ph.put("score", String.valueOf(scoreBefore));
        ph.put("wickets", String.valueOf(wicketsBefore));
        int overNo = be.getOverNumber() != null ? be.getOverNumber() : 0;
        int ballNo = be.getBallNumber() != null ? be.getBallNumber() : 0;
        ph.put("overs", (overNo > 0 ? overNo - 1 : 0) + "." + ballNo);

        return stored + PlaceholderValidator.replacePlaceholders(sub.getCommentaryText(), ph);
    }

    private static String playerName(Player p) {
        String first = p.getFirstName() != null ? p.getFirstName() : "";
        String last = p.getLastName() != null ? p.getLastName() : "";
        return (first + " " + last).trim();
    }
}
