package com.cricketplex.service.match;

import com.cricketplex.entity.BattingScorecard;
import com.cricketplex.entity.BowlingScorecard;
import com.cricketplex.entity.Innings;
import com.cricketplex.entity.LineupPlayer;
import com.cricketplex.entity.Player;

public final class ScorecardFactory {
    private ScorecardFactory() {}

    public static BattingScorecard createBatCard(Innings innings, LineupPlayer lp, int position) {
        return BattingScorecard.builder().innings(innings).player(lp.getPlayer()).battingPosition(position).build();
    }

    public static BowlingScorecard createBowlCard(Innings innings, Player bowler) {
        return BowlingScorecard.builder().innings(innings).player(bowler).build();
    }
}
