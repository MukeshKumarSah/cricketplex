package com.cricketplex.util;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CommentaryOptionCatalog {

    private CommentaryOptionCatalog() {
    }

    public static final List<String> MATCH_FORMATS = List.of("T20", "ODI", "FC", "all");

    public static final List<String> PHASES = List.of("powerplay", "middle", "death", "all");

    public static final List<String> BOWLER_TYPES = List.of(
            "F", "FM", "MF", "M", "FS", "WS", "LAP", "PACE", "SPINNER", "ALL"
    );

    public static final List<String> EVENT_TYPES = List.of(
            "0", "1", "2", "3", "4", "5", "6",
            "1LB", "2LB", "3LB", "4LB",
            "1BYE", "2BYE", "3BYE", "4BYE",
            "1WD", "2WD", "3WD", "4WD", "5WD", "6WD", "7WD",
            "1NB", "2NB", "3NB", "4NB", "5NB", "6NB", "7NB",
            "BOWLED", "CAUGHT", "CAUGHT_BEHIND", "LBW", "RUN_OUT_0", "RUN_OUT_1", "RUN_OUT", "STUMPED", "HIT_WICKET", "CAUGHT_AND_BOWLED"
    );

    public static final List<String> WICKET_SITUATIONS = List.of("run_out_striker", "run_out_non_striker");

    public static final List<String> EXTRA_TAGS = List.of(
            "catch_dropped", "great_fielding", "misfield",
            "free_hit",
            "strike_farming_strong_early", "strike_farming_strong_late",
            "strike_farming_weak_early", "strike_farming_weak_late",
            "strike_farming",
            "milestone_3w_haul", "milestone_5w_haul",
            "milestone_50", "milestone_100", "milestone_150", "milestone_200",
            "partnership_50", "partnership_100", "partnership_150", "partnership_200", "partnership_250"
    );

    public static final Set<String> RUN_OUT_EVENTS = Set.of("RUN_OUT", "RUN_OUT_0", "RUN_OUT_1");

    public static Map<String, Object> asResponseMap() {
        return Map.ofEntries(
                Map.entry("matchFormats", MATCH_FORMATS),
                Map.entry("phases", PHASES),
                Map.entry("bowlerTypes", BOWLER_TYPES),
                Map.entry("eventTypes", EVENT_TYPES),
                Map.entry("wicketSituations", WICKET_SITUATIONS),
                Map.entry("extraTags", EXTRA_TAGS)
        );
    }
}