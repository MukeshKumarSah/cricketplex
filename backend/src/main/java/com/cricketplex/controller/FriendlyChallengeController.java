package com.cricketplex.controller;

import com.cricketplex.entity.*;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.FriendlyChallengeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/challenges")
@RequiredArgsConstructor
public class FriendlyChallengeController {

    private final FriendlyChallengeService challengeService;
    private final UserRepository userRepository;

    // ─── Get all challenges for my team ────────────────────────

    @GetMapping
    public ResponseEntity<?> getChallenges(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(challengeService.getChallenges(user));
    }

    // ─── Get teams available to challenge ──────────────────────

    @GetMapping("/teams")
    public ResponseEntity<?> getChallengeable(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(challengeService.getChallengeable(user));
    }

    // ─── Send a challenge ──────────────────────────────────────

    @PostMapping("/send")
    public ResponseEntity<?> sendChallenge(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UUID opponentTeamId = UUID.fromString((String) body.get("opponentTeamId"));
        String format = (String) body.get("format");
        String pitchType = (String) body.getOrDefault("pitchType", "STANDARD");
        LocalDate matchDate = LocalDate.parse((String) body.get("matchDate"));
        String matchTime = (String) body.get("matchTime");
        String message = (String) body.get("message");

        try {
            FriendlyChallenge challenge = challengeService.sendChallenge(
                    user, opponentTeamId, format, pitchType, matchDate, matchTime, message);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "challengeId", challenge.getId(),
                    "message", "Challenge sent successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── Accept a challenge ────────────────────────────────────

    @PostMapping("/{id}/accept")
    public ResponseEntity<?> acceptChallenge(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        try {
            FriendlyChallenge challenge = challengeService.acceptChallenge(user, id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "fixtureId", challenge.getFixture().getId(),
                    "message", "Challenge accepted! Set your lineup."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── Decline a challenge ───────────────────────────────────

    @PostMapping("/{id}/decline")
    public ResponseEntity<?> declineChallenge(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        try {
            challengeService.declineChallenge(user, id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Challenge declined"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── Cancel a challenge (by sender) ────────────────────────

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelChallenge(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        try {
            challengeService.cancelChallenge(user, id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Challenge cancelled"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ─── Simulate (play) the friendly match ────────────────────

    @PostMapping("/{id}/simulate")
    public ResponseEntity<?> simulateMatch(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        try {
            MatchResult result = challengeService.simulateFriendly(user, id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "fixtureId", result.getFixture().getId(),
                    "message", "Match simulated successfully!"
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
