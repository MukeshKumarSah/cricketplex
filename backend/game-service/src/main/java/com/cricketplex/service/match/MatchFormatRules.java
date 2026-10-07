package com.cricketplex.service.match;

import java.util.Set;

public final class MatchFormatRules {
    public static final int ODI_OVERS        = 50;
    public static final int T20_OVERS        = 20;
    public static final int FC_SESSION_OVERS = 50;
    public static final Set<String> PACE_TYPES = Set.of("F", "FM", "MF", "M", "LAP");
    public static final Set<String> SPIN_TYPES = Set.of("FS", "WS");

    private MatchFormatRules() {}

    public static int getMaxOvers(String format) {
        if ("T20".equalsIgnoreCase(format)) return T20_OVERS;
        if ("FC".equalsIgnoreCase(format))  return FC_SESSION_OVERS;
        return ODI_OVERS;
    }

    public static int getMaxPerBowler(String format) {
        if ("T20".equalsIgnoreCase(format)) return 4;
        if ("FC".equalsIgnoreCase(format))  return 50;
        return 10;
    }
}
