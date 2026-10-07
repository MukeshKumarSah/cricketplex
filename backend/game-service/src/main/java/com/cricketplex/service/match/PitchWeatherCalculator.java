package com.cricketplex.service.match;

public final class PitchWeatherCalculator {
    private PitchWeatherCalculator() {}

    public static double getPositionalWicketMultiplier(int battingPosition) {
        // FIXED: reduced tail multipliers — 2.10× at pos 10/11 combined with
        // pWicket cap 0.28 was causing every tail ball to be near-fatal,
        // making innings-1 tail batting far worse than innings-2 (which starts
        // chasing with top order). Calibrated to real dismissal rate distributions.
        return switch (battingPosition) {
            case 1, 2    -> 0.85;   // openers — very hard to dismiss, experienced
            case 3       -> 0.81;   // #3 — usually the best batter
            case 4, 5    -> 0.90;   // solid middle order
            case 6, 7    -> 1.00;   // lower middle / all-rounders
            case 8       -> 1.18;   // first tailender — was 1.35
            case 9       -> 1.30;   // genuine tail — was 1.65
            case 10, 11  -> 1.50;   // last two — was 2.10 (way too high)
            default      -> 1.00;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  FIX: DYNAMIC FIELD PLACEMENT
    //  Captain adjusts field based on phase and match situation.
    //  Returns [p4Mod, p6Mod, pWicketMod]
    // ═══════════════════════════════════════════════════════════════════════════

    public static double[] getDynamicFieldPlacement(SimContext ctx, int overNumber,
                                               int totalRuns, int totalWickets) {
        // In powerplay: handled by getPowerplayBoost; no additional effect here
        boolean inMandatoryPP = ("T20".equalsIgnoreCase(ctx.format) && overNumber <= 6)
                || ("ODI".equalsIgnoreCase(ctx.format) && overNumber <= 10);
        if (inMandatoryPP) return new double[]{0, 0, 0};

        int wicketsLeft = 10 - totalWickets;
        double completedOvers = overNumber / (double) ctx.maxOvers;

        // Attacking field: more slips, more catchers → higher pWicket, lower p4
        // Defensive field: spread field → lower pWicket, higher p4
        boolean isDeathOvers = ("T20".equalsIgnoreCase(ctx.format) && overNumber > 16)
                || ("ODI".equalsIgnoreCase(ctx.format) && overNumber > 43);

        if (isDeathOvers) {
            // Defensive spread field in death — batter can hit freely but fewer catches
            return new double[]{0.015, 0.010, -0.008};
        }

        if (wicketsLeft <= 3) {
            // Tail: attacking field — more cordon, short leg
            return new double[]{-0.010, -0.008, 0.020};
        }

        if (ctx.isChasing) {
            int runsNeeded = Math.max(0, ctx.target - totalRuns);
            int ballsLeft  = Math.max(1, (ctx.maxOvers - overNumber) * 6);
            double rrr     = runsNeeded * 6.0 / ballsLeft;
            double par     = getPitchAwareParRate(ctx.format, ctx.pitchType);
            if (rrr > par * 1.3) {
                // Chasing hard — batter opening up, fielding captain sets attacking cordon
                return new double[]{0.005, 0.005, 0.010};
            }
            if (rrr < par * 0.7) {
                // Chase very comfortable — fielding captain spreads to prevent easy singles
                return new double[]{0.010, 0.000, -0.005};
            }
        }

        // Middle overs default: mild attacking field (2-3 catchers)
        if (completedOvers > 0.25 && completedOvers < 0.75) {
            return new double[]{-0.005, -0.003, 0.008};
        }

        return new double[]{0, 0, 0};
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  FIX: INTRA-INNINGS PITCH WEAR (ODI/T20)
    //  After over 25 in ODI / over 13 in T20, rough develops from footmarks.
    //  Spin benefits; pace loses conventional swing aid.
    // ═══════════════════════════════════════════════════════════════════════════

    public static double getIntraPitchWear(String format, int overNumber, String bowlType) {
        if ("FC".equalsIgnoreCase(format)) return 0; // FC handled by pitch decay already
        boolean isSpin = bowlType != null && MatchFormatRules.SPIN_TYPES.contains(bowlType);
        boolean isPace = bowlType != null && MatchFormatRules.PACE_TYPES.contains(bowlType);

        int wearThreshold = "T20".equalsIgnoreCase(format) ? 13 : 25;
        if (overNumber <= wearThreshold) return 0;

        double wearProgress = Math.min(1.0,
                (overNumber - wearThreshold) / (double)(MatchFormatRules.getMaxOvers(format) - wearThreshold));

        if (isSpin)  return  2.5 * wearProgress;  // rough helps spin grip
        if (isPace)  return -1.0 * wearProgress;  // footmarks disturb run-up line slightly
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  POWERPLAY FIELD RESTRICTION BOOSTS
    // ═══════════════════════════════════════════════════════════════════════════

    public static double[] getPowerplayBoost(String format, int overNumber) {
        if ("T20".equalsIgnoreCase(format)) {
            if (overNumber <= 6) return new double[]{0.055, 0.025, 0.04, 0.005};
        } else if ("ODI".equalsIgnoreCase(format)) {
            if (overNumber <= 10) return new double[]{0.045, 0.015, 0.03, 0.004};
            // PP2 and PP3 handled by captain-call in simulateInnings; no auto-boost here
        }
        return new double[]{0, 0, 0, 0};
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PITCH DECAY CURVE
    // ═══════════════════════════════════════════════════════════════════════════

    public static double getPitchDecayFactor(String pitchType, int overNumber, int maxOvers) {
        double progress = Math.min(1.0, (double) overNumber / maxOvers);
        return switch (pitchType != null ? pitchType : "STANDARD") {
            case "GREEN"    -> Math.max(0.20, 1.0 - (progress * 1.30));
            case "DUSTY"    -> Math.min(1.15, 0.35 + (progress * 1.10));
            case "DRY"      -> Math.min(1.15, 0.45 + (progress * 0.95));
            case "BOUNCY"   -> Math.max(0.50, 1.0 - (progress * 0.55));
            case "SLOW"     -> Math.min(1.05, 0.55 + (progress * 0.65));
            case "UNEVEN"   -> Math.max(0.70, 1.0 - (progress * 0.30));
            default         -> 1.0;
        };
    }

    public static double[] getPitchPhaseDirectBoost(String pitchType, int overNumber,
                                               int maxOvers, String bowlType) {
        double progress  = Math.min(1.0, (double) overNumber / maxOvers);
        boolean isPace   = bowlType != null && MatchFormatRules.PACE_TYPES.contains(bowlType);
        boolean isSpin   = bowlType != null && MatchFormatRules.SPIN_TYPES.contains(bowlType);

        return switch (pitchType != null ? pitchType : "STANDARD") {
            case "GREEN" -> {
                if (isPace && progress < 0.35) {
                    double intensity = 1.0 - (progress / 0.35);
                    yield new double[]{0.07 * intensity, 0.018 * intensity};
                }
                yield new double[]{0, 0};
            }
            case "DUSTY" -> {
                if (isSpin && progress > 0.35) {
                    double intensity = Math.min(1.0, (progress - 0.35) / 0.65);
                    yield new double[]{0.06 * intensity, 0.020 * intensity};
                }
                yield new double[]{0, 0};
            }
            case "DRY" -> {
                if (isSpin && progress > 0.30) {
                    double intensity = Math.min(1.0, (progress - 0.30) / 0.70);
                    yield new double[]{0.05 * intensity, 0.015 * intensity};
                }
                yield new double[]{0, 0};
            }
            case "FLAT"    -> new double[]{-0.04, -0.008};
            case "BOUNCY"  -> {
                if (isPace && progress < 0.50) {
                    double intensity = 1.0 - (progress / 0.50);
                    yield new double[]{0.04 * intensity, 0.012 * intensity};
                }
                yield new double[]{0, 0};
            }
            default -> new double[]{0, 0};
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PITCH AWARE PAR RATE
    // ═══════════════════════════════════════════════════════════════════════════

    public static double getPitchAwareParRate(String format, String pitchType) {
        double baseRate = "T20".equalsIgnoreCase(format) ? 8.5
                : "FC".equalsIgnoreCase(format) ? 3.0 : 5.6;
        double pitchMult = switch (pitchType != null ? pitchType : "STANDARD") {
            case "FLAT"   -> 1.15;
            case "GREEN"  -> 0.82;
            case "BOUNCY" -> 0.85;
            case "DUSTY"  -> 0.88;
            case "DRY"    -> 0.90;
            case "UNEVEN" -> 0.88;
            case "SLOW"   -> 0.95;
            default       -> 1.00;
        };
        return baseRate * pitchMult;
    }

    public static PitchEffect getPitchEffect(String pitchType, String bowlType) {
        String type = bowlType != null ? bowlType : "NONE";
        double bowlerBonus = switch (pitchType != null ? pitchType : "STANDARD") {
            case "GREEN" -> switch (type) {
                case "F", "LAP" -> 14; case "M" -> 12; case "MF" -> 10;
                case "FM" -> 9; case "FS" -> 2; case "WS" -> 1; default -> 6;
            };
            case "DUSTY" -> switch (type) {
                case "FS" -> 14; case "WS" -> 12; case "M" -> 5; case "MF" -> 4;
                case "FM" -> 3; case "F", "LAP" -> 2; default -> 5;
            };
            case "FLAT" -> switch (type) {
                case "F", "LAP" -> -5; case "FM" -> -6; case "MF" -> -6;
                case "M" -> -7; case "FS" -> -5; case "WS" -> -4; default -> -6;
            };
            case "BOUNCY" -> switch (type) {
                case "F", "LAP" -> 12; case "FM" -> 8; case "MF" -> 7;
                case "M" -> 4; case "FS" -> 1; case "WS" -> 0; default -> 5;
            };
            case "UNEVEN" -> switch (type) {
                case "F", "LAP" -> 10; case "MF" -> 9; case "FM" -> 8;
                case "M" -> 6; case "WS" -> 7; case "FS" -> 6; default -> 7;
            };
            case "DRY" -> switch (type) {
                case "WS" -> 12; case "FS" -> 10; case "M" -> 5; case "MF" -> 4;
                case "FM" -> 2; case "F", "LAP" -> 1; default -> 4;
            };
            case "SLOW" -> switch (type) {
                case "FS" -> 7; case "WS" -> 5; case "M" -> 2; case "MF" -> 0;
                case "FM" -> -2; case "F", "LAP" -> -4; default -> 1;
            };
            default -> 0;
        };
        return new PitchEffect(bowlerBonus);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  WEATHER EFFECTS
    // ═══════════════════════════════════════════════════════════════════════════

    public static WeatherEffect getWeatherEffect(String condition, int temperature, String bowlType) {
        String type = bowlType != null ? bowlType : "NONE";
        double battingMod = 0, bowlingMod = 0;
        switch (condition != null ? condition : "Sunny") {
            case "Sunny" -> {
                battingMod = 3;
                bowlingMod = switch (type) {
                    case "F","LAP" -> -3; case "FM" -> -2; case "MF" -> -1;
                    case "M" -> -1; case "FS" -> 0; case "WS" -> 0; default -> -1;
                };
            }
            case "Hot & Humid" -> {
                battingMod = 1;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 2; case "FM" -> 1; case "MF" -> 0;
                    case "M" -> -1; case "FS" -> 2; case "WS" -> 1; default -> 0;
                };
            }
            case "Partly Cloudy" -> {
                battingMod = 1;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 3; case "FM" -> 2; case "MF" -> 2;
                    case "M" -> 1; case "FS" -> 1; case "WS" -> 0; default -> 1;
                };
            }
            case "Overcast" -> {
                battingMod = -3;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 10; case "FM" -> 7; case "MF" -> 6;
                    case "M" -> 5; case "FS" -> 1; case "WS" -> 0; default -> 4;
                };
            }
            case "Light Rain" -> {
                battingMod = -4;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 8; case "FM" -> 6; case "MF" -> 5;
                    case "M" -> 4; case "FS" -> 0; case "WS" -> -1; default -> 3;
                };
            }
            case "Heavy Rain" -> {
                battingMod = -6;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 5; case "FM" -> 4; case "MF" -> 3;
                    case "M" -> 2; case "FS" -> -2; case "WS" -> -3; default -> 2;
                };
            }
            case "Windy" -> {
                battingMod = -1;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 4; case "FM" -> 3; case "MF" -> 2;
                    case "M" -> 1; case "FS" -> 2; case "WS" -> 3; default -> 2;
                };
            }
            case "Foggy" -> {
                battingMod = -5;
                bowlingMod = switch (type) {
                    case "F","LAP" -> 4; case "FM" -> 3; case "MF" -> 2;
                    case "M" -> 1; case "FS" -> 3; case "WS" -> 3; default -> 2;
                };
            }
        }
        if (temperature > 38) { battingMod -= 2; bowlingMod -= 1; }
        else if (temperature < 12) { bowlingMod -= 1; }
        return new WeatherEffect(battingMod, bowlingMod);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BATTER PITCH MODIFIER (with DUSTY/DRY adaptability decay)
    // ═══════════════════════════════════════════════════════════════════════════

    public static double getBatterPitchModifier(String pitchType, String batHand,
                                           int batRating, int experience,
                                           int overNumber, int maxOvers) {
        boolean isLH      = "LH".equals(batHand);
        double skillAdapt = batRating >= 60 ? 2.0 : (batRating >= 40 ? 1.0 : 0);
        double expAdapt   = experience >= 15 ? 2.0 : (experience >= 8 ? 1.0 : -1.0);
        double progress   = Math.min(1.0, (double) overNumber / maxOvers);

        return switch (pitchType != null ? pitchType : "STANDARD") {
            case "GREEN"   -> (isLH ? 3 : 0) + skillAdapt * 0.5 + expAdapt * 0.5;
            case "DUSTY"   -> {
                double decay = Math.max(0.0, 1.0 - (progress * 1.2));
                yield ((isLH ? -4 : -1) + skillAdapt + expAdapt * 0.5) * decay;
            }
            case "FLAT"    -> 4 + skillAdapt * 1.5;
            case "BOUNCY"  -> (isLH ? -2 : -1) + skillAdapt + expAdapt;
            case "UNEVEN"  -> -2 + skillAdapt + expAdapt;
            case "DRY"     -> {
                double decay = Math.max(0.0, 1.0 - (progress * 1.0));
                yield ((isLH ? -3 : 0) + skillAdapt * 0.8 + expAdapt * 0.5) * decay;
            }
            case "SLOW"    -> 2 + skillAdapt;
            default        -> 0;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BATTER WEATHER MODIFIER
    // ═══════════════════════════════════════════════════════════════════════════

    public static double getBatterWeatherModifier(String condition, int temperature,
                                             int experience, int batRating) {
        double expFactor   = experience >= 15 ? 2.0 : (experience >= 8 ? 0.5 : -1.5);
        double skillFactor = batRating >= 50  ? 1.0 : (batRating >= 30 ? 0 : -1.0);
        return switch (condition != null ? condition : "Sunny") {
            case "Sunny","Hot & Humid","Partly Cloudy" -> 0;
            case "Overcast"   -> -1 + expFactor * 0.8 + skillFactor * 0.5;
            case "Light Rain" -> -2 + expFactor + skillFactor * 0.5;
            case "Heavy Rain" -> -3 + expFactor * 1.2 + skillFactor * 0.5;
            case "Windy"      -> -1 + skillFactor + expFactor * 0.3;
            case "Foggy"      -> -2 + expFactor + skillFactor * 0.3;
            default           -> 0;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BOWLER TYPE MATCHUP
    // ═══════════════════════════════════════════════════════════════════════════

    public static double getBowlerTypeMatchup(String bowlType, String batHand, String pitchType) {
        if (bowlType == null) return 0;
        double bonus = 0;
        if ("LAP".equals(bowlType) && "RH".equals(batHand)) bonus += 4;
        if ("LAP".equals(bowlType) && "LH".equals(batHand)) bonus -= 1;
        if ("LH".equals(batHand)) {
            if ("FS".equals(bowlType)) bonus += 3;
            if ("WS".equals(bowlType)) bonus -= 1;
        }
        if (("F".equals(bowlType) || "FM".equals(bowlType)) &&
                ("GREEN".equals(pitchType) || "BOUNCY".equals(pitchType))) bonus += 3;
        if (("FS".equals(bowlType) || "WS".equals(bowlType)) &&
                ("DUSTY".equals(pitchType) || "DRY".equals(pitchType))) bonus += 4;
        if ("M".equals(bowlType) || "MF".equals(bowlType)) bonus -= 1;
        return bonus;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PHASE MODIFIER
    // ═══════════════════════════════════════════════════════════════════════════

    public static double getPhaseModifier(String format, int overNumber, int maxOvers) {
        if ("T20".equalsIgnoreCase(format)) {
            if (overNumber <= 6)  return 1.0;
            if (overNumber <= 15) return -0.5;
            return 4.0;
        } else if ("ODI".equalsIgnoreCase(format)) {
            if (overNumber <= 10) return 0.5;
            if (overNumber <= 30) return 0;
            if (overNumber <= 40) return 0.5;
            return 2.5;
        } else if ("FC".equalsIgnoreCase(format)) {
            int sessionOver = ((overNumber - 1) % 50) + 1;
            if (sessionOver <= 10)  return 0.8;
            if (sessionOver <= 30)  return -1.0;
            if (sessionOver <= 45)  return 0;
            return 1.0;
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BALL CONDITION MODIFIER
    // ═══════════════════════════════════════════════════════════════════════════

    public static double getBallConditionModifier(String format, int overNumber,
                                             String bowlType, String condition) {
        String type    = bowlType != null ? bowlType : "M";
        boolean isPace = MatchFormatRules.PACE_TYPES.contains(type);
        boolean isSpin = MatchFormatRules.SPIN_TYPES.contains(type);

        // FIX: use modular ball age for FC (new ball taken every 80 overs)
        int ballAgeOvers = "FC".equalsIgnoreCase(format)
                ? ((overNumber - 1) % 80) + 1 : overNumber;

        boolean isNewBall, isOldBall;
        if ("T20".equalsIgnoreCase(format)) {
            isNewBall = ballAgeOvers <= 4;
            isOldBall = ballAgeOvers >= 15;
        } else if ("ODI".equalsIgnoreCase(format)) {
            isNewBall = ballAgeOvers <= 10;
            isOldBall = ballAgeOvers >= 35;
        } else {
            isNewBall = ballAgeOvers <= 15;
            isOldBall = ballAgeOvers >= 60;
        }

        if (isNewBall) {
            if (isPace) return 4.0;
            if (isSpin) return -3.0;
        } else if (isOldBall) {
            if (isSpin) return 5.0;
            if (isPace) {
                if (("Hot & Humid".equals(condition) || "Sunny".equals(condition))
                        && (type.equals("F") || type.equals("FM") || type.equals("LAP")))
                    return 3.0;
                return -3.0;
            }
        }
        return 0.0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  AGGRESSION & SET-BATSMAN HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    public static double getAggressionModifier(String aggression) {
        return switch (aggression != null ? aggression : "N") {
            case "A" ->  1.5;
            case "D" -> -1.0;
            default  ->  0.0;
        };
    }

    public static double getSetBatsmanFactor(String format, int ballsFaced, String aggression) {
        int balls = Math.max(0, ballsFaced);
        double set;
        if ("T20".equalsIgnoreCase(format)) {
            if (balls <= 6)       set = -0.10;
            else if (balls <= 15) set = -0.10 + 0.25 * (balls - 6)  / 9.0;
            else if (balls <= 30) set = 0.15  + 0.20 * (balls - 15) / 15.0;
            else                  set = 0.35  + 0.10 * (1.0 - Math.exp(-(balls - 30) / 16.0));
        } else if ("FC".equalsIgnoreCase(format)) {
            if (balls <= 15)       set = -0.15;
            else if (balls <= 50)  set = -0.15 + 0.30 * (balls - 15)  / 35.0;
            else if (balls <= 120) set = 0.10  + 0.25 * (balls - 50)  / 70.0;
            else                   set = 0.35  + 0.12 * (1.0 - Math.exp(-(balls - 120) / 55.0));
        } else { // ODI
            if (balls <= 10)       set = -0.12;
            else if (balls <= 30)  set = -0.12 + 0.28 * (balls - 10) / 20.0;
            else if (balls <= 50)  set = 0.16  + 0.22 * (balls - 30) / 20.0;
            else if (balls <= 90)  set = 0.38  + 0.18 * (balls - 50) / 40.0;
            else if (balls <= 120) set = 0.56  + 0.10 * (balls - 90) / 30.0;
            else                   set = 0.66  + 0.04 * (1.0 - Math.exp(-(balls - 120) / 45.0));
        }
        if ("A".equals(aggression))      set *= 1.06;
        else if ("D".equals(aggression)) set *= 0.94;
        return Math.max(-0.18, Math.min(set, 0.88));
    }

}
