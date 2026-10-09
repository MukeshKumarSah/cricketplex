package com.cricketplex.service;

import com.cricketplex.entity.AppState;
import com.cricketplex.entity.Fixture;
import com.cricketplex.entity.League;
import com.cricketplex.entity.LeagueTeam;
import com.cricketplex.entity.MatchResult;
import com.cricketplex.entity.Team;
import com.cricketplex.entity.TransactionLog;
import com.cricketplex.entity.Trophy;
import com.cricketplex.repository.AppStateRepository;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.LeagueRepository;
import com.cricketplex.repository.LeagueTeamRepository;
import com.cricketplex.repository.MatchResultRepository;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.TransactionLogRepository;
import com.cricketplex.repository.TrophyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seasonal update - runs every 56 days (8 weeks).
 *
 * Day 56 (season end):
 *   - Prize money credited based on position
 *   - Salary update placeholder
 *
 * Day 1 of next season:
 *   - Promotion / relegation
 *   - Season change
 *   - Next-season fixture generation
 *
 * Historical browsing note:
 *   - Fixtures and league memberships are now season-stamped.
 *   - Season rollover creates next-season league memberships without deleting
 *     the previous season, so past tables remain reconstructable.
 */
@Service
@DependsOn("botTeamService")
@RequiredArgsConstructor
@Slf4j
public class SeasonalUpdateService {

    private static final String PRIZE_KEY = "last_seasonal_prize_season";
    private static final String TRANSITION_KEY = "last_seasonal_transition_season";
    @org.springframework.beans.factory.annotation.Value("${app.season1-start}")
    private String season1StartStr;
    private LocalDate getSeason1Start() { return LocalDate.parse(season1StartStr); }
    private static final int SEASON_DAYS = 56;

    private static final long[] PRIZE_BY_POSITION = {
            100_000, 70_000, 50_000, 35_000, 25_000, 15_000, 10_000, 5_000
    };

    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final FixtureRepository fixtureRepository;
    private final MatchResultRepository matchResultRepository;
    private final TeamRepository teamRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final AppStateRepository appStateRepository;
    private final ActivityLogService activityLogService;
    private final FixtureService fixtureService;
    private final TransactionTemplate txTemplate;
    private final TrophyRepository trophyRepository;

    @PostConstruct
    public void catchUpOnStartup() {
        log.info("Checking for missed seasonal updates...");
        txTemplate.executeWithoutResult(status -> applyMissedUpdates());
    }

    @Scheduled(cron = "0 15 0 * * *", zone = "UTC")
    public void scheduledCheck() {
        applyMissedUpdates();
    }

    @Transactional
    public void applyMissedUpdates() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        long daysSinceStart = ChronoUnit.DAYS.between(getSeason1Start(), today);
        if (daysSinceStart < 55) return;

        int latestPrizeSeason = 1 + (int) ((daysSinceStart - 55) / SEASON_DAYS);
        int latestTransitionSeason = (daysSinceStart >= 56)
                ? 1 + (int) ((daysSinceStart - 56) / SEASON_DAYS) : 0;

        int lastPrize = getAppStateInt(PRIZE_KEY, 0);
        int lastTransition = getAppStateInt(TRANSITION_KEY, 0);

        for (int season = lastPrize + 1; season <= latestPrizeSeason && season <= lastPrize + 4; season++) {
            processPrizeMoney(season);
            processSalaryUpdate(season);
            setAppStateInt(PRIZE_KEY, season);
            log.info("Seasonal Day 1 complete for season {}", season);
        }

        for (int season = lastTransition + 1; season <= latestTransitionSeason && season <= lastTransition + 4; season++) {
            processPromotionRelegation(season);
            processSeasonChange();
            healMissingCurrentSeasonMemberships();
            generateNewFixtures();
            setAppStateInt(TRANSITION_KEY, season);
            log.info("Seasonal Day 2 complete for season {}", season);
        }
    }

    /**
     * Dev/admin override — bypasses the date guard and forces both phases
     * (prize money + promotion/relegation + fixture generation) for the given season.
     * Uses bulk queries to avoid N+1 problems.
     * Blocked if any league fixtures for the season are still incomplete.
     */
    @Transactional
    public Map<String, Object> forceSeasonalUpdate(int season) {
        // Guard: T20/ODI — all rounds are in this season
        List<UUID> incompleteCurrent = fixtureRepository.findLeagueIdsWithIncompleteFixtures(season);
        // Guard: FC — rounds 1-7 are stored as 'season', rounds 8-14 stored as 'season-1' but logically season+1.
        // For the seasonal update of season N, FC prize money settles season N-1 fixtures.
        List<UUID> incompletePrev = season > 1
                ? fixtureRepository.findLeagueIdsWithIncompleteFixtures(season - 1) : List.of();

        if (!incompleteCurrent.isEmpty() || !incompletePrev.isEmpty()) {
            long total = (long) incompleteCurrent.size() + incompletePrev.size();
            throw new IllegalStateException(
                    total + " league(s) still have incomplete fixtures for season " + season +
                    ". Fast-forward or wait for all matches to complete before running the seasonal update.");
        }

        log.info("Force seasonal update: pre-loading standings cache for season {}", season);

        // Pre-compute standings for T20/ODI (settled = season) and FC (settled = season-1)
        Map<UUID, List<UUID>> standingsCurrent = buildBulkStandings(season);
        Map<UUID, List<UUID>> standingsPrev = season > 1 ? buildBulkStandings(season - 1) : Map.of();

        log.info("Force seasonal update: phase 1 (prize money) for season {}", season);
        processPrizeMoney(season, standingsCurrent, standingsPrev);
        processSalaryUpdate(season);
        setAppStateInt(PRIZE_KEY, season);

        log.info("Force seasonal update: phase 2 (promotion/relegation) for season {}", season);
        processPromotionRelegation(season, standingsCurrent, standingsPrev);
        processSeasonChange();
        healMissingCurrentSeasonMemberships();
        generateNewFixtures();
        setAppStateInt(TRANSITION_KEY, season);

        log.info("Force seasonal update complete for season {}", season);
        return Map.of(
                "season", season,
                "message", "Seasonal update (prize money + promotion/relegation + new fixtures) complete for season " + season
        );
    }

    /**
     * Build standings for ALL leagues for a given season in 3 bulk queries:
     *  1. All league-team memberships for the season
     *  2. All completed league fixtures for the season (with homeTeam/awayTeam joined)
     *  3. All match results for those fixtures (with winner joined)
     *
     * Returns: Map&lt;leagueId, sorted list of teamIds by points desc&gt;
     */
    private Map<UUID, List<UUID>> buildBulkStandings(int season) {
        // Query 1: league memberships → Map<leagueId, Set<teamId>>
        Map<UUID, Set<UUID>> members = new HashMap<>();
        for (LeagueTeam lt : leagueTeamRepository.findBySeason(season)) {
            members.computeIfAbsent(lt.getLeague().getId(), k -> new HashSet<>())
                   .add(lt.getTeam().getId());
        }

        // Query 2: all completed fixtures grouped by leagueId
        Map<UUID, List<Fixture>> fixturesByLeague = new HashMap<>();
        List<Fixture> allFixtures = fixtureRepository.findAllCompletedLeagueFixturesBySeason(season);
        List<UUID> fixtureIds = new ArrayList<>(allFixtures.size());
        for (Fixture f : allFixtures) {
            fixturesByLeague.computeIfAbsent(f.getLeague().getId(), k -> new ArrayList<>()).add(f);
            fixtureIds.add(f.getId());
        }

        // Query 3: all match results for those fixtures
        Map<UUID, MatchResult> resultsByFixture = new HashMap<>();
        if (!fixtureIds.isEmpty()) {
            for (MatchResult mr : matchResultRepository.findAllByFixtureIds(fixtureIds)) {
                resultsByFixture.put(mr.getFixture().getId(), mr);
            }
        }

        // Compute standings in memory for each league
        Map<UUID, List<UUID>> standings = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : members.entrySet()) {
            UUID leagueId = entry.getKey();
            Map<UUID, int[]> stats = new LinkedHashMap<>();
            for (UUID teamId : entry.getValue()) {
                stats.put(teamId, new int[2]); // [points, wins]
            }

            for (Fixture f : fixturesByLeague.getOrDefault(leagueId, List.of())) {
                MatchResult result = resultsByFixture.get(f.getId());
                if (result == null) continue;

                UUID teamA = f.getHomeTeam().getId();
                UUID teamB = f.getAwayTeam().getId();
                int[] sA = stats.get(teamA);
                int[] sB = stats.get(teamB);
                if (sA == null || sB == null) continue;

                if ("TIE".equals(result.getResultType())) {
                    sA[0] += 1;
                    sB[0] += 1;
                } else if (result.getWinner() != null) {
                    UUID winnerId = result.getWinner().getId();
                    if (winnerId.equals(teamA)) { sA[0] += 2; sA[1]++; }
                    else if (winnerId.equals(teamB)) { sB[0] += 2; sB[1]++; }
                }
            }

            List<UUID> sorted = new ArrayList<>(stats.keySet());
            sorted.sort((a, b) -> {
                int cmp = Integer.compare(stats.get(b)[0], stats.get(a)[0]);
                return cmp != 0 ? cmp : Integer.compare(stats.get(b)[1], stats.get(a)[1]);
            });
            standings.put(leagueId, sorted);
        }
        log.info("buildBulkStandings(season={}): {} leagues, {} fixtures, {} results loaded",
                season, members.size(), allFixtures.size(), resultsByFixture.size());
        return standings;
    }

    private void processPrizeMoney(int season,
                                   Map<UUID, List<UUID>> standingsCurrent,
                                   Map<UUID, List<UUID>> standingsPrev) {
        // Leagues with still-incomplete fixtures must be skipped
        Set<UUID> incompleteLeagues = new HashSet<>(fixtureRepository.findLeagueIdsWithIncompleteFixtures(season));
        Set<UUID> incompleteLeaguesPrev = season > 1
                ? new HashSet<>(fixtureRepository.findLeagueIdsWithIncompleteFixtures(season - 1)) : Set.of();

        List<Team> teamsToSave = new ArrayList<>();
        List<TransactionLog> txLogs = new ArrayList<>();
        List<Trophy> trophies = new ArrayList<>();

        for (League league : leagueRepository.findAll()) {
            Integer settledSeason = settlementSeason(league.getFormat(), season);
            if (settledSeason == null) continue;

            Set<UUID> incomplete = settledSeason == season ? incompleteLeagues : incompleteLeaguesPrev;
            if (incomplete.contains(league.getId())) continue;

            Map<UUID, List<UUID>> standingsMap = settledSeason == season ? standingsCurrent : standingsPrev;
            List<UUID> standings = standingsMap.getOrDefault(league.getId(), List.of());
            double divMul = divisionMultiplier(league.getDivision());

            for (int i = 0; i < standings.size() && i < PRIZE_BY_POSITION.length; i++) {
                Team team = teamRepository.findById(standings.get(i)).orElse(null);
                if (team == null) continue;

                long prize = Math.round(PRIZE_BY_POSITION[i] * divMul);
                long balance = team.getFunds() + prize;
                team.setFunds(balance);
                teamsToSave.add(team);

                txLogs.add(TransactionLog.builder()
                        .team(team).type("PRIZE_MONEY").amount(prize)
                        .description(String.format("Season %d %s %s %d.%d - %s place",
                                settledSeason, league.getCountry(), league.getFormat(),
                                league.getDivision(), league.getLeagueNumber(), ordinal(i + 1)))
                        .balanceAfter(balance)
                        .build());

                if (i == 0) {
                    trophies.add(Trophy.builder()
                            .team(team).format(league.getFormat()).country(league.getCountry())
                            .division(league.getDivision()).leagueNumber(league.getLeagueNumber())
                            .season(settledSeason).build());
                    activityLogService.log(team, "season",
                            String.format("Won %s %s Division %d.%d trophy - Season %d",
                                    league.getCountry(), league.getFormat(),
                                    league.getDivision(), league.getLeagueNumber(), settledSeason));
                }
            }
        }

        teamRepository.saveAll(teamsToSave);
        transactionLogRepository.saveAll(txLogs);
        trophyRepository.saveAll(trophies);

        teamRepository.findAll().stream()
                .filter(team -> !team.getIsBot())
                .forEach(team -> activityLogService.log(team, "season",
                        "Season " + season + " prize money distributed"));
    }

    private void processSalaryUpdate(int season) {
        log.info("Salary update for season {} - formula pending", season);
    }

    private void processPromotionRelegation(int season,
                                            Map<UUID, List<UUID>> standingsCurrent,
                                            Map<UUID, List<UUID>> standingsPrev) {
        String[] formats = {"T20", "ODI", "FC"};
        for (String country : leagueRepository.findDistinctCountries()) {
            for (String format : formats) {
                processPromoForCountryFormat(country, format, season, standingsCurrent, standingsPrev);
            }
        }
    }

    // Keep old signature for the scheduler path (applyMissedUpdates)
    private void processPrizeMoney(int season) {
        processPrizeMoney(season, buildBulkStandings(season),
                season > 1 ? buildBulkStandings(season - 1) : Map.of());
    }

    private void processPromotionRelegation(int season) {
        Map<UUID, List<UUID>> cur = buildBulkStandings(season);
        Map<UUID, List<UUID>> prev = season > 1 ? buildBulkStandings(season - 1) : Map.of();
        processPromotionRelegation(season, cur, prev);
    }

    private void processPromoForCountryFormat(String country, String format, int season,
                                              Map<UUID, List<UUID>> standingsCurrent,
                                              Map<UUID, List<UUID>> standingsPrev) {
        Integer settledSeason = settlementSeason(format, season);

        List<League> leagues = leagueRepository
                .findByCountryIgnoreCaseAndFormatOrderByDivisionAscLeagueNumberAsc(country, format);
        if (leagues.isEmpty()) return;

        int nextSeason = season + 1;
        if (settledSeason == null) {
            copyMembershipsForward(leagues, season, nextSeason);
            return;
        }

        Map<UUID, List<UUID>> standingsMap = settledSeason == season ? standingsCurrent : standingsPrev;

        Map<UUID, List<UUID>> nextSeasonMembers = new LinkedHashMap<>();
        for (League league : leagues) {
            List<UUID> members = leagueTeamRepository.findByLeagueIdAndSeason(league.getId(), settledSeason).stream()
                    .map(lt -> lt.getTeam().getId())
                    .collect(Collectors.toCollection(ArrayList::new));
            if (members.isEmpty()) {
                members = latestMemberships(league).stream()
                        .map(lt -> lt.getTeam().getId())
                        .collect(Collectors.toCollection(ArrayList::new));
            }
            nextSeasonMembers.put(league.getId(), members);
        }

        Map<Integer, List<League>> byDivision = leagues.stream()
                .collect(Collectors.groupingBy(League::getDivision));
        int maxDivision = byDivision.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
        List<UUID[]> swaps = new ArrayList<>();

        for (int division = 1; division < maxDivision; division++) {
            List<League> upperLeagues = byDivision.getOrDefault(division, List.of());
            List<League> lowerLeagues = byDivision.getOrDefault(division + 1, List.of());

            for (League parent : upperLeagues) {
                int childNum1 = 2 * parent.getLeagueNumber() - 1;
                int childNum2 = 2 * parent.getLeagueNumber();

                League child1 = lowerLeagues.stream()
                        .filter(league -> league.getLeagueNumber() == childNum1)
                        .findFirst().orElse(null);
                League child2 = lowerLeagues.stream()
                        .filter(league -> league.getLeagueNumber() == childNum2)
                        .findFirst().orElse(null);
                if (child1 == null || child2 == null) continue;

                List<UUID> parentStandings = standingsMap.getOrDefault(parent.getId(), List.of());
                List<UUID> child1Standings = standingsMap.getOrDefault(child1.getId(), List.of());
                List<UUID> child2Standings = standingsMap.getOrDefault(child2.getId(), List.of());
                if (parentStandings.size() < 8 || child1Standings.isEmpty() || child2Standings.isEmpty()) continue;

                UUID relegated1 = parentStandings.get(6);
                UUID relegated2 = parentStandings.get(7);
                UUID promoted1 = child1Standings.get(0);
                UUID promoted2 = child2Standings.get(0);

                swaps.add(new UUID[]{parent.getId(), child1.getId(), relegated1});
                swaps.add(new UUID[]{parent.getId(), child2.getId(), relegated2});
                swaps.add(new UUID[]{child1.getId(), parent.getId(), promoted1});
                swaps.add(new UUID[]{child2.getId(), parent.getId(), promoted2});

                logTeamMovement(relegated1, format, division, division + 1, false);
                logTeamMovement(relegated2, format, division, division + 1, false);
                logTeamMovement(promoted1, format, division + 1, division, true);
                logTeamMovement(promoted2, format, division + 1, division, true);
            }
        }

        for (UUID[] swap : swaps) {
            nextSeasonMembers.computeIfAbsent(swap[0], ignored -> new ArrayList<>()).remove(swap[2]);
        }
        for (UUID[] swap : swaps) {
            List<UUID> members = nextSeasonMembers.computeIfAbsent(swap[1], ignored -> new ArrayList<>());
            if (!members.contains(swap[2])) members.add(swap[2]);
        }

        for (League league : leagues) {
            leagueTeamRepository.deleteByLeagueIdAndSeason(league.getId(), nextSeason);
            List<LeagueTeam> rows = new ArrayList<>();
            for (UUID teamId : nextSeasonMembers.getOrDefault(league.getId(), List.of())) {
                Team team = teamRepository.findById(teamId).orElse(null);
                if (team == null) continue;
                rows.add(LeagueTeam.builder().league(league).team(team).season(nextSeason).build());
            }
            leagueTeamRepository.saveAll(rows);
        }
    }

    private void processSeasonChange() {
        List<League> leagues = leagueRepository.findAll();
        for (League league : leagues) {
            league.setSeason(league.getSeason() + 1);
        }
        leagueRepository.saveAll(leagues);
    }

    private void copyMembershipsForward(List<League> leagues, int sourceSeason, int nextSeason) {
        for (League league : leagues) {
            if (leagueTeamRepository.countByLeagueIdAndSeason(league.getId(), nextSeason) > 0) {
                continue;
            }
            List<LeagueTeam> source = leagueTeamRepository.findByLeagueIdAndSeason(league.getId(), sourceSeason);
            if (source.isEmpty()) {
                source = latestMemberships(league);
            }
            List<LeagueTeam> rows = new ArrayList<>();
            for (LeagueTeam lt : source) {
                rows.add(LeagueTeam.builder()
                        .league(league)
                        .team(lt.getTeam())
                        .season(nextSeason)
                        .build());
            }
            if (!rows.isEmpty()) {
                leagueTeamRepository.saveAll(rows);
            }
        }
    }

    private List<LeagueTeam> latestMemberships(League league) {
        List<LeagueTeam> all = leagueTeamRepository.findByLeagueId(league.getId());
        if (all.isEmpty()) return List.of();
        int latest = all.stream().mapToInt(LeagueTeam::getSeason).max().orElse(1);
        return all.stream().filter(lt -> lt.getSeason() == latest).toList();
    }

    private void healMissingCurrentSeasonMemberships() {
        int currentSeason = leagueRepository.findMaxSeason();
        int copied = 0;
        for (League league : leagueRepository.findAll()) {
            if (leagueTeamRepository.countByLeagueIdAndSeason(league.getId(), currentSeason) > 0) {
                continue;
            }
            List<LeagueTeam> source = latestMemberships(league);
            if (source.isEmpty() || source.get(0).getSeason() == currentSeason) {
                continue;
            }
            List<LeagueTeam> rows = new ArrayList<>();
            for (LeagueTeam lt : source) {
                rows.add(LeagueTeam.builder()
                        .league(league)
                        .team(lt.getTeam())
                        .season(currentSeason)
                        .build());
            }
            leagueTeamRepository.saveAll(rows);
            copied += rows.size();
        }
        if (copied > 0) {
            log.info("Copied {} league memberships onto current season {}", copied, currentSeason);
        }
    }

    private void generateNewFixtures() {
        int generated = 0;
        for (League league : leagueRepository.findAll()) {
            long upcoming = fixtureRepository.countByLeagueIdAndStatusNot(league.getId(), "COMPLETED");
            if (upcoming > 0) continue;
            fixtureService.generateFixtures(league);
            generated++;
        }
        log.info("Fixtures generated for {} leagues", generated);
    }

    private Integer settlementSeason(String format, int season) {
        if (!"FC".equals(format)) return season;
        return season > 1 ? season - 1 : null;
    }

    private void logTeamMovement(UUID teamId, String format, int fromDiv, int toDiv, boolean promoted) {
        teamRepository.findById(teamId).ifPresent(team -> {
            String action = promoted ? "Promoted" : "Relegated";
            activityLogService.log(team, "season",
                    String.format("%s in %s: Division %d -> Division %d", action, format, fromDiv, toDiv));
        });
    }

    private int getAppStateInt(String key, int defaultVal) {
        return appStateRepository.findById(key)
                .map(state -> Integer.parseInt(state.getValue()))
                .orElse(defaultVal);
    }

    private void setAppStateInt(String key, int value) {
        AppState state = appStateRepository.findById(key)
                .orElse(new AppState(key, "0"));
        state.setValue(String.valueOf(value));
        appStateRepository.save(state);
    }

    private void saveTx(Team team, String type, long amount, String desc, long balanceAfter) {
        transactionLogRepository.save(TransactionLog.builder()
                .team(team)
                .type(type)
                .amount(amount)
                .description(desc)
                .balanceAfter(balanceAfter)
                .build());
    }

    private static String ordinal(int i) {
        return switch (i) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> i + "th";
        };
    }

    private static double divisionMultiplier(int division) {
        return switch (division) {
            case 1 -> 1.0;
            case 2 -> 0.6;
            default -> 0.4;
        };
    }
}
