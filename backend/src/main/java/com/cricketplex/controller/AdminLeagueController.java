package com.cricketplex.controller;

import com.cricketplex.service.BotTeamService;
import com.cricketplex.service.FitnessRecoveryService;
import com.cricketplex.service.LeagueService;
import com.cricketplex.service.LeagueSyncService;
import com.cricketplex.service.PlayerAgingService;
import com.cricketplex.service.SeasonalUpdateService;
import com.cricketplex.service.TrainingService;
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
    private final PlayerAgingService playerAgingService;
    private final FitnessRecoveryService fitnessRecoveryService;
    private final TrainingService trainingService;
    private final SeasonalUpdateService seasonalUpdateService;
    private final LeagueSyncService leagueSyncService;

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

    @PostMapping("/trigger-aging")
    public ResponseEntity<?> triggerAging() {
        playerAgingService.applyMissedDays();
        return ResponseEntity.ok(Map.of("message", "Player aging catch-up complete"));
    }

    @PostMapping("/trigger-fitness")
    public ResponseEntity<?> triggerFitness() {
        fitnessRecoveryService.applyMissedDays();
        return ResponseEntity.ok(Map.of("message", "Fitness recovery catch-up complete"));
    }

    @PostMapping("/trigger-training")
    public ResponseEntity<?> triggerTraining() {
        trainingService.applyMissedDays();
        return ResponseEntity.ok(Map.of("message", "Training catch-up complete"));
    }

    @PostMapping("/trigger-seasonal")
    public ResponseEntity<?> triggerSeasonal(
            @RequestParam(required = false) Integer season) {
        if (season != null) {
            try {
                return ResponseEntity.ok(seasonalUpdateService.forceSeasonalUpdate(season));
            } catch (IllegalStateException e) {
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
        }
        seasonalUpdateService.applyMissedUpdates();
        return ResponseEntity.ok(Map.of("message", "Seasonal update catch-up complete"));
    }

    /**
     * Fix orphaned team swaps for a specific league — corrects standings for
     * teams that joined while a match was IN_PROGRESS.
     * 
     * POST /api/admin/leagues/sync?leagueId=<uuid>&season=<int>
     */
    @PostMapping("/sync")
    public ResponseEntity<?> syncLeague(
            @RequestParam UUID leagueId,
            @RequestParam(required = false) Integer season) {
        if (season == null) {
            season = 1;
        }
        return ResponseEntity.ok(leagueSyncService.fixOrphanedSwaps(leagueId, season));
    }

    /**
     * Fix orphaned swaps for all leagues of a country+format.
     * 
     * POST /api/admin/leagues/sync-country?country=<string>&format=<string>
     */
    @PostMapping("/sync-country")
    public ResponseEntity<?> syncCountryLeagues(
            @RequestParam String country,
            @RequestParam String format) {
        return ResponseEntity.ok(leagueSyncService.syncCountryLeagues(country, format));
    }
}
