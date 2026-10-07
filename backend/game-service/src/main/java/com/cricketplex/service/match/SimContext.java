package com.cricketplex.service.match;

import com.cricketplex.entity.MatchLineup;

import java.util.Random;

public class SimContext {
    public final Random rng; public final String pitchType, condition, format;
    public final int temperature, maxOvers, maxPerBowler, target;
    public final boolean isChasing;
    public final MatchLineup battingLineup, bowlingLineup;
    public final int firstInningsTotal;

    public SimContext(Random rng, String pitchType, String condition, int temperature,
               int maxOvers, int maxPerBowler, String format,
               boolean isChasing, int target,
               MatchLineup battingLineup, MatchLineup bowlingLineup) {
        this(rng, pitchType, condition, temperature, maxOvers, maxPerBowler,
                format, isChasing, target, battingLineup, bowlingLineup, 0);
    }

    public SimContext(Random rng, String pitchType, String condition, int temperature,
               int maxOvers, int maxPerBowler, String format,
               boolean isChasing, int target,
               MatchLineup battingLineup, MatchLineup bowlingLineup,
               int firstInningsTotal) {
        this.rng = rng; this.pitchType = pitchType; this.condition = condition;
        this.temperature = temperature; this.maxOvers = maxOvers; this.maxPerBowler = maxPerBowler;
        this.format = format; this.isChasing = isChasing; this.target = target;
        this.battingLineup = battingLineup; this.bowlingLineup = bowlingLineup;
        this.firstInningsTotal = firstInningsTotal;
    }
}
