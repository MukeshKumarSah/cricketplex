package com.cricketplex.controller;

import com.cricketplex.entity.League;
import com.cricketplex.entity.LeagueTeam;
import com.cricketplex.entity.Team;
import com.cricketplex.repository.LeagueRepository;
import com.cricketplex.repository.LeagueTeamRepository;
import com.cricketplex.service.FixtureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/leagues")
@RequiredArgsConstructor
public class LeagueController {

    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final FixtureService fixtureService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getLeagueDetail(@PathVariable UUID id) {
        Optional<League> opt = leagueRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        League league = opt.get();
        List<LeagueTeam> leagueTeams = leagueTeamRepository.findByLeagueId(id);

        List<Map<String, Object>> standings = new ArrayList<>();
        int pos = 1;
        for (LeagueTeam lt : leagueTeams) {
            Team team = lt.getTeam();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("position", pos++);
            row.put("teamId", team.getId());
            row.put("teamName", team.getTeamName());
            row.put("teamProfilePicUrl", team.getTeamProfilePicUrl());
            row.put("isBot", team.getIsBot());
            row.put("played", 0);
            row.put("won", 0);
            row.put("lost", 0);
            row.put("tied", 0);
            row.put("points", 0);
            row.put("nrr", 0.0);
            standings.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", league.getId());
        result.put("country", league.getCountry());
        result.put("format", league.getFormat());
        result.put("division", league.getDivision());
        result.put("leagueNumber", league.getLeagueNumber());
        result.put("leagueId", league.getDivision() + "." + league.getLeagueNumber());
        result.put("season", league.getSeason());
        result.put("totalTeams", leagueTeams.size());
        result.put("standings", standings);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/fixtures")
    public ResponseEntity<?> getLeagueFixtures(@PathVariable UUID id) {
        Optional<League> opt = leagueRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        League league = opt.get();
        List<Map<String, Object>> fixtures = fixtureService.getFixturesGroupedByRound(league);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("leagueId", league.getDivision() + "." + league.getLeagueNumber());
        result.put("format", league.getFormat());
        result.put("totalRounds", fixtures.size());
        result.put("rounds", fixtures);
        return ResponseEntity.ok(result);
    }
}
