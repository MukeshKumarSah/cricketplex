package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Daily training at 00:45 UTC for all players.
 *
 * Formula per skill per session:
 *   gain = baseGain × focusMultiplier × ageFactor × fitnessFactor × skillDecay
 *
 *   baseGain       = 0.4 + (stamina / 100) × 0.25          (≈ 0.42 – 0.65)
 *   focusMultiplier = 2.5 (focused) or 1.0 (general)
 *   ageFactor       = smooth curve declining with age
 *   fitnessFactor   = fitness / 100                          (0.0 – 1.0)
 *   skillDecay      = ((100 - currentSkill) / 100) ^ 1.8    (smooth, no hard cap)
 *
 * After training, fitness is reduced:
 *   focused: -2 fitness, general: -1 fitness (min 0).
 *
 * Target outcomes (pulled player at 17, focused training, good fitness):
 *   ~75-80 primary skill by age 23.
 *   General training caps around 60-65.
 *   Skill >80 grows slowly, >85 very slowly (via skill decay curve).
 *
 * Uses app_state for resilience — catches up missed days on startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingService {

    private static final String KEY = "last_training_date";
    private static final double BASE_GAIN = 0.55;
    private static final double FOCUSED_MULTIPLIER = 2.5;
    private static final double GENERAL_MULTIPLIER = 1.0;
    private static final double SKILL_DECAY_EXPONENT = 2.0;
    private static final int FOCUSED_FITNESS_COST = 2;
    private static final int GENERAL_FITNESS_COST = 1;

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final TrainingAssignmentRepository trainingAssignmentRepository;
    private final TrainingLogRepository trainingLogRepository;
    private final AppStateRepository appStateRepository;

    @PostConstruct
    public void catchUpOnStartup() {
        log.info("Checking for missed training days...");
        applyMissedDays();
    }

    @Scheduled(cron = "0 45 0 * * *", zone = "UTC")
    public void scheduledTraining() {
        applyMissedDays();
    }

    @Transactional
    public void applyMissedDays() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        AppState state = appStateRepository.findById(KEY).orElse(null);

        LocalDate lastTrained;
        if (state == null) {
            state = new AppState(KEY, today.minusDays(1).toString());
            appStateRepository.save(state);
            lastTrained = today.minusDays(1);
        } else {
            lastTrained = LocalDate.parse(state.getValue());
        }

        long missedDays = ChronoUnit.DAYS.between(lastTrained, today);
        if (missedDays <= 0) {
            log.info("Training already up-to-date (last: {})", lastTrained);
            return;
        }
        if (missedDays > 60) {
            log.warn("Missed {} training days exceeds safety cap of 60 — capping", missedDays);
            missedDays = 60;
        }

        log.info("Applying {} missed training day(s) (last: {}, today: {})", missedDays, lastTrained, today);
        List<Team> allTeams = teamRepository.findAll();
        for (long d = 0; d < missedDays; d++) {
            for (Team team : allTeams) {
                runTrainingForTeam(team);
            }
            log.debug("Training day {}/{} complete for {} teams", d + 1, missedDays, allTeams.size());
        }

        state.setValue(today.toString());
        appStateRepository.save(state);
        log.info("Training complete — {} day(s) applied", missedDays);
    }

    private void runTrainingForTeam(Team team) {
        List<Player> players = playerRepository.findByTeam(team);
        List<TrainingAssignment> assignments = trainingAssignmentRepository.findByTeamId(team.getId());

        Map<UUID, String> assignmentMap = new HashMap<>();
        for (TrainingAssignment ta : assignments) {
            assignmentMap.put(ta.getPlayer().getId(), ta.getTrainingType());
        }

        Random rng = new Random();

        for (Player p : players) {
            String focusedType = assignmentMap.get(p.getId());
            if (focusedType != null) {
                applyFocusedTraining(team, p, focusedType, rng);
                p.setFitness(Math.max(0, p.getFitness() - FOCUSED_FITNESS_COST));
            } else {
                applyGeneralTraining(team, p, rng);
                p.setFitness(Math.max(0, p.getFitness() - GENERAL_FITNESS_COST));
            }
            // Recalc overall rating
            p.setRating(calcOverallRating(p));
        }

        playerRepository.saveAll(players);
    }

    // ── Age factor: maturation curve using exact age (years + days/56) ──
    // Youth players learn fundamentals slowly, peak training at 20-22,
    // then gradual decline. Value of youth = more years of training ahead.
    // 17→0.60, 18→0.73, 19→0.87, 20→1.00 (peak), 22→1.00,
    // 25→0.91, 30→0.71, 35→0.46, 39→0.20 (floor)
    private static final int DAYS_PER_SEASON = 56;

    private double ageFactor(Player p) {
        double exactAge = p.getAge() + (double) p.getAgeDays() / DAYS_PER_SEASON;
        if (exactAge <= 17.0) return 0.60;
        if (exactAge <= 20.0) return 0.60 + (exactAge - 17.0) * (0.40 / 3.0);  // 17→0.60, 20→1.00 maturation ramp
        if (exactAge <= 22.0) return 1.00;                                      // peak training years
        if (exactAge <= 25.0) return 1.00 - (exactAge - 22.0) * 0.03;          // 22→1.00, 25→0.91
        if (exactAge <= 30.0) return 0.91 - (exactAge - 25.0) * 0.04;          // 25→0.91, 30→0.71
        if (exactAge <= 35.0) return 0.71 - (exactAge - 30.0) * 0.05;          // 30→0.71, 35→0.46
        return Math.max(0.20, 0.46 - (exactAge - 35.0) * 0.065);              // 35→0.46, 39→0.20, floor
    }

    // ── Skill decay: smooth exponential slowdown ──
    private double skillDecay(int skill) {
        double room = (100.0 - skill) / 100.0;
        return Math.pow(Math.max(room, 0.0), SKILL_DECAY_EXPONENT);
    }

    // ── Core gain calculation ──
    // baseGain is flat — stamina doesn't affect learning ability.
    // Fitness has minimal impact: 80% floor, 100% ceiling.
    private double calcGain(Player p, int currentSkill, double multiplier) {
        double fit = 0.80 + (p.getFitness() / 100.0) * 0.20;  // range 0.80 – 1.00
        double age = ageFactor(p);
        double decay = skillDecay(currentSkill);
        return BASE_GAIN * multiplier * age * fit * decay;
    }

    private int applyGain(Team team, Player p, String trainingType, String skill,
                          int currentVal, double multiplier, Random rng) {
        double raw = calcGain(p, currentVal, multiplier);
        // Wide randomness: 0.4× to 1.8× (occasionally poor or great sessions)
        // Gaussian-ish: average of two randoms → bell-curved around 1.1×
        double r1 = 0.4 + rng.nextDouble() * 1.4;   // 0.4 – 1.8
        double r2 = 0.4 + rng.nextDouble() * 1.4;
        double randomFactor = (r1 + r2) / 2.0;       // avg → ~1.1, range 0.4 – 1.8
        raw *= randomFactor;
        // Accumulate fractionally — floor + probabilistic rounding
        int guaranteed = (int) raw;
        double frac = raw - guaranteed;
        int gain = guaranteed + (rng.nextDouble() < frac ? 1 : 0);
        if (gain <= 0) return currentVal;

        int newVal = Math.min(currentVal + gain, 100);
        int change = newVal - currentVal;
        if (change <= 0) return currentVal;

        trainingLogRepository.save(TrainingLog.builder()
                .team(team).player(p)
                .trainingType(trainingType).skill(skill)
                .oldValue(currentVal).newValue(newVal).change(change)
                .build());
        return newVal;
    }

    // ── Focused training ──
    private void applyFocusedTraining(Team team, Player p, String type, Random rng) {
        double fm = FOCUSED_MULTIPLIER;
        double sm = GENERAL_MULTIPLIER; // secondary skills at general rate
        switch (type) {
            case "BAT" -> {
                p.setBatRating(applyGain(team, p, type, "batRating", p.getBatRating(), fm, rng));
                p.setStamina(applyGain(team, p, type, "stamina", p.getStamina(), sm * 0.4, rng));
                p.setConfidence(applyGain(team, p, type, "confidence", p.getConfidence(), sm * 0.4, rng));
            }
            case "BOWL" -> {
                p.setBowlRating(applyGain(team, p, type, "bowlRating", p.getBowlRating(), fm, rng));
                p.setStamina(applyGain(team, p, type, "stamina", p.getStamina(), sm * 0.4, rng));
                p.setConfidence(applyGain(team, p, type, "confidence", p.getConfidence(), sm * 0.4, rng));
            }
            case "AR" -> {
                p.setBatRating(applyGain(team, p, type, "batRating", p.getBatRating(), fm * 0.6, rng));
                p.setBowlRating(applyGain(team, p, type, "bowlRating", p.getBowlRating(), fm * 0.6, rng));
                p.setConfidence(applyGain(team, p, type, "confidence", p.getConfidence(), sm * 0.4, rng));
                p.setStamina(applyGain(team, p, type, "stamina", p.getStamina(), sm * 0.3, rng));
            }
            case "FLD" -> {
                p.setFldRating(applyGain(team, p, type, "fldRating", p.getFldRating(), fm, rng));
                p.setStamina(applyGain(team, p, type, "stamina", p.getStamina(), sm * 0.4, rng));
                p.setConfidence(applyGain(team, p, type, "confidence", p.getConfidence(), sm * 0.4, rng));
            }
            case "WK" -> {
                p.setKeeperRating(applyGain(team, p, type, "keeperRating", p.getKeeperRating(), fm, rng));
                p.setStamina(applyGain(team, p, type, "stamina", p.getStamina(), sm * 0.4, rng));
                p.setConfidence(applyGain(team, p, type, "confidence", p.getConfidence(), sm * 0.4, rng));
            }
            case "STAMINA" -> {
                p.setStamina(applyGain(team, p, type, "stamina", p.getStamina(), fm, rng));
                p.setConfidence(applyGain(team, p, type, "confidence", p.getConfidence(), sm * 0.4, rng));
            }
            case "MENTAL" -> {
                p.setConfidence(applyGain(team, p, type, "confidence", p.getConfidence(), fm, rng));
            }
        }
    }

    // ── General training: role-based, all skills at general rate ──
    private void applyGeneralTraining(Team team, Player p, Random rng) {
        double gm = GENERAL_MULTIPLIER;
        String type = "GENERAL";
        switch (p.getRole()) {
            case "BATSMAN" -> {
                p.setBatRating(applyGain(team, p, type, "batRating", p.getBatRating(), gm, rng));
                p.setFldRating(applyGain(team, p, type, "fldRating", p.getFldRating(), gm * 0.6, rng));
                p.setStamina(applyGain(team, p, type, "stamina", p.getStamina(), gm * 0.4, rng));
            }
            case "BOWLER" -> {
                p.setBowlRating(applyGain(team, p, type, "bowlRating", p.getBowlRating(), gm, rng));
                p.setFldRating(applyGain(team, p, type, "fldRating", p.getFldRating(), gm * 0.6, rng));
                p.setStamina(applyGain(team, p, type, "stamina", p.getStamina(), gm * 0.4, rng));
            }
            case "ALL_ROUNDER" -> {
                p.setBatRating(applyGain(team, p, type, "batRating", p.getBatRating(), gm * 0.7, rng));
                p.setBowlRating(applyGain(team, p, type, "bowlRating", p.getBowlRating(), gm * 0.7, rng));
                p.setFldRating(applyGain(team, p, type, "fldRating", p.getFldRating(), gm * 0.5, rng));
                p.setStamina(applyGain(team, p, type, "stamina", p.getStamina(), gm * 0.3, rng));
            }
            case "KEEPER" -> {
                p.setKeeperRating(applyGain(team, p, type, "keeperRating", p.getKeeperRating(), gm, rng));
                p.setBatRating(applyGain(team, p, type, "batRating", p.getBatRating(), gm * 0.6, rng));
                p.setFldRating(applyGain(team, p, type, "fldRating", p.getFldRating(), gm * 0.5, rng));
                p.setStamina(applyGain(team, p, type, "stamina", p.getStamina(), gm * 0.3, rng));
            }
        }
    }

    private int calcOverallRating(Player p) {
        return switch (p.getRole()) {
            case "BATSMAN"     -> (int)(p.getBatRating() * 0.55 + p.getBowlRating() * 0.10 + p.getFldRating() * 0.35);
            case "BOWLER"      -> (int)(p.getBatRating() * 0.10 + p.getBowlRating() * 0.55 + p.getFldRating() * 0.35);
            case "ALL_ROUNDER" -> (int)(p.getBatRating() * 0.35 + p.getBowlRating() * 0.35 + p.getFldRating() * 0.30);
            case "KEEPER"      -> (int)(p.getBatRating() * 0.30 + p.getBowlRating() * 0.05 + p.getKeeperRating() * 0.35 + p.getFldRating() * 0.30);
            default            -> (p.getBatRating() + p.getBowlRating() + p.getFldRating()) / 3;
        };
    }
}
