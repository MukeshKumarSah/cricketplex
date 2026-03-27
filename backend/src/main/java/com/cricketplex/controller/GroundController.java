package com.cricketplex.controller;

import com.cricketplex.entity.User;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.GroundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ground")
@RequiredArgsConstructor
public class GroundController {

    private final GroundService groundService;
    private final UserRepository userRepository;

    // ── Stadium Seats ──

    @GetMapping("/seats")
    public ResponseEntity<?> getSeats(@AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        return ResponseEntity.ok(groundService.getSeats(user));
    }

    @PutMapping("/seats")
    public ResponseEntity<?> updateSeats(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestBody Map<String, Integer> request) {
        User user = getUser(principal);
        return ResponseEntity.ok(groundService.updateSeats(user, request));
    }

    // ── Home Matches & Pitch ──

    @GetMapping("/matches")
    public ResponseEntity<?> getUpcomingMatches(@AuthenticationPrincipal UserPrincipal principal) {
        User user = getUser(principal);
        return ResponseEntity.ok(groundService.getUpcomingHomeMatches(user));
    }

    @PutMapping("/matches/{matchId}/pitch")
    public ResponseEntity<?> updateMatchPitch(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable UUID matchId,
                                              @RequestBody Map<String, String> request) {
        User user = getUser(principal);
        String pitchType = request.get("pitchType");
        if (pitchType == null || pitchType.isBlank()) {
            throw new IllegalArgumentException("pitchType is required");
        }
        return ResponseEntity.ok(groundService.updateMatchPitch(user, matchId, pitchType));
    }

    private User getUser(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
