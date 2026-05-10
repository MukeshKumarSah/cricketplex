package com.cricketplex.controller;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@RestController
@RequestMapping("/api/academy")
@RequiredArgsConstructor
public class AcademyController {

    private static final String[] COUNTRIES = {
        "Afghanistan", "Australia", "Bangladesh", "England", "India",
        "Ireland", "Nepal", "Netherlands", "New Zealand", "Oman",
        "Pakistan", "Scotland", "South Africa", "Sri Lanka",
        "United Arab Emirates", "United States", "West Indies", "Zimbabwe"
    };

    private static final int[] FOCUSED_SPOTS = {0, 3, 5, 7, 10}; // index = academy level

    private static final String[] AGGRESSIONS = {"D", "N", "A"};
    private static final String[] PART_TIME_BOWL_TYPES = {"M", "MF", "FS"};

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final PlayerFirstNameRepository firstNameRepo;
    private final PlayerLastNameRepository lastNameRepo;
    private final AcademyPullRepository academyPullRepository;
    private final TrainingAssignmentRepository trainingAssignmentRepository;
    private final TrainingLogRepository trainingLogRepository;
    private final ActivityLogService activityLogService;
    private final TransactionLogRepository transactionLogRepository;

    // ════════════════════════════════════════════
    //  GET /api/academy — overview
    // ════════════════════════════════════════════
    @GetMapping
    public ResponseEntity<?> getAcademyOverview(@AuthenticationPrincipal UserPrincipal principal) {
        Team team = getTeam(principal);
        int level = team.getAcademyLevel();
        int maxSpots = FOCUSED_SPOTS[level];

        List<TrainingAssignment> assignments = trainingAssignmentRepository.findByTeamId(team.getId());
        List<Player> players = playerRepository.findByTeam(team);

        // Build assignment map
        Map<UUID, String> assignmentMap = new LinkedHashMap<>();
        for (TrainingAssignment ta : assignments) {
            assignmentMap.put(ta.getPlayer().getId(), ta.getTrainingType());
        }

        // Player list with assignment info
        List<Map<String, Object>> playerList = new ArrayList<>();
        for (Player p : players) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getFirstName() + " " + p.getLastName());
            m.put("role", p.getRole());
            m.put("age", p.getAge());
            m.put("ageDays", p.getAgeDays());
            m.put("batRating", p.getBatRating());
            m.put("bowlRating", p.getBowlRating());
            m.put("keeperRating", p.getKeeperRating());
            m.put("fldRating", p.getFldRating());
            m.put("stamina", p.getStamina());
            m.put("confidence", p.getConfidence());
            m.put("trainingType", assignmentMap.getOrDefault(p.getId(), null));
            playerList.add(m);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("academyLevel", level);
        resp.put("maxFocusedSpots", maxSpots);
        resp.put("usedFocusedSpots", assignments.size());
        resp.put("funds", team.getFunds());
        resp.put("players", playerList);
        return ResponseEntity.ok(resp);
    }

    // ════════════════════════════════════════════
    //  POST /api/academy/upgrade — upgrade academy level
    // ════════════════════════════════════════════
    @PostMapping("/upgrade")
    @Transactional
    public ResponseEntity<?> upgradeAcademy(@AuthenticationPrincipal UserPrincipal principal) {
        Team team = getTeam(principal);
        int current = team.getAcademyLevel();
        if (current >= 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "Academy is already at max level (4)"));
        }
        int cost = getUpgradeCost(current + 1);
        if (team.getFunds() < cost) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Insufficient funds. Upgrade costs $" + String.format("%,d", cost)
                            + " but you only have $" + String.format("%,d", team.getFunds())));
        }
        team.setFunds(team.getFunds() - cost);
        team.setAcademyLevel(current + 1);
        teamRepository.save(team);
        transactionLogRepository.save(TransactionLog.builder()
                .team(team).type("ACADEMY_UPGRADE")
                .description("Academy upgraded to Level " + (current + 1))
                .amount((long) -cost).balanceAfter(team.getFunds())
                .build());
        activityLogService.log(team, "academy",
                "Academy upgraded to Level " + (current + 1) + " for $" + String.format("%,d", cost) + ".");
        return ResponseEntity.ok(Map.of(
                "message", "Academy upgraded to Level " + (current + 1) + " (−$" + String.format("%,d", cost) + ")",
                "academyLevel", current + 1,
                "maxFocusedSpots", FOCUSED_SPOTS[current + 1],
                "funds", team.getFunds()
        ));
    }

    // ════════════════════════════════════════════
    //  POST /api/academy/downgrade — downgrade academy level
    // ════════════════════════════════════════════
    @PostMapping("/downgrade")
    @Transactional
    public ResponseEntity<?> downgradeAcademy(@AuthenticationPrincipal UserPrincipal principal) {
        Team team = getTeam(principal);
        int current = team.getAcademyLevel();
        if (current <= 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Academy is already at minimum level (1)"));
        }
        int newLevel = current - 1;
        int newMax = FOCUSED_SPOTS[newLevel];

        // Remove excess training assignments if downgrading reduces slots
        long used = trainingAssignmentRepository.countByTeamId(team.getId());
        if (used > newMax) {
            List<TrainingAssignment> assignments = trainingAssignmentRepository.findByTeamId(team.getId());
            int toRemove = (int) (used - newMax);
            for (int i = assignments.size() - 1; i >= 0 && toRemove > 0; i--) {
                trainingAssignmentRepository.delete(assignments.get(i));
                toRemove--;
            }
        }

        int refund = getDowngradeRefund(current);
        team.setFunds(team.getFunds() + refund);
        team.setAcademyLevel(newLevel);
        teamRepository.save(team);
        transactionLogRepository.save(TransactionLog.builder()
                .team(team).type("ACADEMY_DOWNGRADE")
                .description("Academy downgraded to Level " + newLevel + " (refund)")
                .amount((long) refund).balanceAfter(team.getFunds())
                .build());
        activityLogService.log(team, "academy",
                "Academy downgraded to Level " + newLevel + ". Refunded $" + String.format("%,d", refund) + ".");
        return ResponseEntity.ok(Map.of(
                "message", "Academy downgraded to Level " + newLevel + " (+$" + String.format("%,d", refund) + " refund)",
                "academyLevel", newLevel,
                "maxFocusedSpots", FOCUSED_SPOTS[newLevel],
                "funds", team.getFunds()
        ));
    }

    private int getUpgradeCost(int targetLevel) {
        return switch (targetLevel) {
            case 2 -> 40000;
            case 3 -> 100000;
            case 4 -> 200000;
            default -> 0;
        };
    }

    private int getDowngradeRefund(int fromLevel) {
        return switch (fromLevel) {
            case 4 -> 150000;
            case 3 -> 75000;
            case 2 -> 30000;
            default -> 0;
        };
    }

    // ════════════════════════════════════════════
    //  GET /api/academy/pull-status — weekly pull availability
    // ════════════════════════════════════════════
    @GetMapping("/pull-status")
    public ResponseEntity<?> getPullStatus(@AuthenticationPrincipal UserPrincipal principal) {
        Team team = getTeam(principal);
        LocalDateTime windowStart = getWeeklyPullWindowStart();
        boolean alreadyPulled = academyPullRepository.existsByTeamIdAndPulledAtAfter(team.getId(), windowStart);
        LocalDateTime nextWindow = getNextPullWindowStart();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("canPull", !alreadyPulled);
        resp.put("windowStart", windowStart.toString());
        resp.put("nextWindow", nextWindow.toString());
        return ResponseEntity.ok(resp);
    }

    // ════════════════════════════════════════════
    //  POST /api/academy/pull — pull a new player
    // ════════════════════════════════════════════
    @PostMapping("/pull")
    @Transactional
    public ResponseEntity<?> pullPlayer(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {

        String role = body.get("role");
        if (role == null || !List.of("BATSMAN", "BOWLER", "ALL_ROUNDER", "KEEPER").contains(role)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role"));
        }

        Team team = getTeam(principal);

        // Weekly pull limit: 1 pull per week, resets Sunday 1:00 AM UTC
        LocalDateTime windowStart = getWeeklyPullWindowStart();
        if (academyPullRepository.existsByTeamIdAndPulledAtAfter(team.getId(), windowStart)) {
            LocalDateTime nextWindow = getNextPullWindowStart();
            return ResponseEntity.badRequest().body(Map.of("error",
                    "You have already used your weekly pull. Next pull available on Sunday 1:00 AM UTC.",
                    "nextWindow", nextWindow.toString()));
        }

        Random rng = new Random();

        // Pick country: 15% home, 5% each other
        String pulledCountry = pickCountry(team.getCountry(), rng);

        // Generate 17-year-old player
        Player player = generateYouthPlayer(team, role, pulledCountry, rng);
        player = playerRepository.save(player);

        // Record pull with stat snapshot
        AcademyPull pull = AcademyPull.builder()
                .team(team)
                .player(player)
                .requestedRole(role)
                .pulledFromCountry(pulledCountry)
                .snapshotRole(player.getRole())
                .snapshotAge(player.getAge())
                .snapshotAgeDays(player.getAgeDays())
                .snapshotBatRating(player.getBatRating())
                .snapshotBowlRating(player.getBowlRating())
                .snapshotKeeperRating(player.getKeeperRating())
                .snapshotFldRating(player.getFldRating())
                .snapshotStamina(player.getStamina())
                .snapshotConfidence(player.getConfidence())
                .snapshotExperience(player.getExperience())
                .build();
        academyPullRepository.save(pull);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("player", buildPlayerMap(player));
        resp.put("pulledFrom", pulledCountry);

        activityLogService.log(team, "academy", "Recruited " + player.getFirstName() + " " + player.getLastName() + " (" + role + ") from " + pulledCountry + " academy.");

        return ResponseEntity.ok(resp);
    }

    // ════════════════════════════════════════════
    //  GET /api/academy/pull-history
    // ════════════════════════════════════════════
    @GetMapping("/pull-history")
    public ResponseEntity<?> getPullHistory(@AuthenticationPrincipal UserPrincipal principal) {
        Team team = getTeam(principal);
        List<AcademyPull> pulls = academyPullRepository.findByTeamIdOrderByPulledAtDesc(team.getId());

        List<Map<String, Object>> list = new ArrayList<>();
        for (AcademyPull p : pulls) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            // Use snapshot if available, fall back to live data for old pulls
            if (p.getSnapshotBatRating() != null) {
                Map<String, Object> snap = new LinkedHashMap<>();
                snap.put("id", p.getPlayer().getId());
                snap.put("name", p.getPlayer().getFirstName() + " " + p.getPlayer().getLastName());
                snap.put("country", p.getPlayer().getCountry());
                snap.put("role", p.getSnapshotRole());
                snap.put("age", p.getSnapshotAge());
                snap.put("ageDays", p.getSnapshotAgeDays());
                snap.put("batRating", p.getSnapshotBatRating());
                snap.put("bowlRating", p.getSnapshotBowlRating());
                snap.put("keeperRating", p.getSnapshotKeeperRating());
                snap.put("fldRating", p.getSnapshotFldRating());
                snap.put("stamina", p.getSnapshotStamina());
                snap.put("confidence", p.getSnapshotConfidence());
                snap.put("experience", p.getSnapshotExperience());
                m.put("player", snap);
            } else {
                m.put("player", buildPlayerMap(p.getPlayer()));
            }
            m.put("requestedRole", p.getRequestedRole());
            m.put("pulledFrom", p.getPulledFromCountry());
            m.put("pulledAt", p.getPulledAt() != null ? p.getPulledAt().toString() : null);
            list.add(m);
        }
        return ResponseEntity.ok(list);
    }

    // ════════════════════════════════════════════
    //  POST /api/academy/training — assign focused training
    // ════════════════════════════════════════════
    @PostMapping("/training")
    @Transactional
    public ResponseEntity<?> assignTraining(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {

        Team team = getTeam(principal);
        UUID playerId = UUID.fromString(body.get("playerId"));
        String type = body.get("trainingType"); // BAT, BOWL, AR, FLD, WK, STAMINA, MENTAL

        if (type == null || !List.of("BAT", "BOWL", "AR", "FLD", "WK", "STAMINA", "MENTAL").contains(type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid training type"));
        }

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));
        if (!player.getTeam().getId().equals(team.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not your player"));
        }

        int level = team.getAcademyLevel();
        int maxSpots = FOCUSED_SPOTS[level];

        // Check if already assigned — update
        Optional<TrainingAssignment> existing = trainingAssignmentRepository
                .findByTeamIdAndPlayerId(team.getId(), playerId);
        if (existing.isPresent()) {
            existing.get().setTrainingType(type);
            trainingAssignmentRepository.save(existing.get());
            return ResponseEntity.ok(Map.of("message", "Training updated"));
        }

        // Check slot limit
        long used = trainingAssignmentRepository.countByTeamId(team.getId());
        if (used >= maxSpots) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "All " + maxSpots + " focused training spots are taken. Remove one first or upgrade academy."));
        }

        TrainingAssignment ta = TrainingAssignment.builder()
                .team(team)
                .player(player)
                .trainingType(type)
                .build();
        trainingAssignmentRepository.save(ta);

        activityLogService.log(team, "training", "Assigned " + type + " training to " + player.getFirstName() + " " + player.getLastName() + ".");

        return ResponseEntity.ok(Map.of("message", "Training assigned"));
    }

    // ════════════════════════════════════════════
    //  DELETE /api/academy/training/{playerId} — remove focused training
    // ════════════════════════════════════════════
    @DeleteMapping("/training/{playerId}")
    @Transactional
    public ResponseEntity<?> removeTraining(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID playerId) {

        Team team = getTeam(principal);
        trainingAssignmentRepository.deleteByTeamIdAndPlayerId(team.getId(), playerId);
        return ResponseEntity.ok(Map.of("message", "Training removed"));
    }

    // ════════════════════════════════════════════
    //  GET /api/academy/training-history
    // ════════════════════════════════════════════
    @GetMapping("/training-history")
    public ResponseEntity<?> getTrainingHistory(@AuthenticationPrincipal UserPrincipal principal) {
        Team team = getTeam(principal);
        List<TrainingLog> logs = trainingLogRepository.findByTeamIdOrderByTrainedAtDesc(team.getId());

        List<Map<String, Object>> list = new ArrayList<>();
        for (TrainingLog log : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", log.getId());
            m.put("playerName", log.getPlayer().getFirstName() + " " + log.getPlayer().getLastName());
            m.put("playerId", log.getPlayer().getId());
            m.put("trainingType", log.getTrainingType());
            m.put("skill", log.getSkill());
            m.put("oldValue", log.getOldValue());
            m.put("newValue", log.getNewValue());
            m.put("change", log.getChange());
            m.put("trainedAt", log.getTrainedAt() != null ? log.getTrainedAt().toString() : null);
            list.add(m);
        }
        return ResponseEntity.ok(list);
    }

    // ════════════════════════════════════════════
    //  Private helpers
    // ════════════════════════════════════════════

    /** Returns the most recent Sunday 1:00 AM UTC (start of current pull window). */
    private LocalDateTime getWeeklyPullWindowStart() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDate today = now.toLocalDate();
        // Find the most recent Sunday
        LocalDate lastSunday = today.getDayOfWeek() == DayOfWeek.SUNDAY
                ? today : today.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY));
        LocalDateTime windowStart = lastSunday.atTime(1, 0);
        // If it's Sunday but before 1:00 AM, use previous Sunday
        if (now.isBefore(windowStart)) {
            windowStart = windowStart.minusWeeks(1);
        }
        return windowStart;
    }

    /** Returns the next Sunday 1:00 AM UTC (start of next pull window). */
    private LocalDateTime getNextPullWindowStart() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDate today = now.toLocalDate();
        LocalDate nextSunday = today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        LocalDateTime next = nextSunday.atTime(1, 0);
        // Edge case: it's Sunday before 1:00 AM — next window is today at 1:00 AM
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY && now.isBefore(today.atTime(1, 0))) {
            next = today.atTime(1, 0);
        }
        return next;
    }

    private Team getTeam(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("No team found"));
    }

    private String pickCountry(String homeCountry, Random rng) {
        // 15% home nation, 85% split among 17 others => 5% each
        double roll = rng.nextDouble();
        if (roll < 0.15) return homeCountry;

        List<String> others = new ArrayList<>();
        for (String c : COUNTRIES) {
            if (!c.equals(homeCountry)) others.add(c);
        }
        return others.get(rng.nextInt(others.size()));
    }

    private Player generateYouthPlayer(Team team, String role, String country, Random rng) {
        // Get name from country pool
        List<PlayerFirstName> firstNames = firstNameRepo.findByCountryIgnoreCase(country);
        List<PlayerLastName> lastNames = lastNameRepo.findByCountryIgnoreCase(country);

        // Fallback to any country if pool empty
        if (firstNames.isEmpty()) firstNames = firstNameRepo.findAll();
        if (lastNames.isEmpty()) lastNames = lastNameRepo.findAll();

        Set<String> globalUsed = playerRepository.findAllNameCombos();
        String fn, ln;
        int attempts = 0;
        do {
            fn = firstNames.get(rng.nextInt(firstNames.size())).getName();
            ln = lastNames.get(rng.nextInt(lastNames.size())).getName();
            attempts++;
        } while (globalUsed.contains(fn.toLowerCase() + "|" + ln.toLowerCase()) && attempts < 50);

        String batHand = rng.nextBoolean() ? "RH" : "LH";
        String bowlHand = rng.nextBoolean() ? "RH" : "LH";

        // Youth player: 17 years old, lower ratings
        int batR, bowlR, kpR = 0, fldR;
        String bowlType;

        switch (role) {
            case "BATSMAN" -> {
                batR = randInt(rng, 8, 18);
                bowlR = randInt(rng, 0, 8);
                fldR = randInt(rng, 5, 15);
                bowlType = randomPartTimeBowlType(rng);
            }
            case "BOWLER" -> {
                batR = randInt(rng, 0, 8);
                bowlR = randInt(rng, 8, 18);
                fldR = randInt(rng, 5, 15);
                bowlType = randomBowlType(rng);
            }
            case "ALL_ROUNDER" -> {
                batR = randInt(rng, 6, 14);
                bowlR = randInt(rng, 6, 14);
                fldR = randInt(rng, 5, 15);
                bowlType = randomBowlType(rng);
            }
            case "KEEPER" -> {
                batR = randInt(rng, 8, 16);
                bowlR = randInt(rng, 0, 5);
                kpR = randInt(rng, 8, 18);
                fldR = randInt(rng, 5, 15);
                bowlType = randomPartTimeBowlType(rng);
            }
            default -> {
                batR = 10; bowlR = 10; fldR = 10;
                bowlType = "M";
            }
        }

        return Player.builder()
                .firstName(fn).lastName(ln).country(country).team(team)
                .nationality(country)
                .role(role).age(17).batHand(batHand)
                .bowlHand(bowlHand).bowlType(bowlType)
                .batRating(batR).bowlRating(bowlR)
                .keeperRating(kpR).fldRating(fldR)
                .rating(calcOverallRating(role, batR, bowlR, kpR, fldR))
                .wage(randInt(rng, 100, 300))
                .confidence(randInt(rng, 30, 50))
                .experience(0).stamina(randInt(rng, 5, 15)).fitness(100)
                .batAggression(randomAggression(rng)).bowlAggression(randomAggression(rng))
                .build();
    }

    private Map<String, Object> buildPlayerMap(Player p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getFirstName() + " " + p.getLastName());
        m.put("country", p.getCountry());
        m.put("role", p.getRole());
        m.put("age", p.getAge());
        m.put("ageDays", p.getAgeDays());
        m.put("batRating", p.getBatRating());
        m.put("bowlRating", p.getBowlRating());
        m.put("keeperRating", p.getKeeperRating());
        m.put("fldRating", p.getFldRating());
        m.put("stamina", p.getStamina());
        m.put("confidence", p.getConfidence());
        m.put("experience", p.getExperience());
        return m;
    }

    private int randInt(Random rng, int min, int max) {
        return min + rng.nextInt(max - min + 1);
    }

    private String randomAggression(Random rng) {
        return AGGRESSIONS[rng.nextInt(AGGRESSIONS.length)];
    }

    private String randomPartTimeBowlType(Random rng) {
        return PART_TIME_BOWL_TYPES[rng.nextInt(PART_TIME_BOWL_TYPES.length)];
    }

    private String randomBowlType(Random rng) {
        String[] types = {"FS", "WS", "F", "M", "FM", "MF"};
        return types[rng.nextInt(types.length)];
    }

    private int calcOverallRating(String role, int bat, int bowl, int keeper, int fld) {
        return switch (role) {
            case "BATSMAN"     -> (int)(bat * 0.55 + bowl * 0.10 + fld * 0.35);
            case "BOWLER"      -> (int)(bat * 0.10 + bowl * 0.55 + fld * 0.35);
            case "ALL_ROUNDER" -> (int)(bat * 0.35 + bowl * 0.35 + fld * 0.30);
            case "KEEPER"      -> (int)(bat * 0.30 + bowl * 0.05 + keeper * 0.35 + fld * 0.30);
            default            -> (bat + bowl + fld) / 3;
        };
    }
}
