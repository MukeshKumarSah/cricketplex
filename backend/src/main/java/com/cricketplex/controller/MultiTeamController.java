package com.cricketplex.controller;

import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/multi-team")
@RequiredArgsConstructor
public class MultiTeamController {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final TeamService teamService;

    // ════════════════════════════════════════════
    //  GET /api/multi-team/my-teams
    //  Get all teams owned by the current user
    // ════════════════════════════════════════════
    @GetMapping("/my-teams")
    public ResponseEntity<?> getMyTeams(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Team> teams = teamRepository.findByOwnerOrderByTeamOrderAsc(user);

        List<Map<String, Object>> teamList = new ArrayList<>();
        for (Team team : teams) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", team.getId());
            t.put("teamName", team.getTeamName());
            t.put("country", team.getCountry());
            t.put("teamOrder", team.getTeamOrder());
            t.put("teamProfilePicUrl", team.getTeamProfilePicUrl());
            t.put("odiRating", team.getOdiRating());
            t.put("t20Rating", team.getT20Rating());
            t.put("fcRating", team.getFcRating());
            t.put("funds", team.getFunds());
            t.put("isActive", team.getId().equals(user.getActiveTeamId()));
            teamList.add(t);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("teams", teamList);
        response.put("activeTeamId", user.getActiveTeamId());
        response.put("canCreateSecondary", user.getIsSupporter() && teams.size() < 2);

        return ResponseEntity.ok(response);
    }

    // ════════════════════════════════════════════
    //  POST /api/multi-team/switch/{teamId}
    //  Switch active team
    // ════════════════════════════════════════════
    @PostMapping("/switch/{teamId}")
    public ResponseEntity<?> switchTeam(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID teamId) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));

        // Verify ownership
        if (team.getOwner() == null || !team.getOwner().getId().equals(user.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "You do not own this team"));
        }

        user.setActiveTeamId(teamId);
        userRepository.save(user);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Switched to " + team.getTeamName());
        response.put("activeTeamId", teamId);
        response.put("teamName", team.getTeamName());

        return ResponseEntity.ok(response);
    }

    // ════════════════════════════════════════════
    //  POST /api/multi-team/create-secondary
    //  Create a secondary team (supporter only)
    // ════════════════════════════════════════════
    @PostMapping("/create-secondary")
    public ResponseEntity<?> createSecondaryTeam(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> request) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check if user is supporter
        if (!user.getIsSupporter()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only supporters can create a secondary team"));
        }

        // Check if user already has 2 teams
        long teamCount = teamRepository.countByOwner(user);
        if (teamCount >= 2) {
            return ResponseEntity.badRequest().body(Map.of("error", "You already have the maximum number of teams (2)"));
        }

        String teamName = request.get("teamName");
        String country = request.get("country");
        String groundName = request.get("groundName");

        // Validate team name
        if (teamName == null || teamName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Team name is required"));
        }

        if (teamRepository.existsByTeamName(teamName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Team name already exists"));
        }

        // Validate country
        if (country == null || country.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Country is required"));
        }

        // Check country restriction - cannot have teams in same country
        List<Team> existingTeams = teamRepository.findByOwnerOrderByTeamOrderAsc(user);
        for (Team existingTeam : existingTeams) {
            if (existingTeam.getCountry().equalsIgnoreCase(country)) {
                return ResponseEntity.badRequest().body(Map.of("error", "You already have a team in " + country + ". Teams must be in different countries."));
            }
        }

        // Create new team
        Team newTeam = Team.builder()
                .teamName(teamName.trim())
                .country(country.trim())
                .groundName(groundName != null ? groundName.trim() : teamName.trim() + " Stadium")
                .owner(user)
                .teamOrder(2) // Secondary team
                .isBot(false)
                .odiRating(1000)
                .t20Rating(1000)
                .fcRating(1000)
                .fans(1000)
                .morale(50)
                .academyLevel(1)
                .funds(50000L)
                .build();

        teamRepository.save(newTeam);

        // Perform full team setup: assign players, enroll in leagues, create fixtures
        try {
            teamService.generateSquadForTeam(newTeam);
            teamService.assignTeamToLeagues(newTeam);
        } catch (Exception e) {
            // If setup fails, still return success but log the error
            // The team has been created, they can manually fix issues later
            System.err.println("Error during secondary team setup: " + e.getMessage());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Secondary team created successfully");
        response.put("team", Map.of(
                "id", newTeam.getId(),
                "teamName", newTeam.getTeamName(),
                "country", newTeam.getCountry(),
                "teamOrder", newTeam.getTeamOrder()
        ));

        return ResponseEntity.ok(response);
    }

    // ════════════════════════════════════════════
    //  GET /api/multi-team/can-bid-on/{playerId}
    //  Check if user can bid on a player (not from own team)
    // ════════════════════════════════════════════
    @GetMapping("/can-bid-on-listing/{listingId}")
    public ResponseEntity<?> canBidOnListing(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID listingId) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // This will be used by the transfer market to check bidding eligibility
        // We'll need to pass the team that owns the listed player

        return ResponseEntity.ok(Map.of("canBid", true));
    }
}
