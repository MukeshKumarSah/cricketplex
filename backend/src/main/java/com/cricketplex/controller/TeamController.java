package com.cricketplex.controller;

import com.cricketplex.dto.ApiResponse;
import com.cricketplex.dto.TeamSetupRequest;
import com.cricketplex.entity.League;
import com.cricketplex.entity.LeagueTeam;
import com.cricketplex.entity.Fixture;
import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.LeagueRepository;
import com.cricketplex.repository.LeagueTeamRepository;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.MatchLineupRepository;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.TeamService;
import com.cricketplex.service.FixtureService;
import com.cricketplex.service.WeatherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final FixtureService fixtureService;
    private final UserRepository userRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final TeamRepository teamRepository;
    private final FixtureRepository fixtureRepository;
    private final MatchLineupRepository matchLineupRepository;
    private final WeatherService weatherService;

    /**
     * Check whether a country still has available league slots for new teams.
     * Also returns the country's UTC match start time.
     */
    @GetMapping("/check-availability")
    public ResponseEntity<?> checkAvailability(@RequestParam String country) {
        boolean available = teamService.isCountryAvailable(country);
        String matchTime = FixtureService.getMatchStartTime(country);
        return ResponseEntity.ok(Map.of(
                "country", country,
                "available", available,
                "matchStartTimeUtc", matchTime
        ));
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setupTeam(@AuthenticationPrincipal UserPrincipal principal,
                                       @Valid @RequestBody TeamSetupRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Team team = teamService.setupTeam(user, request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Team created successfully",
                "team", Map.of(
                        "id", team.getId().toString(),
                        "teamName", team.getTeamName(),
                        "country", team.getCountry()
                )
        ));
    }

    @GetMapping("/current-season")
    public ResponseEntity<?> getCurrentSeason() {
        int season = leagueRepository.findMaxSeason();
        return ResponseEntity.ok(Map.of("season", season));
    }

    @GetMapping("/my-leagues")
    public ResponseEntity<?> getMyLeagues(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Optional<Team> teamOpt = teamRepository.findByOwner(user);
        if (teamOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("leagues", List.of()));
        }

        Team myTeam = teamOpt.get();
        List<LeagueTeam> myLeagueTeams = leagueTeamRepository.findByTeamId(myTeam.getId());

        // Auto-assign if team exists but has no league entries (legacy teams)
        if (myLeagueTeams.isEmpty()) {
            teamService.assignTeamToLeagues(myTeam);
            myLeagueTeams = leagueTeamRepository.findByTeamId(myTeam.getId());
        }

        List<Map<String, Object>> leagues = new ArrayList<>();
        for (LeagueTeam lt : myLeagueTeams) {
            League league = lt.getLeague();

            // Get all teams in this league to find position
            List<LeagueTeam> allInLeague = leagueTeamRepository.findByLeagueId(league.getId());

            // Sort by points desc, then NRR desc (for now all 0, so alphabetical)
            // When matches exist, this will sort by actual standings
            int position = 1;
            for (LeagueTeam entry : allInLeague) {
                if (entry.getTeam().getId().equals(myTeam.getId())) break;
                position++;
            }

            Map<String, Object> leagueInfo = new LinkedHashMap<>();
            leagueInfo.put("leagueId", league.getId());
            leagueInfo.put("leagueLabel", league.getDivision() + "." + league.getLeagueNumber());
            leagueInfo.put("country", league.getCountry());
            leagueInfo.put("format", league.getFormat());
            leagueInfo.put("division", league.getDivision());
            leagueInfo.put("leagueNumber", league.getLeagueNumber());
            leagueInfo.put("season", league.getSeason());
            leagueInfo.put("matchStartTimeUtc", league.getMatchStartTime());
            leagueInfo.put("position", position);
            leagueInfo.put("totalTeams", allInLeague.size());
            leagues.add(leagueInfo);
        }

        return ResponseEntity.ok(Map.of("leagues", leagues));
    }

    @GetMapping("/my-matches")
    public ResponseEntity<?> getMyMatches(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) String format) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Optional<Team> teamOpt = teamRepository.findByOwner(user);
        if (teamOpt.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Team myTeam = teamOpt.get();

        // Ensure fixtures exist for all user's leagues
        List<LeagueTeam> myLeagueTeams = leagueTeamRepository.findByTeamId(myTeam.getId());
        for (LeagueTeam lt : myLeagueTeams) {
            fixtureService.ensureFixturesExist(lt.getLeague());
        }

        List<Fixture> all = fixtureRepository.findAllByTeamOrderByMatchDate(myTeam);

        // Batch-load fixture IDs that have a saved lineup
        Set<UUID> linedUpFixtures = new HashSet<>(matchLineupRepository.findFixtureIdsByTeamId(myTeam.getId()));

        LocalDate today = LocalDate.now();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Fixture f : all) {
            League league = f.getLeague();
            boolean isFriendly = league == null;
            String fFormat = isFriendly ? f.getFormat() : league.getFormat();

            if (season != null && (isFriendly || !league.getSeason().equals(season))) continue;
            if (format != null && (fFormat == null || !fFormat.equalsIgnoreCase(format))) continue;

            boolean isHome = f.getHomeTeam().getId().equals(myTeam.getId());
            Team opponent = isHome ? f.getAwayTeam() : f.getHomeTeam();

            Map<String, Object> match = new LinkedHashMap<>();
            match.put("id", f.getId());
            match.put("matchDate", f.getMatchDate().toString());
            match.put("matchStartTimeUtc", isFriendly ? null : league.getMatchStartTime());
            match.put("format", fFormat);
            match.put("round", f.getRound());
            match.put("leagueLabel", isFriendly ? "Friendly" : league.getDivision() + "." + league.getLeagueNumber());
            match.put("matchType", isFriendly ? "FRIENDLY" : "LEAGUE");
            match.put("homeTeamName", f.getHomeTeam().getTeamName());
            match.put("homeTeamPicUrl", f.getHomeTeam().getTeamProfilePicUrl());
            match.put("homeIsBot", f.getHomeTeam().getIsBot());
            match.put("awayTeamName", f.getAwayTeam().getTeamName());
            match.put("awayTeamPicUrl", f.getAwayTeam().getTeamProfilePicUrl());
            match.put("awayIsBot", f.getAwayTeam().getIsBot());
            match.put("isHome", isHome);
            match.put("opponentName", opponent.getTeamName());
            match.put("opponentPicUrl", opponent.getTeamProfilePicUrl());
            match.put("opponentIsBot", opponent.getIsBot());
            match.put("status", f.getStatus());
            match.put("pitchType", f.getPitchType());
            match.put("season", isFriendly ? null : league.getSeason());
            match.put("lineupSet", linedUpFixtures.contains(f.getId()));
            match.put("weather", weatherService.getMatchWeather(
                    f.getHomeTeam().getCountry(), f.getMatchDate(), today));
            result.add(match);
        }

        return ResponseEntity.ok(result);
    }
}
