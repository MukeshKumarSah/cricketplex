package com.cricketplex.controller;

import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.CupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/cup")
@RequiredArgsConstructor
public class CupController {

    private final CupService     cupService;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    /** Current season's cup bracket (or the latest that exists). */
    @GetMapping("/current")
    public ResponseEntity<?> current() {
        int season = cupService.computeCurrentSeason();
        Map<String, Object> result = cupService.getCupBracket(season);
        if (Boolean.FALSE.equals(result.get("exists"))) {
            // Try the previous season if cup not yet initialized for this season
            if (season > 1) {
                result = cupService.getCupBracket(season - 1);
            }
        }
        return ResponseEntity.ok(result);
    }

    /** Cup bracket for a specific season. */
    @GetMapping("/{season}")
    public ResponseEntity<?> bySeason(@PathVariable int season) {
        return ResponseEntity.ok(cupService.getCupBracket(season));
    }

    /** Authenticated user's cup status for the current season. */
    @GetMapping("/my-status")
    public ResponseEntity<?> myStatus(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId()).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        Optional<Team> teamOpt = teamRepository.findByOwner(user);
        if (teamOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("inCup", false));
        }

        int season = cupService.computeCurrentSeason();
        Optional<Map<String, Object>> status = cupService.getMyStatus(season, teamOpt.get().getId());
        return status
                .map(s -> ResponseEntity.ok(Map.of("inCup", true, "status", s)))
                .orElseGet(() -> ResponseEntity.ok(Map.of("inCup", false)));
    }

    // ── Admin endpoints ──────────────────────────────────────────────────────

    /** Admin: manually initialize the cup for the current (or specified) season. */
    @PostMapping("/admin/initialize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> initialize(@RequestParam(required = false) Integer season) {
        int s = (season != null) ? season : cupService.computeCurrentSeason();
        return ResponseEntity.ok(cupService.initializeCupForSeason(s));
    }

    /** Admin: manually trigger round advancement check for a cup. */
    @PostMapping("/admin/advance/{cupId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> advance(@PathVariable UUID cupId) {
        cupService.checkAndAdvanceCupRound(cupId);
        return ResponseEntity.ok(Map.of("triggered", true));
    }
}
