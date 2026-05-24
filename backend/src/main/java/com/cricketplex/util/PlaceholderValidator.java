package com.cricketplex.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for validating and extracting placeholders from commentary text
 */
public class PlaceholderValidator {

    // Allowed placeholders (Phase 1 - minimal set)
    public static final Set<String> ALLOWED_PLACEHOLDERS = Set.of(
            // Players
            "batsman", "non_striker", "bowler", "fielder", "keeper",
            // Teams
            "batting_team", "bowling_team",
            // Stats
            "runs", "score", "wickets", "overs"
    );

    // Context-specific placeholders that require certain extra tags
    public static final Map<String, Set<String>> CONTEXT_REQUIRED_PLACEHOLDERS = Map.of(
            "fielder", Set.of("CAUGHT", "CAUGHT_AND_BOWLED", "catch_dropped", "great_fielding", "misfield", "RUN_OUT", "RUN_OUT_0", "RUN_OUT_1"),
            "keeper", Set.of("STUMPED", "CAUGHT_BEHIND") // keeper relevant for stumpings or keeper catches
    );

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\[([a-z_]+)\\]");

    /**
     * Extract all placeholders from commentary text
     */
    public static List<String> extractPlaceholders(String commentaryText) {
        List<String> placeholders = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(commentaryText);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return placeholders;
    }

    /**
     * Validate that all placeholders are allowed
     * Returns list of invalid placeholders (empty if all valid)
     */
    public static List<String> validatePlaceholders(String commentaryText) {
        List<String> invalid = new ArrayList<>();
        List<String> placeholders = extractPlaceholders(commentaryText);

        for (String placeholder : placeholders) {
            if (!ALLOWED_PLACEHOLDERS.contains(placeholder)) {
                invalid.add(placeholder);
            }
        }

        return invalid;
    }

    /**
     * Check if context-specific placeholders match the event type and extra tags
     * Returns error messages for mismatches
     */
    public static List<String> validateContext(String commentaryText, String eventType, Set<String> extraTags) {
        List<String> errors = new ArrayList<>();
        List<String> placeholders = extractPlaceholders(commentaryText);

        for (String placeholder : placeholders) {
            if (CONTEXT_REQUIRED_PLACEHOLDERS.containsKey(placeholder)) {
                Set<String> requiredContexts = CONTEXT_REQUIRED_PLACEHOLDERS.get(placeholder);
                boolean hasContext = false;

                // Check if event type matches
                if (requiredContexts.contains(eventType)) {
                    hasContext = true;
                }

                // Check if any extra tag matches
                if (extraTags != null) {
                    for (String tag : extraTags) {
                        if (requiredContexts.contains(tag)) {
                            hasContext = true;
                            break;
                        }
                    }
                }

                if (!hasContext) {
                    errors.add(String.format(
                            "Placeholder [%s] requires one of: %s (add appropriate extra tag or change event type)",
                            placeholder,
                            String.join(", ", requiredContexts)
                    ));
                }
            }
        }

        return errors;
    }

    /**
     * Get unique placeholders as JSON array string
     */
    public static String toJsonArray(List<String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return "[]";
        }
        Set<String> unique = new LinkedHashSet<>(placeholders);
        return "[\"" + String.join("\",\"", unique) + "\"]";
    }

    /**
     * Replace placeholders with actual values
     */
    public static String replacePlaceholders(String commentaryText, Map<String, String> values) {
        String result = commentaryText;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String placeholder = "[" + entry.getKey() + "]";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * Check if commentary has required placeholders available in context
     */
    public static boolean hasRequiredData(String placeholdersJson, Map<String, String> availableData) {
        if (placeholdersJson == null || placeholdersJson.equals("[]")) {
            return true; // No placeholders required
        }

        // Simple JSON parsing (for production, use Jackson)
        String[] placeholders = placeholdersJson
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .split(",");

        for (String placeholder : placeholders) {
            String key = placeholder.trim();
            if (!key.isEmpty() && !availableData.containsKey(key)) {
                return false; // Required placeholder not available
            }
        }

        return true;
    }
}
