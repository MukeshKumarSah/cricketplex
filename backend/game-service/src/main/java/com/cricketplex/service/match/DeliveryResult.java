package com.cricketplex.service.match;

import com.cricketplex.entity.Player;

public class DeliveryResult {
    public int runs = 0;
    public boolean isWicket, isNonStrikerOut, isBoundary, isSix, isWide, isNoBall, isBye, isLegBye;
    public String dismissalType = null;
    public Player fielder       = null;
    public String commentary    = "";
}
