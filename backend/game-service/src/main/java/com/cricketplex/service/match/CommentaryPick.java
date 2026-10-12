package com.cricketplex.service.match;

import com.cricketplex.entity.CommentarySubmission;

public class CommentaryPick {
    public final CommentarySubmission submission;
    public final String fallbackText;

    private CommentaryPick(CommentarySubmission submission, String fallbackText) {
        this.submission = submission;
        this.fallbackText = fallbackText;
    }

    public static CommentaryPick template(CommentarySubmission submission) {
        return new CommentaryPick(submission, null);
    }

    public static CommentaryPick fallback(String text) {
        return new CommentaryPick(null, text != null ? text : "");
    }

    public boolean hasTemplate() {
        return submission != null;
    }
}
