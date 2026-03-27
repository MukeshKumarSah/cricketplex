package com.cricketplex.controller;

import com.cricketplex.service.BotTeamService;
import com.cricketplex.service.LeagueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/leagues")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLeagueController {

    private final LeagueService leagueService;
    private final BotTeamService botTeamService;

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(leagueService.getLeagueStats());
    }

    @GetMapping("/{country}")
    public ResponseEntity<?> getByCountry(
            @PathVariable String country,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) Integer season) {
        return ResponseEntity.ok(leagueService.getLeaguesByCountry(country, format, season));
    }

    @PostMapping
    public ResponseEntity<?> createLeague(@RequestBody Map<String, Object> body) {
        String country = (String) body.get("country");
        String format = (String) body.get("format");
        int division = ((Number) body.get("division")).intValue();
        return ResponseEntity.ok(leagueService.createLeague(country, format, division));
    }

    @PostMapping("/generate-bots")
    public ResponseEntity<?> generateBotTeams() {
        return ResponseEntity.ok(botTeamService.generateBotTeams());
    }

    @GetMapping("/bot-stats")
    public ResponseEntity<?> getBotStats() {
        return ResponseEntity.ok(botTeamService.getBotStats());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLeague(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(leagueService.deleteLeague(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
