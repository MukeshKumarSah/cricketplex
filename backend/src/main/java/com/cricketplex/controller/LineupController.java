package com.cricketplex.controller;

import com.cricketplex.entity.User;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
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
        return ResponseEntity.ok(lineupService.saveLineup(fixtureId, user, request));
    }
}
