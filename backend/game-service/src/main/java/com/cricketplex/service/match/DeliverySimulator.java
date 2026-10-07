package com.cricketplex.service.match;

import com.cricketplex.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class DeliverySimulator {
    private final CommentaryEngine commentaryEngine;

    public DeliveryResult simulateDelivery(
            SimContext ctx, BatsmanState batter, BatsmanState nonStriker,
            Player bowler, String bowlerAggression,
            double teamFieldingAvg, double keeperSkill,
            int overNumber, int totalRuns, int totalWickets, int legalBallsBowled,
            BattingScorecard batCard, int bowlerBalls,
            boolean isFreeHit, String previousBatHand, int partnershipBalls,
            int battingPosition, boolean battingPpActive) {

        Random rng = ctx.rng;
        DeliveryResult result = new DeliveryResult();

        // ── Aggression & player attributes ──
        double batAggrMod  = PitchWeatherCalculator.getAggressionModifier(batter.lineupPlayer.getBatAggression());
        double bowlAggrMod = PitchWeatherCalculator.getAggressionModifier(bowlerAggression);
        int ballsFaced = batCard != null && batCard.getBallsFaced() != null ? batCard.getBallsFaced() : 0;
        double setBatsmanFactor = PitchWeatherCalculator.getSetBatsmanFactor(ctx.format, ballsFaced,
                batter.lineupPlayer.getBatAggression());

        double batConfidence  = batter.player.getConfidence()  / 100.0;
        double bowlConfidence = bowler.getConfidence()         / 100.0;
        double batExpBonus    = Math.min(batter.player.getExperience()  * 0.3, 10.0);
        double bowlExpBonus   = Math.min(bowler.getExperience()         * 0.3, 10.0);
        double batFitness     = batter.player.getFitness()  / 100.0;
        double bowlFitness    = bowler.getFitness()          / 100.0;
        double batStamina     = batter.player.getStamina()   / 100.0;
        double bowlStamina    = bowler.getStamina()          / 100.0;

        // FIX: batter fatigue — non-linear acceleration after 100 balls for FC realism
        double batFatigue;
        if (ballsFaced <= 100) {
            batFatigue = 1.0 - (ballsFaced * 0.0005 * (1.0 - batStamina));
        } else {
            double baseLoss = 100 * 0.0005 * (1.0 - batStamina);
            double extraLoss = (ballsFaced - 100) * 0.0015 * (1.0 - batStamina);
            batFatigue = 1.0 - baseLoss - extraLoss;
        }
        batFatigue = Math.max(0.70, batFatigue);

        // FIX: bowler fatigue — T20 floor raised to 0.85 so 4th over is harder
        boolean bowlerIsPace = MatchFormatRules.PACE_TYPES.contains(bowler.getBowlType());
        double decayRate = bowlerIsPace ? 0.003 : 0.0015;
        double fatigueFloor = "T20".equalsIgnoreCase(ctx.format) ? 0.85
                : "ODI".equalsIgnoreCase(ctx.format) ? 0.78 : 0.65;
        double bowlFatigue = Math.max(fatigueFloor, 1.0 - (bowlerBalls * decayRate * (1.0 - bowlStamina)));

        // ── Pitch effects ──
        PitchEffect pitchEffect    = PitchWeatherCalculator.getPitchEffect(ctx.pitchType, bowler.getBowlType());
        double pitchDecay          = PitchWeatherCalculator.getPitchDecayFactor(ctx.pitchType, overNumber, ctx.maxOvers);
        // FIX: intra-innings pitch wear for ODI/T20 — spin gets more grip after over 25
        double intraPitchWear      = PitchWeatherCalculator.getIntraPitchWear(ctx.format, overNumber, bowler.getBowlType());
        double effectivePitchBonus = pitchEffect.bowlerBonus * pitchDecay + intraPitchWear;

        double batPitchMod = PitchWeatherCalculator.getBatterPitchModifier(ctx.pitchType, batter.player.getBatHand(),
                (int) batter.player.getBatRating(), batter.player.getExperience(), overNumber, ctx.maxOvers);

        // ── Weather ──
        WeatherEffect weatherEffect = PitchWeatherCalculator.getWeatherEffect(ctx.condition, ctx.temperature, bowler.getBowlType());
        double batWeatherMod = PitchWeatherCalculator.getBatterWeatherModifier(ctx.condition, ctx.temperature,
                batter.player.getExperience(), (int) batter.player.getBatRating());

        // ── Type matchup ──
        double typeMatchup   = PitchWeatherCalculator.getBowlerTypeMatchup(bowler.getBowlType(), batter.player.getBatHand(), ctx.pitchType);
        double phaseModifier = PitchWeatherCalculator.getPhaseModifier(ctx.format, overNumber, ctx.maxOvers);

        // FIX: ball age consistent with getBallConditionModifier (uses modular over for FC)
        int ballAgeOvers = "FC".equalsIgnoreCase(ctx.format)
                ? ((overNumber - 1) % 80) + 1 : overNumber;
        double ballCondMod = PitchWeatherCalculator.getBallConditionModifier(ctx.format, overNumber, bowler.getBowlType(), ctx.condition);

        // FIX: reverse swing uses ball age (ballAgeOvers), not raw overNumber
        double reverseSwingBonus = 0;
        if (bowlerIsPace && bowler.getBowlRating() >= 60) {
            int ageThreshold = "ODI".equalsIgnoreCase(ctx.format) ? 35
                    : "FC".equalsIgnoreCase(ctx.format) ? 55 : 99;
            if (ballAgeOvers > ageThreshold) {
                double intensity = Math.min(1.0,
                        (ballAgeOvers - ageThreshold) / (double)(ctx.maxOvers - ageThreshold + 1));
                reverseSwingBonus = 6.0 * intensity;
            }
        }

        // LH/RH disruption (bowler-type-sensitive)
        double lrDisruption = 0;
        if (previousBatHand != null && !previousBatHand.equals(batter.player.getBatHand())) {
            String bt = bowler.getBowlType() != null ? bowler.getBowlType() : "";
            if      ("LAP".equals(bt) && "RH".equals(batter.player.getBatHand())) lrDisruption = -5.0;
            else if ("FS".equals(bt)  && "LH".equals(batter.player.getBatHand())) lrDisruption = -4.0;
            else if ("WS".equals(bt)  && "RH".equals(batter.player.getBatHand())) lrDisruption = -3.5;
            else                                                                    lrDisruption = -2.5;
        }

        // FIX: dynamic field placement — affects p4/p6/pCaught outside powerplay
        double[] fieldPlacement = PitchWeatherCalculator.getDynamicFieldPlacement(ctx, overNumber, totalRuns, totalWickets);
        double fieldP4Mod  = fieldPlacement[0];
        double fieldP6Mod  = fieldPlacement[1];
        double fieldWktMod = fieldPlacement[2];

        // ── Composite strengths ──
        double batStrength = batter.player.getBatRating()
                + batAggrMod   * 8.0
                + batConfidence * 6.0
                + batExpBonus
                + batFitness   * 3.0
                - effectivePitchBonus * 0.75
                + batPitchMod
                + weatherEffect.battingMod
                + batWeatherMod
                + phaseModifier * 2.0
                + setBatsmanFactor * 3.5;
        batStrength *= batFatigue;
        batStrength  = Math.max(5, Math.min(batStrength, 120));

        double bowlStrength = bowler.getBowlRating()
                + bowlAggrMod    * 5.0
                + bowlConfidence * 5.0
                + bowlExpBonus
                + bowlFitness    * 3.0
                + effectivePitchBonus
                + weatherEffect.bowlingMod
                + ballCondMod
                + reverseSwingBonus
                + typeMatchup
                + teamFieldingAvg * 0.15
                + keeperSkill     * 0.05
                + lrDisruption;
        bowlStrength *= bowlFatigue;
        bowlStrength  = Math.max(5, Math.min(bowlStrength, 120));

        // ─── WIDE / NO-BALL CHECK ───
        double wideBase = "T20".equalsIgnoreCase(ctx.format) ? 0.028
                : "FC".equalsIgnoreCase(ctx.format) ? 0.008 : 0.016;
        double noBallBase = "T20".equalsIgnoreCase(ctx.format) ? 0.010
                : "FC".equalsIgnoreCase(ctx.format) ? 0.003 : 0.007;
        boolean deathOvers = ("T20".equalsIgnoreCase(ctx.format) && overNumber >= 16)
                || ("ODI".equalsIgnoreCase(ctx.format) && overNumber >= 41);
        double yorkerPressure = deathOvers && "A".equals(bowlerAggression) ? 0.006 : 0.0;
        double wideChance = wideBase
                + (bowlAggrMod * 0.004)
                + yorkerPressure
                + (1.0 - bowlFitness) * 0.004
                + (1.0 - bowlStamina) * 0.003
                - (bowler.getBowlRating() * 0.00008);
        double noBallChance = noBallBase
                + (bowlAggrMod * 0.0025)
                + (bowlerIsPace ? 0.0015 : -0.0005)
                + (1.0 - bowlFitness) * 0.002
                + (1.0 - bowlStamina) * 0.0015
                - (bowler.getBowlRating() * 0.00004);
        wideChance = Math.max(0.003, Math.min(wideChance, 0.060));
        noBallChance = Math.max(0.001, Math.min(noBallChance, 0.025));
        double extraChance = wideChance + noBallChance;

        if (rng.nextDouble() < extraChance) {
            boolean isWide = rng.nextDouble() < (wideChance / Math.max(0.0001, extraChance));
            result.isWide   = isWide;
            result.isNoBall = !isWide;
            result.runs     = 1;

            if (isWide) {
                double extraRoll = rng.nextDouble();
                if      (extraRoll < 0.05)  { result.runs = 5; }
                else if (extraRoll < 0.15)  { result.runs += 2; }
                else if (extraRoll < 0.20)  { result.runs += 1; }
                // FIX: wide boundary does NOT set isBoundary — it's extras, not batting boundary
                String userComm = commentaryEngine.getExtraCommentary(rng, ctx, overNumber, batter, bowler, "WD", result.runs, totalRuns, totalWickets);
                result.commentary = userComm != null ? userComm : 
                        (result.runs >= 5 ? "Wide — races away for 4 wides to the boundary"
                        : "Wide ball" + (result.runs > 1 ? ", plus " + (result.runs - 1) + " run" + (result.runs > 2 ? "s" : "") : ""));

                // FIX: stumped off wide — possible if spinner bowling and batter ventures out
                boolean isSpinner = MatchFormatRules.SPIN_TYPES.contains(bowler.getBowlType());
                if (isSpinner && result.runs == 1 && rng.nextDouble() < 0.015) {
                    // Batter came down the track, missed, stumped off wide
                    result.isWicket     = true;
                    result.dismissalType = "STUMPED";
                    result.fielder       = ctx.bowlingLineup.getKeeper();
                    result.commentary    = "Stumped off a wide! Batter strayed out of crease.";
                }
            } else {
                double extraRoll = rng.nextDouble();
                if      (extraRoll < 0.05)  { result.runs += 4; result.isBoundary = true; }
                else if (extraRoll < 0.15)  { result.runs += 2; }
                else if (extraRoll < 0.20)  { result.runs += 1; }
                String userComm = commentaryEngine.getExtraCommentary(rng, ctx, overNumber, batter, bowler, "NB", result.runs, totalRuns, totalWickets);
                result.commentary = userComm != null ? userComm :
                        ("No ball" + (result.runs > 1 ? ", plus " + (result.runs - 1) + " run" + (result.runs > 2 ? "s" : "") : "")
                        + (result.isBoundary ? " away to the boundary" : ""));
            }
            if (isFreeHit) result.commentary = "Free Hit! " + result.commentary;
            return result;
        }

        // ─── BASE SCORING PROBABILITIES ───
        // FIX: Recalibrated for real international averages (2019-2024 ball-by-ball data):
        //   T20:  E = 0.25×0 + 0.27×1 + 0.10×2 + 0.02×3 + 0.13×4 + 0.07×6 = 1.41 → 8.46 RPO (baseline)
        //   ODI:  E = 0.44×0 + 0.28×1 + 0.09×2 + 0.02×3 + 0.09×4 + 0.04×6 = 1.10 → 6.6 RPO → ~5.6 after wickets
        //   FC:   E = 0.52×0 + 0.25×1 + 0.08×2 + 0.03×3 + 0.05×4 + 0.01×6 = 0.76 → ~3.3 RPO after wickets
        double pDot, p1, p2, p3, p4, p6, pWicket;

        if ("T20".equalsIgnoreCase(ctx.format)) {
            pDot    = 0.30;
            p1      = 0.27;   // was 0.30 — fewer singles, more intent
            p2      = 0.09;
            p3      = 0.015;
            p4      = 0.120;  // was 0.115
            p6      = 0.055;
            pWicket = 0.050;
        } else if ("FC".equalsIgnoreCase(ctx.format)) {
            pDot    = 0.57;
            p1      = 0.24;
            p2      = 0.07;
            p3      = 0.02;
            p4      = 0.04;
            p6      = 0.005;
            pWicket = 0.023;
        } else { // ODI
            pDot    = 0.50;   // was 0.48 — more dots means pitch-reading matters
            p1      = 0.27;   // was 0.31 — KEY FIX: fewer singles, more decisive play
            p2      = 0.08;
            p3      = 0.015;
            p4      = 0.085;  // was 0.075 — more boundaries to compensate
            p6      = 0.022;  // was 0.020
            pWicket = 0.028;  // was 0.030 — slightly lower base wicket rate
        }

        // FIX: skill_diff uses sqrt-damped scale to prevent extreme probability distortion
        double skill_diff = batStrength - bowlStrength;
        double rawScale   = "T20".equalsIgnoreCase(ctx.format) ? 1.2
                : "FC".equalsIgnoreCase(ctx.format) ? 0.6 : 0.9;
        // sqrt dampening: large gaps shift less dramatically
        double dampedDiff = Math.signum(skill_diff) * Math.sqrt(Math.abs(skill_diff));
        double shift      = (dampedDiff / 15.0) * rawScale;  // 15.0 normalizes sqrt(225)
        pDot    -= shift * 0.35;
        p1      += shift * 0.10;
        p2      += shift * 0.05;
        p4      += shift * 0.10;
        p6      += shift * 0.06;
        pWicket -= shift * 0.20;

        // ── Batting aggression ──
        double aggrScale  = "T20".equalsIgnoreCase(ctx.format) ? 1.3
                : "FC".equalsIgnoreCase(ctx.format) ? 0.5 : 0.8;
        double batSkillMod = Math.max(0.2, Math.min(1.2, batter.player.getBatRating() / 50.0));
        if ("A".equals(batter.lineupPlayer.getBatAggression())) {
            p4      += 0.025 * aggrScale * batSkillMod;
            p6      += 0.020 * aggrScale * batSkillMod;
            pWicket += 0.012 * aggrScale;
            pDot    -= 0.040 * aggrScale * batSkillMod;
            p1      -= 0.015 * aggrScale * batSkillMod;
        } else if ("D".equals(batter.lineupPlayer.getBatAggression())) {
            p4      -= 0.015 * aggrScale;
            p6      -= 0.015 * aggrScale;
            pWicket -= 0.015 * aggrScale * batSkillMod;
            pDot    += 0.030 * aggrScale;
            p1      += 0.015 * aggrScale;
        }

        // ── Set-batsman effect ──
        double setSkillMod = Math.max(0.3, Math.min(1.0, batter.player.getBatRating() / 40.0));
        if (setBatsmanFactor >= 0) {
            pDot    -= 0.022 * setBatsmanFactor * setSkillMod;
            p1      += 0.004 * setBatsmanFactor * setSkillMod;
            p2      += 0.008 * setBatsmanFactor * setSkillMod;
            p4      += 0.011 * setBatsmanFactor * setSkillMod;
            p6      += 0.006 * setBatsmanFactor * setSkillMod;
            pWicket -= 0.010 * setBatsmanFactor;
        } else {
            // New batter — harder to score, slightly more vulnerable
            // FIXED: reduced settling pWicket from 0.025 to 0.012 to prevent
            // triple-compounding with chase pressure + wicket cluster in innings 2
            double settling = Math.abs(setBatsmanFactor);
            pDot    += 0.06  * settling;
            p1      -= 0.01  * settling;
            p4      -= 0.030 * settling;
            p6      -= 0.020 * settling;
            pWicket += 0.012 * settling;   // was 0.025
        }

        // ── Bowling aggression ──
        if ("A".equals(bowlerAggression)) {
            pWicket += 0.010 * aggrScale;
            p4      += 0.015 * aggrScale;
            p6      += 0.010 * aggrScale;
            pDot    -= 0.015 * aggrScale;
            p1      -= 0.010 * aggrScale;
        } else if ("D".equals(bowlerAggression)) {
            pDot    += 0.030 * aggrScale;
            p1      -= 0.010 * aggrScale;
            p4      -= 0.010 * aggrScale;
            pWicket -= 0.010 * aggrScale;
        }

        // ─── SITUATIONAL MODIFIERS ───

        String playerAggr = batter.lineupPlayer.getBatAggression();
        if (playerAggr == null) playerAggr = "N";

        // 1. Wicket-cluster pressure — FIXED: thresholds raised, pWicket reduced
        // and cluster now REDUCES in chasing innings to prevent triple-compounding
        {
            double completedOvers   = Math.max(1.0, legalBallsBowled / 6.0);
            double wktsPerOver      = totalWickets / completedOvers;
            // RAISED thresholds — was 0.60/0.35/0.45, too trigger-happy
            double collapseThreshold = "T20".equalsIgnoreCase(ctx.format) ? 0.75
                    : "FC".equalsIgnoreCase(ctx.format) ? 0.45 : 0.60;
            double collapseFactor   = 0;
            if (wktsPerOver > collapseThreshold)
                collapseFactor = Math.min(1.0, (wktsPerOver - collapseThreshold) / 0.9);
            // RAISED floor thresholds — was 8/6/4, now 9/7/5
            if (totalWickets >= 9)      collapseFactor = Math.max(collapseFactor, 0.60);
            else if (totalWickets >= 7) collapseFactor = Math.max(collapseFactor, 0.25);
            else if (totalWickets >= 5) collapseFactor = Math.max(collapseFactor, 0.08);
            double resistFactor     = "A".equals(playerAggr) ? 0.7 : ("D".equals(playerAggr) ? 1.2 : 1.0);
            double adjustedCollapse = collapseFactor * resistFactor;
            // In chasing innings, halve the collapse wicket penalty — teams fight back
            if (ctx.isChasing) adjustedCollapse *= 0.5;
            pDot    += 0.050 * adjustedCollapse;  // was 0.060
            p1      += 0.020 * adjustedCollapse;  // was 0.030
            p4      -= 0.030 * adjustedCollapse;  // was 0.040
            p6      -= 0.020 * adjustedCollapse;  // was 0.030
            pWicket += 0.006 * adjustedCollapse;  // was 0.010 — significantly reduced
        }

        // 2. Chase pressure — FIXED: boundary compensation raised, pWicket reduced
        // so teams accelerate scoring rather than just losing wickets faster.
        if (ctx.isChasing) {
            int runsNeeded     = ctx.target - totalRuns;
            int totalBalls     = ctx.maxOvers * 6;
            int ballsRemaining = Math.max(1, totalBalls - legalBallsBowled);
            double requiredRate = (runsNeeded * 6.0) / ballsRemaining;
            // Use actual first innings RPO as par — avoids false pressure spikes
            // when batting first scored a high-but-achievable total
            double pitchParRate = PitchWeatherCalculator.getPitchAwareParRate(ctx.format, ctx.pitchType);
            double parRate = ctx.firstInningsTotal > 0
                    ? Math.max(pitchParRate, ctx.firstInningsTotal * 6.0 / totalBalls)
                    : pitchParRate;
            double progress     = legalBallsBowled / (double) totalBalls;
            double progressMult = 0.30 + (0.70 * progress);

            if (runsNeeded > 0) {
                double ratio = requiredRate / parRate;
                if (ratio > 1.0) {
                    // Behind in chase — accelerate scoring, accept some wicket risk
                    double intensity = Math.min(1.0, (ratio - 1.0)) * progressMult;
                    pDot    -= 0.05 * intensity;   // more intent
                    p1      -= 0.02 * intensity;
                    p4      += 0.04 * intensity;   // more boundary hunting
                    p6      += 0.04 * intensity;   // more sixes attempted
                    pWicket += 0.008 * intensity;  // REDUCED: less punishing
                } else if (ratio < 1.0) {
                    // Ahead in chase — tick singles, protect wickets
                    double intensity = Math.min(1.0, (1.0 - ratio) / 0.6) * progressMult;
                    pDot    -= 0.03 * intensity;
                    p1      += 0.05 * intensity;
                    p2      += 0.01 * intensity;
                    p4      -= 0.01 * intensity;
                    p6      -= 0.005 * intensity;
                    pWicket -= 0.005 * intensity;
                }
            }
        }

        // 3a. Early innings pacing
        double oversDone = legalBallsBowled / 6.0;
        if (oversDone < 4 && totalWickets >= 2) {
            double earlyPressure = Math.min(1.0, totalWickets / 3.0);
            pDot    += 0.04 * earlyPressure;
            p1      += 0.03 * earlyPressure;
            p4      -= 0.03 * earlyPressure;
            p6      -= 0.03 * earlyPressure;
            pWicket -= 0.01 * earlyPressure;
        }
        if ("FC".equalsIgnoreCase(ctx.format) && !ctx.isChasing && oversDone < 10 && totalWickets >= 3) {
            double earlyPressure = Math.min(1.0, totalWickets / 4.0);
            pDot    += 0.06 * earlyPressure;
            p1      += 0.02 * earlyPressure;
            p4      -= 0.04 * earlyPressure;
            p6      -= 0.02 * earlyPressure;
            pWicket -= 0.01 * earlyPressure;
        }

        // 3b. ODI middle overs consolidation — FIXED: removed p1+=0.07 which was
        // consuming too many balls on singles for BOTH innings. Now a mild
        // dot+boundary reshuffle only. Chasing teams in particular benefit from
        // keeping boundary probability alive in the middle overs.
        if ("ODI".equalsIgnoreCase(ctx.format) && oversDone >= 10 && oversDone < 40) {
            pDot += 0.02;   // slightly more defensive
            p1   += 0.02;   // modest singles increase (was +0.07 — removed)
            p4   -= 0.01;   // mild boundary reduction vs powerplay
        }

        // 3c. Death overs acceleration
        if ("T20".equalsIgnoreCase(ctx.format) && oversDone >= 15) {
            double wktFactor = Math.max(0.0, 1.0 - (Math.max(0, totalWickets - 4) * 0.2));
            double deathPush = Math.min(0.90, (0.5 + (oversDone - 15) * 0.16)) * wktFactor;
            pDot    -= 0.07 * deathPush;
            p1      += 0.02 * deathPush;
            p4      += 0.03 * deathPush;
            p6      += 0.02 * deathPush;
            pWicket += 0.015 * deathPush;
        } else if ("ODI".equalsIgnoreCase(ctx.format) && oversDone >= 40) {
            double wktFactor = Math.max(0.0, 1.0 - (Math.max(0, totalWickets - 4) * 0.15));
            double deathPush = Math.min(0.90, (0.5 + (oversDone - 40) * 0.10)) * wktFactor;
            pDot    -= 0.08 * deathPush;
            p1      += 0.04 * deathPush;
            p4      += 0.04 * deathPush;
            p6      += 0.025 * deathPush;
            pWicket += 0.018 * deathPush;
        }

        // 4. Powerplay field restriction (mandatory)
        double[] ppBoost = PitchWeatherCalculator.getPowerplayBoost(ctx.format, overNumber);
        p4      += ppBoost[0];
        p6      += ppBoost[1];
        pDot    -= ppBoost[2];
        pWicket -= ppBoost[3];

        // 5. Dynamic field placement (captain strategy outside powerplay)
        p4      += fieldP4Mod;
        p6      += fieldP6Mod;
        pWicket += fieldWktMod;

        // 6. Mismatch crush / feast
        if (skill_diff < -25) {
            double mm = Math.min(1.0, Math.abs(skill_diff + 25) / 50.0);
            p4      *= (1.0 - 0.85 * mm);
            p6      *= (1.0 - 0.95 * mm);
            pWicket += 0.15 * mm;
            pDot    += 0.10 * mm;
            p1      += 0.05 * mm;
        } else if (skill_diff > 25) {
            double fi = Math.min(1.0, (skill_diff - 25) / 50.0);
            p4      += 0.10 * fi;
            p6      += 0.08 * fi;
            pDot    -= 0.12 * fi;
            p1      -= 0.06 * fi;
        }

        // 7. Free Hit — scoring boost, no wicket (handled at result phase)
        if (isFreeHit) {
            p4      += 0.12;
            p6      += 0.10;
            pDot    -= 0.15;
            p1      -= 0.07;
        }

        // 8. Pitch-phase direct boost
        double[] phaseBoost = PitchWeatherCalculator.getPitchPhaseDirectBoost(ctx.pitchType, overNumber,
                ctx.maxOvers, bowler.getBowlType());
        pDot    += phaseBoost[0];
        pWicket += phaseBoost[1];

        // 9. Partnership momentum — FIX: exponential curve (1-e^(-x/30)) instead of linear
        if (partnershipBalls >= 10) {
            double momentum = 1.0 - Math.exp(-(partnershipBalls - 10) / 30.0); // max approaches 1.0
            pWicket -= 0.020 * momentum; // up to −0.020
            pDot    -= 0.010 * momentum;
            p4      += 0.005 * momentum;
        }

        // 10. Spinner variation delivery (doosra / googly)
        if (MatchFormatRules.SPIN_TYPES.contains(bowler.getBowlType())
                && bowler.getBowlRating() >= 50
                && rng.nextDouble() < 0.05) {
            pWicket += 0.020;
            pDot    += 0.015;
            p4      -= 0.010;
            result.commentary = "[Variation] ";
        }

        // FIX: positional wicket modifier — tailenders face higher dismissal probability
        double positionalWktMult = PitchWeatherCalculator.getPositionalWicketMultiplier(battingPosition);
        pWicket *= positionalWktMult;

        // ── Clamp & normalize ──
        pDot    = Math.max(0.10, pDot);
        p1      = Math.max(0.05, p1);
        p2      = Math.max(0.02, p2);
        p3      = Math.max(0.005, p3);
        p4      = Math.max(0.005, p4);
        p6      = Math.max(0.001, p6);
        // FIX: hard cap at 0.28 (was 0.50) to prevent unrealistic tail decimation
        pWicket = Math.max(0.01, Math.min(pWicket, 0.28));

        double total = pDot + p1 + p2 + p3 + p4 + p6 + pWicket;
        pDot    /= total; p1 /= total; p2 /= total; p3 /= total;
        p4      /= total; p6 /= total; pWicket /= total;

        // ─── ROLL ───
        double roll = rng.nextDouble();
        double cumulative = 0;

        double byeChance = Math.max(0.002, 0.015 - keeperSkill * 0.0001);

        if (roll < (cumulative += pDot)) {
            if (rng.nextDouble() < byeChance) {
                double byeRoll = rng.nextDouble();
                int byeRuns    = (byeRoll < 0.05) ? 4 : (byeRoll < 0.15) ? 2 : (byeRoll < 0.20) ? 3 : 1;
                boolean isLegBye = rng.nextBoolean();
                result.runs      = byeRuns;
                result.isBye     = !isLegBye;
                result.isLegBye  = isLegBye;
                result.isBoundary = (byeRuns == 4);
                String extraType = isLegBye ? "LB" : "BYE";
                String userComm = commentaryEngine.getExtraCommentary(rng, ctx, overNumber, batter, bowler, extraType, byeRuns, totalRuns, totalWickets);
                result.commentary = userComm != null ? userComm :
                        ((isLegBye ? "Leg bye, " : "Bye, ") + byeRuns + " run" + (byeRuns > 1 ? "s" : "")
                        + (result.isBoundary ? " away to the boundary" : ""));
            } else {
                result.runs       = 0;
                result.commentary += commentaryEngine.getDotBallCommentary(rng, ctx, overNumber, batter, bowler, totalRuns, totalWickets);
            }
        } else if (roll < (cumulative += p1)) {
            result.runs = 1; result.commentary += commentaryEngine.getSingleCommentary(rng, ctx, overNumber, batter, bowler, totalRuns, totalWickets);
        } else if (roll < (cumulative += p2)) {
            result.runs = 2; result.commentary += commentaryEngine.getTwoRunsCommentary(rng, ctx, overNumber, batter, bowler, totalRuns, totalWickets);
        } else if (roll < (cumulative += p3)) {
            result.runs = 3; result.commentary += commentaryEngine.getThreeRunsCommentary(rng, ctx, overNumber, batter, bowler, totalRuns, totalWickets);
        } else if (roll < (cumulative += p4)) {
            result.runs = 4; result.isBoundary = true;
            result.commentary += "FOUR! " + commentaryEngine.getBoundaryCommentary(rng, ctx, overNumber, batter, bowler, totalRuns, totalWickets);
        } else if (roll < (cumulative += p6)) {
            result.runs = 6; result.isSix = true;
            result.commentary += "SIX! " + commentaryEngine.getSixCommentary(rng, ctx, overNumber, batter, bowler, totalRuns, totalWickets);
        } else {
            // ─── WICKET ───
            // FIX: free hit — run through normal scoring but suppress wicket (except run out)
            if (isFreeHit) {
                // Batter survives; re-roll for scoring
                double fhRoll = rng.nextDouble();
                result.runs = fhRoll < 0.25 ? 0 : fhRoll < 0.50 ? 1 : fhRoll < 0.70 ? 2
                        : fhRoll < 0.85 ? 4 : 6;
                if (result.runs == 4)  result.isBoundary = true;
                if (result.runs == 6)  result.isSix      = true;
                result.commentary = "Free Hit! Would have been out, but batter survives!"
                        + (result.runs > 0 ? " Scores " + result.runs + "." : "");
                return result;
            }

            result.isWicket = true;
            result.runs     = 0;
            DismissalInfo dismissal = determineDismissal(
                    rng, bowler, batter, teamFieldingAvg, keeperSkill, ctx,
                    legalBallsBowled, totalRuns, totalWickets, battingPosition);
            if ("RUN_OUT".equals(dismissal.type) && rng.nextDouble() < 0.25) {
                result.runs = 1;
            }
                if ("RUN_OUT".equals(dismissal.type)) {
                // Allow both run-out variants to match submitted commentary.
                result.isNonStrikerOut = rng.nextDouble() < 0.30;
                }
            result.dismissalType = dismissal.type;
            result.fielder       = dismissal.fielder;
            result.commentary   += "OUT! " + commentaryEngine.getWicketCommentary(
                    rng,
                    ctx,
                    overNumber,
                    batter,
                    nonStriker,
                    bowler,
                    dismissal,
                    result.isNonStrikerOut,
                    result.runs,
                    totalRuns,
                    totalWickets
            );

            if ("DROPPED".equals(dismissal.type)) {
                result.isWicket      = false;
                result.runs          = 0;
                result.dismissalType = null;
                result.fielder       = dismissal.fielder;
                result.commentary    = dismissal.commentary + " Batter survives!";
            }
        }

        if (isFreeHit && !result.commentary.startsWith("Free Hit!")) {
            result.commentary = "Free Hit! " + result.commentary;
        }
        return result;
    }

    public DismissalInfo determineDismissal(
            Random rng, Player bowler, BatsmanState batter,
            double fieldingAvg, double keeperSkill, SimContext ctx,
            int legalBallsBowled, int totalRuns, int totalWickets, int battingPosition) {

        String bowlType = bowler.getBowlType() != null ? bowler.getBowlType() : "M";
        boolean isPace  = MatchFormatRules.PACE_TYPES.contains(bowlType);
        boolean isSpin  = MatchFormatRules.SPIN_TYPES.contains(bowlType);
        boolean isLAP   = "LAP".equals(bowlType);

        // Dismissal distribution calibrated to real-world international data:
        // Caught ≈57%, Bowled ≈17%, LBW ≈19%, RunOut ≈4%, Stumped ≈2%, HitWicket ≈0.2%
        double pBowled       = isPace ? 0.17 : (isSpin ? 0.14 : 0.16);
        double pCaught       = isPace ? 0.44 : (isSpin ? 0.42 : 0.43);
        double pLBW          = isPace ? 0.19 : (isSpin ? 0.19 : 0.18);
        // FIX: stumping only realistic if batter is out of crease (spin / off-length bowling)
        double pStumped      = isSpin ? 0.04 : (isPace ? 0.003 : 0.008);
        double pRunOut       = 0.04;
        double pCaughtBehind = isPace ? 0.08 : (isSpin ? 0.03 : 0.05);
        double pHitWicket    = 0.002;

        // Keeper & fielding influences
        pCaught       += fieldingAvg  * 0.001;
        pCaughtBehind += keeperSkill  * 0.0005;
        pStumped      += keeperSkill  * 0.0008;

        // Pitch modifiers
        if ("GREEN".equals(ctx.pitchType) || "BOUNCY".equals(ctx.pitchType)) {
            pCaughtBehind += 0.04;
            pBowled       += 0.03;
        }
        if ("DUSTY".equals(ctx.pitchType) || "DRY".equals(ctx.pitchType)) {
            pStumped += 0.015;
            pLBW     += 0.02;
        }

        // Type-sensitive LBW adjustments
        if (isLAP && "RH".equals(batter.player.getBatHand())) {
            pLBW += 0.04; pBowled += 0.02;
        }
        if (!isLAP && isPace && "RH".equals(batter.player.getBatHand())) {
            pLBW += 0.02;
        }
        if (isSpin && "LH".equals(batter.player.getBatHand()) && "FS".equals(bowlType)) {
            pLBW += 0.03; pStumped += 0.01;
        }
        if ("WS".equals(bowlType) && "RH".equals(batter.player.getBatHand())) {
            pCaughtBehind += 0.015; pStumped += 0.01;
        }

        // FIX: tailenders more likely to be caught or bowled (less LBW — poor technique)
        if (battingPosition >= 9) {
            pCaught += 0.04;
            pBowled += 0.03;
            pLBW    -= 0.03;
        } else if (battingPosition >= 7) {
            pCaught += 0.02;
        }

        // Run-out pressure
        double runOutPressure = 0.0;
        if (ctx.isChasing) {
            double runsNeeded   = ctx.target > 0 ? ctx.target - totalRuns : 0;
            double oversComp    = legalBallsBowled / 6.0;
            double oversRem     = ctx.maxOvers - oversComp;
            double requiredRate = oversRem > 0 ? runsNeeded / oversRem : 0;
            double parRate      = ctx.target > 0 ? (double) ctx.target / ctx.maxOvers : 0;
            if (requiredRate > parRate * 1.5)      runOutPressure = 0.04;
            else if (requiredRate > parRate * 1.2) runOutPressure = 0.02;
        } else {
            double oversComp  = legalBallsBowled / 6.0;
            double wicketRate = oversComp > 0 ? totalWickets / oversComp : 0;
            if (wicketRate > 1.5)      runOutPressure = 0.02;
            else if (wicketRate > 1.0) runOutPressure = 0.01;
        }
        pRunOut += runOutPressure;

        // Normalize
        double dTotal = pBowled + pCaught + pLBW + pStumped + pRunOut + pCaughtBehind + pHitWicket;
        if (dTotal > 1.0) {
            double scale = 1.0 / dTotal;
            pBowled *= scale; pCaught *= scale; pLBW *= scale; pStumped *= scale;
            pRunOut *= scale; pCaughtBehind *= scale; pHitWicket *= scale;
            dTotal = 1.0;
        }

        double dRoll = rng.nextDouble() * dTotal;
        double dCum  = 0;

        List<LineupPlayer> fieldingPlayers = ctx.bowlingLineup.getPlayers();
        Player keeper = ctx.bowlingLineup.getKeeper();
        List<Player> nonKeeperFielders = fieldingPlayers.stream()
                .map(LineupPlayer::getPlayer)
                .filter(p -> keeper == null || !p.getId().equals(keeper.getId()))
                .toList();
        Player randomFielder = nonKeeperFielders.isEmpty()
                ? fieldingPlayers.get(rng.nextInt(fieldingPlayers.size())).getPlayer()
                : nonKeeperFielders.get(rng.nextInt(nonKeeperFielders.size()));

        if (dRoll < (dCum += pBowled)) {
            return new DismissalInfo("BOWLED", null,
                    batter.player.getFirstName() + " " + batter.player.getLastName()
                    + " bowled by " + bowler.getFirstName() + " " + bowler.getLastName());

        } else if (dRoll < (dCum += pCaught)) {
            double catchChance = 0.40 + (randomFielder.getFldRating() * 0.006);
            catchChance = Math.min(0.97, catchChance);
            if (rng.nextDouble() > catchChance) {
                return new DismissalInfo("DROPPED", randomFielder,
                        "Dropped! " + randomFielder.getFirstName() + " "
                        + randomFielder.getLastName() + " puts it down at "
                        + getFieldPosition(rng) + "!");
            }
            return new DismissalInfo("CAUGHT", randomFielder,
                    "Caught by " + randomFielder.getFirstName() + " " + randomFielder.getLastName()
                    + " off " + bowler.getFirstName() + " " + bowler.getLastName());

        } else if (dRoll < (dCum += pLBW)) {
            return new DismissalInfo("LBW", null,
                    "LBW! Trapped in front by " + bowler.getFirstName() + " " + bowler.getLastName());

        } else if (dRoll < (dCum += pStumped)) {
            return new DismissalInfo("STUMPED", keeper,
                    "Stumped by " + (keeper != null
                            ? keeper.getFirstName() + " " + keeper.getLastName() : "the keeper")
                    + " off " + bowler.getFirstName() + " " + bowler.getLastName());

        } else if (dRoll < (dCum += pRunOut)) {
            return new DismissalInfo("RUN_OUT", randomFielder,
                    "Run out! Direct hit by " + randomFielder.getFirstName() + " " + randomFielder.getLastName());

        } else if (dRoll < (dCum += pCaughtBehind)) {
            double catchChance = 0.55 + (keeper != null ? keeper.getKeeperRating() * 0.004 : 0);
            catchChance = Math.min(0.97, catchChance);
            if (rng.nextDouble() > catchChance) {
                String kn = keeper != null
                        ? keeper.getFirstName() + " " + keeper.getLastName() : "The keeper";
                return new DismissalInfo("DROPPED", keeper,
                        "Dropped! " + kn + " spills a tough chance behind the stumps!");
            }
            return new DismissalInfo("CAUGHT_BEHIND", keeper,
                    "Caught behind by " + (keeper != null
                            ? keeper.getFirstName() + " " + keeper.getLastName() : "the keeper")
                    + " off " + bowler.getFirstName() + " " + bowler.getLastName());

        } else {
            return new DismissalInfo("HIT_WICKET", null,
                    "Hit wicket! " + batter.player.getFirstName() + " " + batter.player.getLastName()
                    + " dislodged the bails");
        }
    }

    public String getFieldPosition(Random rng) {
        String[] positions = {"mid-on", "mid-off", "square leg", "fine leg", "cover",
                "point", "gully", "slip", "third man", "long-on", "long-off", "deep mid-wicket"};
        return positions[rng.nextInt(positions.length)];
    }

    public void applyStrikeFarming(SimContext ctx, BatsmanState striker, BatsmanState nonStriker,
                                     DeliveryResult delivery, int legalBallsThisOver) {
        if (delivery.isWicket || delivery.isBoundary || delivery.isSix
                || delivery.isWide || delivery.isNoBall || delivery.isBye || delivery.isLegBye) return;

        double strikerSkill = Math.max(1.0, striker.player.getBatRating());
        double partnerSkill = Math.max(1.0, nonStriker.player.getBatRating());
        boolean isStrongStriker = strikerSkill > partnerSkill && (partnerSkill / strikerSkill) <= 0.45;
        boolean isWeakStriker   = partnerSkill > strikerSkill && (strikerSkill / partnerSkill) <= 0.45;
        if (!isStrongStriker && !isWeakStriker) return;

        double gapIntensity = isStrongStriker
                ? (0.45 - partnerSkill / strikerSkill) / 0.45
                : (0.45 - strikerSkill / partnerSkill) / 0.45;
        double actionChance = 0.30 + 0.40 * gapIntensity;
        if ("T20".equalsIgnoreCase(ctx.format))      actionChance += 0.15;
        else if ("FC".equalsIgnoreCase(ctx.format))  actionChance -= 0.10;
        if (ctx.rng.nextDouble() > actionChance) return;

        if (isStrongStriker) {
            if (legalBallsThisOver < 4) {
                if (delivery.runs % 2 != 0) {
                    delivery.runs--;
                    delivery.commentary = "Refuses the single to keep the strike.";
                }
            } else {
                if (delivery.runs % 2 == 0) {
                    if (delivery.runs == 0) {
                        delivery.runs = 1;
                        delivery.commentary = "Tapped for a quick single to farm the strike next over.";
                    } else if (delivery.runs == 2) {
                        delivery.runs = 1;
                        delivery.commentary = "Settles for one to keep strike.";
                    }
                }
            }
        } else {
            if (legalBallsThisOver < 5) {
                if (delivery.runs % 2 == 0) {
                    if (delivery.runs == 0) {
                        delivery.runs = 1;
                        delivery.commentary = "Nudges one to give strike to the set batter.";
                    } else if (delivery.runs == 2) {
                        delivery.runs = 1;
                        delivery.commentary = "Declines the second to hand over the strike.";
                    }
                }
            } else {
                if (delivery.runs % 2 != 0) {
                    delivery.runs--;
                    delivery.commentary = "Refuses the single to keep the better batter on strike next over.";
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  TEAM FIELDING & KEEPER
    // ═══════════════════════════════════════════════════════════════════════════

    public double calculateTeamFielding(MatchLineup bowlingLineup) {
        double sum = 0; int count = 0;
        for (LineupPlayer lp : bowlingLineup.getPlayers()) { sum += lp.getPlayer().getFldRating(); count++; }
        return count > 0 ? sum / count : 30;
    }

    public double getKeeperRating(MatchLineup bowlingLineup) {
        Player keeper = bowlingLineup.getKeeper();
        if (keeper != null) return keeper.getKeeperRating();
        return bowlingLineup.getPlayers().stream()
                .mapToDouble(lp -> lp.getPlayer().getKeeperRating()).max().orElse(20);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  FIX: FALLBACK BOWLER SELECTION — rotates among top bowlers, not always #1
    // ═══════════════════════════════════════════════════════════════════════════

    public Player pickFallbackBowler(SimContext ctx, Map<UUID, Integer> bowlerBallCounts,
                                       Player previousBowler, int overNumber) {
        int maxBalls = ctx.maxPerBowler * 6;
        List<LineupPlayer> candidates = new ArrayList<>();
        for (LineupPlayer lp : ctx.bowlingLineup.getPlayers()) {
            Player p   = lp.getPlayer();
            if (previousBowler != null && p.getId().equals(previousBowler.getId())) continue;
            int bowled = bowlerBallCounts.getOrDefault(p.getId(), 0);
            if (bowled < maxBalls && p.getBowlRating() >= 15) candidates.add(lp);
        }
        if (candidates.isEmpty()) {
            for (LineupPlayer lp : ctx.bowlingLineup.getPlayers()) {
                Player p = lp.getPlayer();
                if (previousBowler != null && p.getId().equals(previousBowler.getId())) continue;
                if (bowlerBallCounts.getOrDefault(p.getId(), 0) < maxBalls) candidates.add(lp);
            }
        }
        if (candidates.isEmpty()) return ctx.bowlingLineup.getPlayers().get(0).getPlayer();

        // FIX: weighted random selection — higher rated bowlers get more picks but not exclusively
        // Sort by rating descending; use overNumber as variety seed to rotate
        candidates.sort((a, b) -> Double.compare(b.getPlayer().getBowlRating(), a.getPlayer().getBowlRating()));
        int topN = Math.min(3, candidates.size()); // consider top 3
        // Rotate among top-N using a simple weighted pick: top bowler 50%, 2nd 30%, 3rd 20%
        double[] weights = {0.50, 0.30, 0.20};
        double roll = ctx.rng.nextDouble();
        double cum  = 0;
        for (int i = 0; i < topN; i++) {
            cum += weights[i];
            if (roll < cum) return candidates.get(i).getPlayer();
        }
        return candidates.get(0).getPlayer();
    }

}
