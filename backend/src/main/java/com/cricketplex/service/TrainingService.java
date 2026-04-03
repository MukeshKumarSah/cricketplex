package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainingService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final TrainingAssignmentRepository trainingAssignmentRepository;
    private final TrainingLogRepository trainingLogRepository;

    /**
     * Run training for all teams. Called by scheduler (e.g., daily).
     * Focused training: full improvement.
     * General training: ~40% of focused.
     */
    @Transactional
    public void runTrainingForTeam(Team team) {
        List<Player> players = playerRepository.findByTeam(team);
        List<TrainingAssignment> assignments = trainingAssignmentRepository.findByTeamId(team.getId());

        // Build assignment map
        Map<UUID, String> assignmentMap = new HashMap<>();
        for (TrainingAssignment ta : assignments) {
            assignmentMap.put(ta.getPlayer().getId(), ta.getTrainingType());
        }

        Random rng = new Random();

        for (Player p : players) {
            String focusedType = assignmentMap.get(p.getId());
            if (focusedType != null) {
                applyFocusedTraining(team, p, focusedType, rng);
            } else {
                applyGeneralTraining(team, p, rng);
            }
        }

        playerRepository.saveAll(players);
    }

    private void applyFocusedTraining(Team team, Player p, String type, Random rng) {
        // Focused: 1-3 points per skill
        switch (type) {
            case "BAT" -> {
                trainSkill(team, p, type, "batRating", p.getBatRating(), rng, 1, 3, v -> p.setBatRating(v));
                trainSkill(team, p, type, "stamina", p.getStamina(), rng, 0, 2, v -> p.setStamina(v));
                trainSkill(team, p, type, "confidence", p.getConfidence(), rng, 0, 2, v -> p.setConfidence(v));
            }
            case "BOWL" -> {
                trainSkill(team, p, type, "bowlRating", p.getBowlRating(), rng, 1, 3, v -> p.setBowlRating(v));
                trainSkill(team, p, type, "confidence", p.getConfidence(), rng, 0, 2, v -> p.setConfidence(v));
                trainSkill(team, p, type, "stamina", p.getStamina(), rng, 0, 2, v -> p.setStamina(v));
            }
            case "AR" -> {
                trainSkill(team, p, type, "batRating", p.getBatRating(), rng, 1, 2, v -> p.setBatRating(v));
                trainSkill(team, p, type, "bowlRating", p.getBowlRating(), rng, 1, 2, v -> p.setBowlRating(v));
                trainSkill(team, p, type, "confidence", p.getConfidence(), rng, 0, 2, v -> p.setConfidence(v));
                trainSkill(team, p, type, "stamina", p.getStamina(), rng, 0, 1, v -> p.setStamina(v));
            }
            case "FLD" -> {
                trainSkill(team, p, type, "fldRating", p.getFldRating(), rng, 1, 3, v -> p.setFldRating(v));
                trainSkill(team, p, type, "stamina", p.getStamina(), rng, 0, 2, v -> p.setStamina(v));
                trainSkill(team, p, type, "confidence", p.getConfidence(), rng, 0, 2, v -> p.setConfidence(v));
            }
            case "WK" -> {
                trainSkill(team, p, type, "keeperRating", p.getKeeperRating(), rng, 1, 3, v -> p.setKeeperRating(v));
                trainSkill(team, p, type, "stamina", p.getStamina(), rng, 0, 2, v -> p.setStamina(v));
                trainSkill(team, p, type, "confidence", p.getConfidence(), rng, 0, 2, v -> p.setConfidence(v));
            }
            case "STAMINA" -> {
                trainSkill(team, p, type, "stamina", p.getStamina(), rng, 1, 3, v -> p.setStamina(v));
                trainSkill(team, p, type, "confidence", p.getConfidence(), rng, 0, 2, v -> p.setConfidence(v));
            }
            case "MENTAL" -> {
                trainSkill(team, p, type, "confidence", p.getConfidence(), rng, 1, 3, v -> p.setConfidence(v));
            }
        }
    }

    private void applyGeneralTraining(Team team, Player p, Random rng) {
        // General: ~40% of focused (0-1 pts per skill, role-based)
        String type = "GENERAL";
        switch (p.getRole()) {
            case "BATSMAN" -> {
                trainSkill(team, p, type, "batRating", p.getBatRating(), rng, 0, 1, v -> p.setBatRating(v));
                trainSkill(team, p, type, "fldRating", p.getFldRating(), rng, 0, 1, v -> p.setFldRating(v));
                if (rng.nextDouble() < 0.3)
                    trainSkill(team, p, type, "bowlRating", p.getBowlRating(), rng, 0, 1, v -> p.setBowlRating(v));
                trainSkill(team, p, type, "stamina", p.getStamina(), rng, 0, 1, v -> p.setStamina(v));
            }
            case "BOWLER" -> {
                trainSkill(team, p, type, "bowlRating", p.getBowlRating(), rng, 0, 1, v -> p.setBowlRating(v));
                trainSkill(team, p, type, "fldRating", p.getFldRating(), rng, 0, 1, v -> p.setFldRating(v));
                if (rng.nextDouble() < 0.3)
                    trainSkill(team, p, type, "batRating", p.getBatRating(), rng, 0, 1, v -> p.setBatRating(v));
                trainSkill(team, p, type, "stamina", p.getStamina(), rng, 0, 1, v -> p.setStamina(v));
            }
            case "ALL_ROUNDER" -> {
                trainSkill(team, p, type, "batRating", p.getBatRating(), rng, 0, 1, v -> p.setBatRating(v));
                trainSkill(team, p, type, "bowlRating", p.getBowlRating(), rng, 0, 1, v -> p.setBowlRating(v));
                trainSkill(team, p, type, "fldRating", p.getFldRating(), rng, 0, 1, v -> p.setFldRating(v));
                trainSkill(team, p, type, "stamina", p.getStamina(), rng, 0, 1, v -> p.setStamina(v));
            }
            case "KEEPER" -> {
                trainSkill(team, p, type, "keeperRating", p.getKeeperRating(), rng, 0, 1, v -> p.setKeeperRating(v));
                trainSkill(team, p, type, "batRating", p.getBatRating(), rng, 0, 1, v -> p.setBatRating(v));
                trainSkill(team, p, type, "fldRating", p.getFldRating(), rng, 0, 1, v -> p.setFldRating(v));
                trainSkill(team, p, type, "stamina", p.getStamina(), rng, 0, 1, v -> p.setStamina(v));
            }
        }
    }

    private void trainSkill(Team team, Player p, String trainingType, String skill,
                            int currentVal, Random rng, int minGain, int maxGain,
                            java.util.function.IntConsumer setter) {
        int gain = minGain + rng.nextInt(maxGain - minGain + 1);
        if (gain == 0) return;

        int newVal = Math.min(currentVal + gain, 100);
        int change = newVal - currentVal;
        if (change == 0) return;

        setter.accept(newVal);

        trainingLogRepository.save(TrainingLog.builder()
                .team(team)
                .player(p)
                .trainingType(trainingType)
                .skill(skill)
                .oldValue(currentVal)
                .newValue(newVal)
                .change(change)
                .build());
    }
}
