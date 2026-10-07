package com.cricketplex.service.match;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FCResumeState {
    public UUID strikerId, nonStrikerId, previousBowlerId;
    public int nextBatIdx;
    public Map<UUID, Integer> bowlerBallCounts = new HashMap<>();
}
