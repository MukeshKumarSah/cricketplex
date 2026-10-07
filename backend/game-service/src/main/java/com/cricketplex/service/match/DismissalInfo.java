package com.cricketplex.service.match;

import com.cricketplex.entity.Player;

public class DismissalInfo {
    public final String type; public final Player fielder; public final String commentary;
    public DismissalInfo(String type, Player fielder, String commentary) {
        this.type = type; this.fielder = fielder; this.commentary = commentary;
    }
}
