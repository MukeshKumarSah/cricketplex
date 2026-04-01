package com.cricketplex.controller;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import com.cricketplex.service.TeamListService;
import com.cricketplex.service.FixtureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamListController {

    private final TeamListService teamListService;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final FixtureRepository fixtureRepository;
    private final MatchResultRepository matchResultRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final StadiumSeatsRepository stadiumSeatsRepository;

    @GetMapping
    public ResponseEntity<?> getAllTeams() {
        return ResponseEntity.ok(teamListService.getAllTeams());
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<?> getTeamProfile(@PathVariable UUID teamId) {
        Optional<Team> opt = teamRepository.findById(teamId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Team team = opt.get();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", team.getId());
        info.put("teamName", team.getTeamName());
        info.put("country", team.getCountry());
        info.put("groundName", team.getGroundName());
        info.put("teamProfilePicUrl", team.getTeamProfilePicUrl());
        info.put("odiRating", team.getOdiRating());
        info.put("t20Rating", team.getT20Rating());
        info.put("fcRating", team.getFcRating());
        info.put("fans", team.getFans());
        info.put("isBot", team.getIsBot());
        if (team.getOwner() != null) {
            info.put("managerName", team.getOwner().getName());
        }

        return ResponseEntity.ok(info);
    }

    // ── Squad for any team ──

    @GetMapping("/{teamId}/squad")
    public ResponseEntity<?> getTeamSquad(@PathVariable UUID teamId) {
        Optional<Team> opt = teamRepository.findById(teamId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Team team = opt.get();
        List<Player> players = playerRepository.findByTeam(team);

        List<Map<String, Object>> squad = players.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("firstName", p.getFirstName());
            m.put("lastName", p.getLastName());
            m.put("role", p.getRole());
            m.put("age", p.getAge());
            m.put("batHand", p.getBatHand());
            m.put("bowlHand", p.getBowlHand());
            m.put("bowlType", p.getBowlType());
            m.put("batRating", p.getBatRating());
            m.put("bowlRating", p.getBowlRating());
            m.put("keeperRating", p.getKeeperRating());
            m.put("fldRating", p.getFldRating());
            m.put("rating", p.getRating());
            m.put("nationality", p.getNationality());
            return m;
        }).toList();

        return ResponseEntity.ok(Map.of("teamName", team.getTeamName(), "players", squad));
    }

    // ── Matches for any team ──

    @GetMapping("/{teamId}/matches")
    public ResponseEntity<?> getTeamMatches(@PathVariable UUID teamId) {
        Optional<Team> opt = teamRepository.findById(teamId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Team team = opt.get();
        List<Fixture> all = fixtureRepository.findAllByTeamOrderByMatchDate(team);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Fixture f : all) {
            League league = f.getLeague();
            boolean isFriendly = league == null;

            boolean isHome = f.getHomeTeam().getId().equals(team.getId());
            Team opponent = isHome ? f.getAwayTeam() : f.getHomeTeam();

            Map<String, Object> match = new LinkedHashMap<>();
            match.put("id", f.getId());
            match.put("matchDate", f.getMatchDate().toString());
            match.put("matchStartTimeUtc", isFriendly ? null : league.getMatchStartTime());
            match.put("format", isFriendly ? f.getFormat() : league.getFormat());
            match.put("round", f.getRound());
            match.put("leagueLabel", isFriendly ? "Friendly" : league.getDivision() + "." + league.getLeagueNumber());
            match.put("matchType", isFriendly ? "FRIENDLY" : "LEAGUE");
            match.put("homeTeamId", f.getHomeTeam().getId());
            match.put("homeTeamName", f.getHomeTeam().getTeamName());
            match.put("homeTeamPicUrl", f.getHomeTeam().getTeamProfilePicUrl());
            match.put("awayTeamId", f.getAwayTeam().getId());
            match.put("awayTeamName", f.getAwayTeam().getTeamName());
            match.put("awayTeamPicUrl", f.getAwayTeam().getTeamProfilePicUrl());
            match.put("isHome", isHome);
            match.put("opponentName", opponent.getTeamName());
            match.put("opponentPicUrl", opponent.getTeamProfilePicUrl());
            match.put("opponentIsBot", opponent.getIsBot());
            match.put("status", f.getStatus());
            match.put("pitchType", f.getPitchType());

            // Include result summary for completed matches
            if ("COMPLETED".equals(f.getStatus())) {
                matchResultRepository.findByFixtureId(f.getId()).ifPresent(mr -> {
                    match.put("winnerId", mr.getWinner() != null ? mr.getWinner().getId() : null);
                    String summary = "";
                    if ("TIE".equals(mr.getResultType())) {
                        summary = "Match Tied";
                    } else if (mr.getWinner() != null && mr.getResultMargin() != null) {
                        String winnerName = mr.getWinner().getTeamName();
                        if ("RUNS".equals(mr.getResultType())) {
                            summary = winnerName + " won by " + mr.getResultMargin() + " runs";
                        } else if ("WICKETS".equals(mr.getResultType())) {
                            summary = winnerName + " won by " + mr.getResultMargin() + " wickets";
                        } else {
                            summary = winnerName + " won";
                        }
                    }
                    match.put("resultSummary", summary);
                });
            }
            result.add(match);
        }

        return ResponseEntity.ok(result);
    }

    // ── Leagues for any team ──

    @GetMapping("/{teamId}/leagues")
    public ResponseEntity<?> getTeamLeagues(@PathVariable UUID teamId) {
        Optional<Team> opt = teamRepository.findById(teamId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Team team = opt.get();
        List<LeagueTeam> leagueTeams = leagueTeamRepository.findByTeamId(teamId);

        List<Map<String, Object>> leagues = new ArrayList<>();
        for (LeagueTeam lt : leagueTeams) {
            League league = lt.getLeague();
            List<LeagueTeam> allInLeague = leagueTeamRepository.findByLeagueId(league.getId());

            // Compute standings to find position
            List<Fixture> fixtures = fixtureRepository.findByLeagueIdOrderByRoundAscMatchNumberAsc(league.getId());
            Map<UUID, int[]> stats = new LinkedHashMap<>(); // [points, wins]
            Map<UUID, double[]> nrrData = new LinkedHashMap<>();
            for (LeagueTeam entry : allInLeague) {
                stats.put(entry.getTeam().getId(), new int[2]);
                nrrData.put(entry.getTeam().getId(), new double[4]);
            }

            for (Fixture f : fixtures) {
                if (!"COMPLETED".equals(f.getStatus())) continue;
                matchResultRepository.findByFixtureId(f.getId()).ifPresent(mr -> {
                    UUID homeId = f.getHomeTeam().getId();
                    UUID awayId = f.getAwayTeam().getId();

                    for (Innings inn : mr.getInningsList()) {
                        UUID batId = inn.getBattingTeam().getId();
                        UUID bowlId = inn.getBowlingTeam().getId();
                        double overs = oversToDecimal(inn.getTotalOvers() != null ? inn.getTotalOvers() : 0.0);
                        int runs = inn.getTotalRuns() != null ? inn.getTotalRuns() : 0;
                        double[] batNrr = nrrData.get(batId);
                        double[] bowlNrr = nrrData.get(bowlId);
                        if (batNrr != null) { batNrr[0] += runs; batNrr[1] += overs; }
                        if (bowlNrr != null) { bowlNrr[2] += runs; bowlNrr[3] += overs; }
                    }

                    if ("TIE".equals(mr.getResultType())) {
                        stats.getOrDefault(homeId, new int[2])[0] += 1;
                        stats.getOrDefault(awayId, new int[2])[0] += 1;
                    } else if (mr.getWinner() != null) {
                        int[] ws = stats.get(mr.getWinner().getId());
                        if (ws != null) { ws[0] += 2; ws[1]++; }
                    }
                });
            }

            // Sort teams by Points → NRR → Wins
            List<UUID> sorted = new ArrayList<>(stats.keySet());
            sorted.sort((a, b) -> {
                int cmp = Integer.compare(stats.get(b)[0], stats.get(a)[0]);
                if (cmp != 0) return cmp;
                double nrrA = nrrData.get(a)[1] > 0 && nrrData.get(a)[3] > 0
                        ? (nrrData.get(a)[0] / nrrData.get(a)[1]) - (nrrData.get(a)[2] / nrrData.get(a)[3]) : 0;
                double nrrB = nrrData.get(b)[1] > 0 && nrrData.get(b)[3] > 0
                        ? (nrrData.get(b)[0] / nrrData.get(b)[1]) - (nrrData.get(b)[2] / nrrData.get(b)[3]) : 0;
                cmp = Double.compare(nrrB, nrrA);
                if (cmp != 0) return cmp;
                return Integer.compare(stats.get(b)[1], stats.get(a)[1]);
            });

            int position = sorted.indexOf(teamId) + 1;

            Map<String, Object> leagueInfo = new LinkedHashMap<>();
            leagueInfo.put("leagueId", league.getId());
            leagueInfo.put("leagueLabel", league.getDivision() + "." + league.getLeagueNumber());
            leagueInfo.put("country", league.getCountry());
            leagueInfo.put("format", league.getFormat());
            leagueInfo.put("division", league.getDivision());
            leagueInfo.put("leagueNumber", league.getLeagueNumber());
            leagueInfo.put("season", league.getSeason());
            leagueInfo.put("position", position);
            leagueInfo.put("totalTeams", allInLeague.size());
            leagues.add(leagueInfo);
        }

        return ResponseEntity.ok(Map.of("leagues", leagues));
    }

    // ── Ground info for any team ──

    @GetMapping("/{teamId}/ground")
    public ResponseEntity<?> getTeamGround(@PathVariable UUID teamId) {
        Optional<Team> opt = teamRepository.findById(teamId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Team team = opt.get();
        StadiumSeats seats = stadiumSeatsRepository.findByTeam(team).orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groundName", team.getGroundName());
        result.put("country", team.getCountry());

        int premium = seats != null ? seats.getPremium() : 2000;
        int standard = seats != null ? seats.getStandard() : 8000;
        int economy = seats != null ? seats.getEconomy() : 12000;
        int standing = seats != null ? seats.getStanding() : 3000;
        result.put("premium", premium);
        result.put("standard", standard);
        result.put("economy", economy);
        result.put("standing", standing);
        result.put("total", premium + standard + economy + standing);

        return ResponseEntity.ok(result);
    }

    private double oversToDecimal(double overs) {
        int fullOvers = (int) overs;
        int extraBalls = (int) Math.round((overs - fullOvers) * 10);
        return fullOvers + extraBalls / 6.0;
    }
}
