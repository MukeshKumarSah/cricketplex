package com.cricketplex.service.match;

import com.cricketplex.entity.BowlingOrder;
import com.cricketplex.entity.LineupPlayer;
import com.cricketplex.entity.MatchLineup;
import com.cricketplex.entity.Player;

import java.util.*;

public final class TeamStrengthCalculator {
    private TeamStrengthCalculator() {}

    public static double captainSkillBonus(MatchLineup lineup) {
        if (lineup == null) return 0;
        return captainSkillBonus(lineup.getCaptain());
    }

    public static double captainSkillBonus(Player captain) {
        if (captain == null) return 0;
        double fromRating = (captain.getRating() / 100.0) * 0.8;
        double fromExp = Math.min(captain.getExperience() * 0.03, 0.6);
        return fromRating + fromExp;
    }

    public static Map<String, Object> computeTeamStrengthBreakdown(MatchLineup lineup, String pitchType,
                                                             String condition, int temperature) {
        List<LineupPlayer> sortedPlayers = new ArrayList<>(lineup.getPlayers());
        sortedPlayers.sort(Comparator.comparingInt(LineupPlayer::getBattingPosition));
        double captainBonus = captainSkillBonus(lineup);
        double topOrder = 0, middleOrder = 0, lowerOrder = 0, totalFielding = 0, wkSkill = 0;
        for (LineupPlayer lp : sortedPlayers) {
            Player p = lp.getPlayer(); int pos = lp.getBattingPosition();
            double batEff = p.getBatRating() + (p.getConfidence() / 100.0) * 3.0
                    + Math.min(p.getExperience() * 0.3, 10.0) + (p.getFitness() / 100.0) * 3.0
                    + PitchWeatherCalculator.getBatterPitchModifier(pitchType, p.getBatHand(), (int) p.getBatRating(), p.getExperience(), 1, 50)
                    + PitchWeatherCalculator.getWeatherEffect(condition, temperature, p.getBowlType()).battingMod
                    + PitchWeatherCalculator.getBatterWeatherModifier(condition, temperature, p.getExperience(), (int) p.getBatRating())
                    + captainBonus;
            batEff = Math.max(5, Math.min(batEff, 120));
            if (pos <= 3) topOrder += batEff; else if (pos <= 7) middleOrder += batEff; else lowerOrder += batEff;
            totalFielding += p.getFldRating();
            if ("WK".equals(p.getRole()) || "WK_BAT".equals(p.getRole()) || "KEEPER".equals(p.getRole()))
                wkSkill = Math.max(wkSkill, p.getKeeperRating());
        }
        double seamBowling = 0, spinBowling = 0; int seamCount = 0, spinCount = 0;
        Set<UUID> seenBowlerIds = new HashSet<>();
        for (BowlingOrder bo : lineup.getBowlingOrders()) {
            Player p = bo.getBowler();
            if (!seenBowlerIds.add(p.getId())) continue;
            String bType = p.getBowlType() != null ? p.getBowlType() : "M";
            PitchEffect pe = PitchWeatherCalculator.getPitchEffect(pitchType, bType);
            WeatherEffect we = PitchWeatherCalculator.getWeatherEffect(condition, temperature, bType);
            double bowlEff = p.getBowlRating() + (p.getConfidence() / 100.0) * 2.5
                    + Math.min(p.getExperience() * 0.3, 10.0) + (p.getFitness() / 100.0) * 3.0
                    + pe.bowlerBonus + we.bowlingMod + PitchWeatherCalculator.getBowlerTypeMatchup(bType, "RH", pitchType)
                    + captainBonus;
            bowlEff = Math.max(5, Math.min(bowlEff, 120));
            if (MatchFormatRules.PACE_TYPES.contains(bType)) { seamBowling += bowlEff; seamCount++; }
            else if (MatchFormatRules.SPIN_TYPES.contains(bType)) { spinBowling += bowlEff; spinCount++; }
            else { seamBowling += bowlEff; seamCount++; }
        }
        double fldComponent = totalFielding + wkSkill;
        double total = topOrder + middleOrder + lowerOrder + seamBowling + spinBowling + Math.round(fldComponent / 4.0);
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("topOrder", Math.round(topOrder));
        breakdown.put("middleOrder", Math.round(middleOrder));
        breakdown.put("lowerOrder", Math.round(lowerOrder));
        breakdown.put("seamBowling", Math.round(seamBowling));
        breakdown.put("seamCount", seamCount);
        breakdown.put("spinBowling", Math.round(spinBowling));
        breakdown.put("spinCount", spinCount);
        breakdown.put("fielding", Math.round(totalFielding));
        breakdown.put("wkSkill", Math.round(wkSkill));
        breakdown.put("fldComponent", Math.round(fldComponent));
        breakdown.put("total", Math.round(total));
        return breakdown;
    }

}
