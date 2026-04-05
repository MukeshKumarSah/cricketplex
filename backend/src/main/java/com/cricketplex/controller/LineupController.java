package com.cricketplex.controller;

import com.cricketplex.entity.Fixture;
import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.ActivityLogService;
import com.cricketplex.service.LineupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class LineupController {

    private final LineupService lineupService;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final FixtureRepository fixtureRepository;
    private final ActivityLogService activityLogService;

    @GetMapping("/{fixtureId}/lineup")
    public ResponseEntity<?> getLineupData(
            @PathVariable UUID fixtureId,
            @AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(lineupService.getLineupData(fixtureId, user));
    }

    @PostMapping("/{fixtureId}/lineup")
    public ResponseEntity<?> saveLineup(
            @PathVariable UUID fixtureId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Object result = lineupService.saveLineup(fixtureId, user, request);
        teamRepository.findByOwner(user).ifPresent(team -> {
            String opponent = "";
            Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
            if (fixture != null) {
                Team opp = fixture.getHomeTeam().getId().equals(team.getId())
                        ? fixture.getAwayTeam() : fixture.getHomeTeam();
                opponent = " against " + opp.getTeamName();
            }
            activityLogService.log(team, "lineup", "Lineup set for upcoming match" + opponent + ".");
        });
        return ResponseEntity.ok(result);
    }

    @GetMapping("/default-lineup")
    public ResponseEntity<?> getDefaultLineup(
            @AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(lineupService.getDefaultLineup(user));
    }
}
