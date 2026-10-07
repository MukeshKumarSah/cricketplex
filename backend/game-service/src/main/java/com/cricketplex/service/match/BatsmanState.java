package com.cricketplex.service.match;

import com.cricketplex.entity.LineupPlayer;
import com.cricketplex.entity.Player;

public class BatsmanState {
    public final Player player; public final LineupPlayer lineupPlayer;
    public BatsmanState(LineupPlayer lp) { this.player = lp.getPlayer(); this.lineupPlayer = lp; }
}
