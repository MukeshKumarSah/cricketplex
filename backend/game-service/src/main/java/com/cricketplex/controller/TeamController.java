package com.cricketplex.controller;

import com.cricketplex.dto.ApiResponse;
import com.cricketplex.dto.TeamSetupRequest;
import com.cricketplex.entity.League;
import com.cricketplex.entity.LeagueTeam;
import com.cricketplex.entity.Fixture;
import com.cricketplex.entity.Innings;
import com.cricketplex.entity.MatchResult;
import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.LeagueRepository;
import com.cricketplex.repository.LeagueTeamRepository;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.MatchLineupRepository;
import com.cricketplex.repository.MatchResultRepository;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.ActivityLogService;
import com.cricketplex.service.MatchEngine;
import com.cricketplex.service.TeamService;
import com.cricketplex.service.FixtureService;
import com.cricketplex.service.WeatherService;
import com.cricketplex.util.TeamHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamController {
    @org.springframework.beans.factory.annotation.Value("${app.season1-start}")
    private String season1StartStr;
    private LocalDate getSeason1Start() { return LocalDate.parse(season1StartStr); }
    private static final int SEASON_DAYS = 56;

    private final TeamService teamService;
    private final FixtureService fixtureService;
    private final MatchEngine matchEngine;
    private final ActivityLogService activityLogService;
    private final UserRepository userRepository;
    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final TeamRepository teamRepository;
    private final FixtureRepository fixtureRepository;
    private final MatchResultRepository matchResultRepository;
    private final MatchLineupRepository matchLineupRepository;
    private final WeatherService weatherService;
    private final TeamHelper teamHelper;

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

    /**
     * Returns availability + match time for all countries in one call.
     */
    @GetMapping("/all-country-availability")
    public ResponseEntity<?> allCountryAvailability() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String country : FixtureService.getAllCountries()) {
            int slots = teamService.countAvailableSlots(country);
            String matchTime = FixtureService.getMatchStartTime(country);
            list.add(Map.of(
                    "country", country,
                    "available", slots > 0,
                    "slots", slots,
                    "matchStartTimeUtc", matchTime
            ));
        }
        return ResponseEntity.ok(list);
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setupTeam(@AuthenticationPrincipal UserPrincipal principal,
                                       @Valid @RequestBody TeamSetupRequest request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Team team = teamService.setupTeam(user, request);

        activityLogService.log(team, "signup", "Welcome to CricketPlex! You signed up as manager.");
        activityLogService.log(team, "team-setup", "Team \"" + team.getTeamName() + "\" created in " + team.getCountry() + ".");
        activityLogService.log(team, "league", "Placed into T20, ODI, and FC leagues for " + team.getCountry() + ".");

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

    @GetMapping("/season-calendar")
    public ResponseEntity<?> getSeasonCalendar() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate season1Start = getSeason1Start();
        long daysSince = ChronoUnit.DAYS.between(season1Start, today);
        int currentSeason = 1 + (int) Math.max(0, daysSince / SEASON_DAYS);
        int dayIndex = (int) Math.floorMod(daysSince, SEASON_DAYS); // 0..55
        int currentSeasonDay = dayIndex + 1; // 1..56
        LocalDate currentSeasonStart = season1Start.plusDays((long) (currentSeason - 1) * SEASON_DAYS);

        List<Map<String, Object>> days = new ArrayList<>();
        for (int i = 0; i < SEASON_DAYS; i++) {
            LocalDate date = currentSeasonStart.plusDays(i);
            int seasonDay = i + 1;
            List<String> events = new ArrayList<>();

            if (i == 0) events.add("Season Start & Fixtures Release");
            if (i == 55) events.add("Season End & Prize Money Distribution");

            // T20: Sunday + Thursday pattern from season anchor (Thu start)
            if (i >= 3 && ((i - 3) % 7 == 0 || (i - 7) % 7 == 0)) {
                events.add("T20 Match Day");
            }
            // ODI: Monday + Friday pattern from season anchor (Thu start)
            if (i >= 4 && ((i - 4) % 7 == 0 || (i - 8) % 7 == 0)) {
                events.add("ODI Match Day");
            }
            // FC: Tuesday (first half only in this season's stored fixtures)
            if (i >= 5 && (i - 5) % 7 == 0 && i <= 47) {
                events.add("FC Match Day");
            }

            if (events.isEmpty()) events.add("No League Match Day");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seasonDay", seasonDay);
            row.put("date", date.toString());
            row.put("weekday", date.getDayOfWeek().toString());
            row.put("isToday", seasonDay == currentSeasonDay);
            row.put("events", events);
            days.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("season", currentSeason);
        out.put("seasonStartDate", currentSeasonStart.toString());
        out.put("todayUtc", today.toString());
        out.put("currentSeasonDay", currentSeasonDay);
        out.put("totalSeasonDays", SEASON_DAYS);
        out.put("seasonEndDate", currentSeasonStart.plusDays(SEASON_DAYS - 1).toString());
        out.put("days", days);
        return ResponseEntity.ok(out);
    }

    @GetMapping("/my-leagues")
    public ResponseEntity<?> getMyLeagues(@AuthenticationPrincipal UserPrincipal principal) {
        Optional<Team> teamOpt = teamHelper.getActiveTeam(principal);
        if (teamOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("leagues", List.of()));
        }

        Team myTeam = teamOpt.get();
        int currentSeason = leagueRepository.findMaxSeason();
        List<LeagueTeam> myLeagueTeams = new ArrayList<>(leagueTeamRepository.findByTeamIdAndSeason(myTeam.getId(), currentSeason));

        // Auto-assign if team exists but has no league entries (legacy teams)
        if (myLeagueTeams.isEmpty()) {
            teamService.assignTeamToLeagues(myTeam);
            myLeagueTeams = new ArrayList<>(leagueTeamRepository.findByTeamIdAndSeason(myTeam.getId(), currentSeason));
        }

        // FC leagues span 2 seasons — rounds 8-14 happen in season N+1 dates but
        // memberships are written for season N (FC promotion/relegation is deferred).
        // If no FC entry exists for currentSeason, include FC entries from season N-1.
        if (currentSeason > 1) {
            Set<UUID> foundLeagueIds = myLeagueTeams.stream()
                    .map(lt -> lt.getLeague().getId()).collect(Collectors.toSet());
            leagueTeamRepository.findByTeamIdAndSeason(myTeam.getId(), currentSeason - 1)
                    .stream()
                    .filter(lt -> "FC".equalsIgnoreCase(lt.getLeague().getFormat()))
                    .filter(lt -> !foundLeagueIds.contains(lt.getLeague().getId()))
                    .forEach(myLeagueTeams::add);
        }

        List<Map<String, Object>> leagues = new ArrayList<>();
        for (LeagueTeam lt : myLeagueTeams) {
            League league = lt.getLeague();

            // Get all teams in this league
            int standingsSeason = resolveLeagueSeasonForSummary(league);
            List<LeagueTeam> allInLeague = leagueTeamRepository.findByLeagueIdAndSeason(league.getId(), standingsSeason);

            // Compute standings from completed fixtures
            Map<UUID, int[]> stats = new LinkedHashMap<>(); // [played, won, lost, tied, points]
            // For FC: [runsScored, wicketsLost, runsConceded, wicketsTaken]
            // For T20/ODI: [runsScored, oversPlayed, runsConceded, oversBowled]
            Map<UUID, double[]> nrrData = new LinkedHashMap<>();
            boolean isFC = "FC".equals(league.getFormat());
            for (LeagueTeam entry : allInLeague) {
                stats.put(entry.getTeam().getId(), new int[5]);
                nrrData.put(entry.getTeam().getId(), new double[4]);
            }

            List<Fixture> fixtures = fixtureRepository.findByLeagueIdAndSeason(league.getId(), standingsSeason);
            for (Fixture f : fixtures) {
                if (!"COMPLETED".equals(f.getStatus())) continue;
                Optional<MatchResult> mrOpt = matchResultRepository.findByFixtureId(f.getId());
                if (mrOpt.isEmpty()) continue;
                MatchResult mr = mrOpt.get();

                // Derive team IDs from innings (handles bot→human swaps where
                // fixture still references old bot but innings reference new team)
                List<Innings> innList = mr.getInningsList();
                if (innList.isEmpty()) continue;
                UUID teamA = innList.get(0).getBattingTeam().getId();
                UUID teamB = innList.get(0).getBowlingTeam().getId();
                int[] hs = stats.get(teamA);
                int[] as = stats.get(teamB);
                if (hs == null || as == null) continue;

                hs[0]++;
                as[0]++;

                // Accumulate data from innings
                for (Innings inn : mr.getInningsList()) {
                    UUID batTeamId = inn.getBattingTeam().getId();
                    UUID bowlTeamId = inn.getBowlingTeam().getId();
                    int runs = inn.getTotalRuns() != null ? inn.getTotalRuns() : 0;
                    int wickets = inn.getTotalWickets() != null ? inn.getTotalWickets() : 0;

                    double[] batNrr = nrrData.get(batTeamId);
                    double[] bowlNrr = nrrData.get(bowlTeamId);

                    if (isFC) {
                        if (batNrr != null) { batNrr[0] += runs; batNrr[1] += wickets; }
                        if (bowlNrr != null) { bowlNrr[2] += runs; bowlNrr[3] += wickets; }
                    } else {
                        double overs;
                        if (Boolean.TRUE.equals(inn.getAllOut())) {
                            overs = getMaxOvers(league.getFormat());
                        } else {
                            overs = oversToDecimal(inn.getTotalOvers() != null ? inn.getTotalOvers() : 0.0);
                        }
                        if (batNrr != null) { batNrr[0] += runs; batNrr[1] += overs; }
                        if (bowlNrr != null) { bowlNrr[2] += runs; bowlNrr[3] += overs; }
                    }
                }

                if ("TIE".equals(mr.getResultType())) {
                    hs[3]++; as[3]++;
                    hs[4] += 1; as[4] += 1;
                } else if (mr.getWinner() != null) {
                    UUID winnerId = mr.getWinner().getId();
                    if (winnerId.equals(teamA)) {
                        hs[1]++; as[2]++; hs[4] += 2;
                    } else if (winnerId.equals(teamB)) {
                        as[1]++; hs[2]++; as[4] += 2;
                    }
                }
            }

            // Sort teams by points DESC, then tiebreaker DESC, then wins DESC
            List<UUID> sorted = new ArrayList<>(stats.keySet());
            sorted.sort((a, b) -> {
                int cmp = Integer.compare(stats.get(b)[4], stats.get(a)[4]);
                if (cmp != 0) return cmp;
                double[] na = nrrData.get(a);
                double[] nb = nrrData.get(b);
                double valA, valB;
                if (isFC) {
                    double batAvgA = na[1] > 0 ? na[0] / na[1] : 0.0;
                    double bowlAvgA = na[3] > 0 ? na[2] / na[3] : 0.0;
                    valA = bowlAvgA > 0 ? batAvgA / bowlAvgA : 0.0;
                    double batAvgB = nb[1] > 0 ? nb[0] / nb[1] : 0.0;
                    double bowlAvgB = nb[3] > 0 ? nb[2] / nb[3] : 0.0;
                    valB = bowlAvgB > 0 ? batAvgB / bowlAvgB : 0.0;
                } else {
                    valA = (na[1] > 0 && na[3] > 0) ? (na[0] / na[1]) - (na[2] / na[3]) : 0.0;
                    valB = (nb[1] > 0 && nb[3] > 0) ? (nb[0] / nb[1]) - (nb[2] / nb[3]) : 0.0;
                }
                cmp = Double.compare(valB, valA);
                if (cmp != 0) return cmp;
                return Integer.compare(stats.get(b)[1], stats.get(a)[1]);
            });

            int position = sorted.indexOf(myTeam.getId()) + 1;
            if (position == 0) position = allInLeague.size(); // fallback

            int[] myStats = stats.getOrDefault(myTeam.getId(), new int[5]);

            // Recent form: last 5 completed matches for my team (newest first)
            // Use innings team refs (not fixture teams) to handle bot→human swaps
            List<String> recentForm = new ArrayList<>();
            List<Fixture> completedFixtures = fixtures.stream()
                    .filter(f -> "COMPLETED".equals(f.getStatus()))
                    .sorted(Comparator.comparing(Fixture::getMatchDate,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(f -> f.getRound() != null ? f.getRound() : 0,
                                    Comparator.reverseOrder()))
                    .toList();
            int formCount = 0;
            for (Fixture cf : completedFixtures) {
                if (formCount >= 5) break;
                Optional<MatchResult> mrOpt2 = matchResultRepository.findByFixtureId(cf.getId());
                if (mrOpt2.isEmpty()) continue;
                MatchResult mr2 = mrOpt2.get();
                List<Innings> innList2 = mr2.getInningsList();
                if (innList2.isEmpty()) continue;
                boolean involved = innList2.stream().anyMatch(inn ->
                        inn.getBattingTeam().getId().equals(myTeam.getId())
                        || inn.getBowlingTeam().getId().equals(myTeam.getId()));
                if (!involved) continue;
                formCount++;
                if ("TIE".equals(mr2.getResultType()) || "DRAW".equals(mr2.getResultType())
                        || "NO_RESULT".equals(mr2.getResultType())) {
                    recentForm.add("T");
                } else if (mr2.getWinner() != null && mr2.getWinner().getId().equals(myTeam.getId())) {
                    recentForm.add("W");
                } else {
                    recentForm.add("L");
                }
            }

            Map<String, Object> leagueInfo = new LinkedHashMap<>();
            leagueInfo.put("leagueId", league.getId());
            leagueInfo.put("leagueLabel", league.getDivision() + "." + league.getLeagueNumber());
            leagueInfo.put("country", league.getCountry());
            leagueInfo.put("format", league.getFormat());
            leagueInfo.put("division", league.getDivision());
            leagueInfo.put("leagueNumber", league.getLeagueNumber());
            leagueInfo.put("season", standingsSeason);
            leagueInfo.put("matchStartTimeUtc", league.getMatchStartTime());
            leagueInfo.put("position", position);
            leagueInfo.put("totalTeams", allInLeague.size());
            leagueInfo.put("played", myStats[0]);
            leagueInfo.put("won", myStats[1]);
            leagueInfo.put("lost", myStats[2]);
            leagueInfo.put("tied", myStats[3]);
            leagueInfo.put("points", myStats[4]);
            leagueInfo.put("recentForm", recentForm);
            leagues.add(leagueInfo);
        }

        return ResponseEntity.ok(Map.of("leagues", leagues));
    }

    @GetMapping("/my-matches")
    public ResponseEntity<?> getMyMatches(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) String format) {
        Optional<Team> teamOpt = teamHelper.getActiveTeam(principal);
        if (teamOpt.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Team myTeam = teamOpt.get();

        // Ensure fixtures exist for all user's leagues
        int currentSeason = leagueRepository.findMaxSeason();
        List<LeagueTeam> myLeagueTeams = leagueTeamRepository.findByTeamIdAndSeason(myTeam.getId(), currentSeason);
        for (LeagueTeam lt : myLeagueTeams) {
            fixtureService.ensureFixturesExist(lt.getLeague());
        }

        List<Fixture> all = fixtureRepository.findAllByTeamOrderByMatchDate(myTeam);

        // Eagerly simulate any overdue SCHEDULED league fixtures so they show as live
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        for (Fixture f : all) {
            if ("FC".equalsIgnoreCase(f.getLeague() != null ? f.getLeague().getFormat() : f.getFormat())
                    && !"FC_DAY1_COMPLETE".equals(f.getStatus())) {
                Optional<MatchResult> mrOpt = matchResultRepository.findByFixtureId(f.getId());
                if (mrOpt.isPresent() && "PENDING".equals(mrOpt.get().getResultType())) {
                    f.setFcDay(1);
                    f.setStatus("FC_DAY1_COMPLETE");
                    fixtureRepository.save(f);
                    continue;
                }
            }
            if (!"SCHEDULED".equals(f.getStatus())) continue;
            if (f.getLeague() == null) continue;
            String startTimeStr = f.getLeague().getMatchStartTime();
            if (startTimeStr == null) startTimeStr = "14:00";
            LocalTime matchTime = LocalTime.parse(startTimeStr);
            LocalDateTime matchStart = LocalDateTime.of(f.getMatchDate(), matchTime);
            if (nowUtc.isBefore(matchStart)) continue;
            if (matchResultRepository.existsByFixtureId(f.getId())) continue;
            try {
                matchEngine.simulateMatch(f.getId());
                Fixture fresh = fixtureRepository.findById(f.getId()).orElse(null);
                if (fresh != null && !"FC_DAY1_COMPLETE".equals(fresh.getStatus())) {
                    fresh.setStatus("IN_PROGRESS");
                    fixtureRepository.save(fresh);
                }
            } catch (Exception ignored) { }
        }

        // Batch-load fixture IDs that have a saved lineup
        Set<UUID> linedUpFixtures = new HashSet<>(matchLineupRepository.findFixtureIdsByTeamId(myTeam.getId()));

        LocalDate today = LocalDate.now();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Fixture f : all) {
            League league = f.getLeague();
            boolean isCup = "CUP".equals(f.getMatchType());
            boolean isFriendly = !isCup && league == null;
            String fFormat = (isFriendly || isCup) ? f.getFormat() : league.getFormat();

            if (season != null) {
                if (isFriendly && !season.equals(f.getSeason())) continue;
                if (!isFriendly && !isCup && !includeLeagueFixtureForSeason(f, league, season, currentSeason)) continue;
            }
            if (format != null && (fFormat == null || !fFormat.equalsIgnoreCase(format))) continue;

            boolean isHome = f.getHomeTeam().getId().equals(myTeam.getId());
            Team opponent = isHome ? f.getAwayTeam() : f.getHomeTeam();

            Map<String, Object> match = new LinkedHashMap<>();
            match.put("id", f.getId());
            match.put("matchDate", f.getMatchDate().toString());
            match.put("matchStartTimeUtc", (isFriendly || isCup) ? f.getMatchTime() : league.getMatchStartTime());
            match.put("format", fFormat);
            match.put("round", f.getRound());
            match.put("leagueLabel", isCup ? "Cup – R" + f.getRound() : isFriendly ? "Friendly" : league.getDivision() + "." + league.getLeagueNumber());
            match.put("matchType", isCup ? "CUP" : isFriendly ? "FRIENDLY" : "LEAGUE");
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
            match.put("season", (isFriendly || isCup) ? f.getSeason() : fixtureSeason(f, league));
            match.put("lineupSet", linedUpFixtures.contains(f.getId()));
            match.put("weather", weatherService.getMatchWeather(
                    f.getHomeTeam().getCountry(), f.getMatchDate(), today));

            // Result summary for completed matches (from myTeam's perspective)
            if ("COMPLETED".equals(f.getStatus())) {
                matchResultRepository.findByFixtureId(f.getId()).ifPresent(mr -> {
                    String summary;
                    if ("TIE".equals(mr.getResultType())) {
                        summary = "Match Tied";
                    } else if ("DRAW".equals(mr.getResultType())) {
                        summary = "Match Drawn";
                    } else if (mr.getWinner() != null && mr.getResultMargin() != null) {
                        boolean myTeamWon = mr.getWinner().getId().equals(myTeam.getId());
                        String margin = mr.getResultMargin().toString();
                        if ("RUNS".equals(mr.getResultType())) {
                            summary = myTeamWon ? "Won by " + margin + " runs" : "Lost by " + margin + " runs";
                        } else if ("WICKETS".equals(mr.getResultType())) {
                            summary = myTeamWon ? "Won by " + margin + " wickets" : "Lost by " + margin + " wickets";
                        } else if ("INNINGS".equals(mr.getResultType())) {
                            summary = myTeamWon ? "Won by an innings & " + margin + " runs" : "Lost by an innings & " + margin + " runs";
                        } else {
                            summary = myTeamWon ? "Won" : "Lost";
                        }
                    } else {
                        summary = "Completed";
                    }
                    match.put("resultSummary", summary);
                    match.put("winnerId", mr.getWinner() != null ? mr.getWinner().getId() : null);
                });
            }

            result.add(match);
        }

        return ResponseEntity.ok(result);
    }

    private double oversToDecimal(double overs) {
        int fullOvers = (int) overs;
        int extraBalls = (int) Math.round((overs - fullOvers) * 10);
        return fullOvers + extraBalls / 6.0;
    }

    private double getMaxOvers(String format) {
        if (format == null) return 20;
        return switch (format) {
            case "ODI" -> 50;
            case "FC" -> 90;
            default -> 20;
        };
    }

    private int resolveLeagueSeasonForSummary(League league) {
        if (!"FC".equalsIgnoreCase(league.getFormat())) return league.getSeason();
        int previousSeason = league.getSeason() - 1;
        if (previousSeason >= 1
                && fixtureRepository.existsByLeagueIdAndSeason(league.getId(), previousSeason)
                && fixtureRepository.countByLeagueIdAndSeasonAndStatusNot(league.getId(), previousSeason, "COMPLETED") > 0) {
            return previousSeason;
        }
        return league.getSeason();
    }

    private boolean includeLeagueFixtureForSeason(Fixture fixture, League league, int requestedSeason, int currentSeason) {
        int fixtureSeason = fixtureSeason(fixture, league);
        return fixtureSeason == requestedSeason;
    }

    private int fixtureSeason(Fixture fixture, League league) {
        int stored = fixture.getSeason() != null ? fixture.getSeason()
                   : (league != null ? league.getSeason() : 1);
        // FC rounds 8-14 have match dates in the following season
        if ("FC".equalsIgnoreCase(league != null ? league.getFormat() : fixture.getFormat())
                && fixture.getRound() != null && fixture.getRound() > 7) {
            return stored + 1;
        }
        return stored;
    }
}
