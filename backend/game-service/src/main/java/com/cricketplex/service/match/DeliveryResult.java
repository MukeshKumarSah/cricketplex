package com.cricketplex.service.match;

import com.cricketplex.entity.CommentarySubmission;
import com.cricketplex.entity.Player;

public class DeliveryResult {
    public int runs = 0;
    public boolean isWicket, isNonStrikerOut, isBoundary, isSix, isWide, isNoBall, isBye, isLegBye;
    public String dismissalType = null;
    public Player fielder       = null;
    public CommentarySubmission commentarySubmission = null;
    public String commentary    = "";

    public void apply(CommentaryPick pick) {
        apply(pick, "");
    }

    public void apply(CommentaryPick pick, String prefix) {
        if (pick != null && pick.hasTemplate()) {
            this.commentarySubmission = pick.submission;
            this.commentary += prefix;
        } else {
            this.commentary += prefix;
            if (pick != null && pick.fallbackText != null) {
                this.commentary += pick.fallbackText;
            }
        }
    }

    public void setFallback(String text) {
        this.commentarySubmission = null;
        this.commentary = text != null ? text : "";
    }
}
