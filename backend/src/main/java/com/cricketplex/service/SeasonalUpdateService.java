package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Seasonal update — runs every 56 days (8 weeks).
 *
 * Day 1 (Wednesday, day 52 of cycle):
 *   - Prize money credited based on position and division
 *   - Salary update of players (formula TBD)
 *
 * Day 2 (Thursday, day 53 of cycle):
 *   - Promotion/relegation: bottom 2 demote, top 1 from each lower-division league promotes
 *   - Season change: increment league.season
 *   - Next-season fixture generation
 *
 * Day 5 (Sunday): T20 matches begin in the new season, and so on.
 *
 * FC note: 14 rounds span 2 seasons (R1-R7 in odd, R8-R14 in even).
 *          FC promotion/relegation + new FC fixtures only after all 14 rounds complete.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeasonalUpdateService {

    /* ═══════════════ constants ═══════════════ */

    private static final String PRIZE_KEY = "last_seasonal_prize_season";
    private static final String TRANSITION_KEY = "last_seasonal_transition_season";

    /** Must match FixtureService.SEASON_1_START */
    private static final LocalDate SEASON_1_START = LocalDate.of(2026, 4, 5);
    private static final int SEASON_DAYS = 56;

    /** Prize money by finishing position (index 0 = 1st place) — Div 1 base amounts */
    private static final long[] PRIZE_BY_POSITION = {
            100_000, 70_000, 50_000, 35_000, 25_000, 15_000, 10_000, 5_000
    };

    /* ═══════════════ dependencies ═══════════════ */

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

    /* ═══════════════ scheduling + resilience ═══════════════ */

    @PostConstruct
    public void catchUpOnStartup() {
        log.info("Checking for missed seasonal updates...");
        txTemplate.executeWithoutResult(status -> applyMissedUpdates());
    }

    /** Daily at 00:15 UTC — checks if a seasonal milestone has been reached. */
    @Scheduled(cron = "0 15 0 * * *", zone = "UTC")
    public void scheduledCheck() {
        applyMissedUpdates();
    }

    @Transactional
    public void applyMissedUpdates() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        long daysSinceStart = ChronoUnit.DAYS.between(SEASON_1_START, today);
        if (daysSinceStart < 52) return; // too early for any seasonal update

        // Which season's update days have passed?
        int latestPrizeSeason = 1 + (int) ((daysSinceStart - 52) / SEASON_DAYS);
        int latestTransitionSeason = (daysSinceStart >= 53)
                ? 1 + (int) ((daysSinceStart - 53) / SEASON_DAYS) : 0;

        int lastPrize = getAppStateInt(PRIZE_KEY, 0);
        int lastTransition = getAppStateInt(TRANSITION_KEY, 0);

        // Day 1 (Wednesday): Prize money + salary update
        for (int s = lastPrize + 1; s <= latestPrizeSeason && s <= lastPrize + 4; s++) {
            processPrizeMoney(s);
            processSalaryUpdate(s);
            setAppStateInt(PRIZE_KEY, s);
            log.info("Seasonal Day 1 complete for season {}: prize money + salary update", s);
        }

        // Day 2 (Thursday): Promotion/relegation + season change + fixtures
        for (int s = lastTransition + 1; s <= latestTransitionSeason && s <= lastTransition + 4; s++) {
            processPromotionRelegation(s);
            processSeasonChange();
            generateNewFixtures();
            setAppStateInt(TRANSITION_KEY, s);
            log.info("Seasonal Day 2 complete for season {}: promotion + season change + fixtures", s);
        }
    }

    /* ═══════════════ Day 1: Prize Money ═══════════════ */

    private void processPrizeMoney(int season) {
        List<League> allLeagues = leagueRepository.findAll();

        for (League league : allLeagues) {
            // FC: skip if not all fixtures are completed (R8-R14 still pending)
            if ("FC".equals(league.getFormat()) &&
                    fixtureRepository.countByLeagueIdAndStatusNot(league.getId(), "COMPLETED") > 0) {
                continue;
            }

            List<UUID> standings = computeStandings(league);
            double divMul = divisionMultiplier(league.getDivision());

            for (int i = 0; i < standings.size() && i < PRIZE_BY_POSITION.length; i++) {
                UUID teamId = standings.get(i);
                Team team = teamRepository.findById(teamId).orElse(null);
                if (team == null) continue;

                long prize = Math.round(PRIZE_BY_POSITION[i] * divMul);
                long balance = team.getFunds() + prize;
                team.setFunds(balance);
                teamRepository.save(team);

                saveTx(team, "PRIZE_MONEY", prize,
                        String.format("Season %d %s %s %d.%d — %s place",
                                season, league.getCountry(), league.getFormat(),
                                league.getDivision(), league.getLeagueNumber(),
                                ordinal(i + 1)),
                        balance);

                // Award trophy to 1st place
                if (i == 0) {
                    trophyRepository.save(Trophy.builder()
                            .team(team)
                            .format(league.getFormat())
                            .country(league.getCountry())
                            .division(league.getDivision())
                            .leagueNumber(league.getLeagueNumber())
                            .season(season)
                            .build());
                    activityLogService.log(team, "season",
                            String.format("Won %s %s Division %d.%d trophy — Season %d",
                                    league.getCountry(), league.getFormat(),
                                    league.getDivision(), league.getLeagueNumber(), season));
                    log.info("Trophy awarded to team {} for {} {} {}.{} season {}",
                            team.getTeamName(), league.getCountry(), league.getFormat(),
                            league.getDivision(), league.getLeagueNumber(), season);
                }
            }
        }

        // Activity log for human teams
        teamRepository.findAll().stream()
                .filter(t -> !t.getIsBot())
                .forEach(t -> activityLogService.log(t, "season",
                        "Season " + season + " prize money distributed"));

        log.info("Prize money distributed for season {}", season);
    }

    /* ═══════════════ Day 1: Salary Update ═══════════════ */

    private void processSalaryUpdate(int season) {
        // TODO: Formula to be provided later
        log.info("Salary update for season {} — formula pending", season);
    }

    /* ═══════════════ Day 2: Promotion / Relegation ═══════════════ */

    private void processPromotionRelegation(int season) {
        List<String> countries = leagueRepository.findDistinctCountries();
        String[] formats = {"T20", "ODI", "FC"};

        for (String country : countries) {
            for (String format : formats) {
                processPromoForCountryFormat(country, format, season);
            }
        }
    }

    /**
     * Promotion/relegation for one country + format combination.
     *
     * Per upper-division league:
     *   - Bottom 2 (7th, 8th) → relegated to child leagues
     * Per lower-division league:
     *   - Top 1 (1st) → promoted to parent league
     *
     * League mapping (binary tree):
     *   Div N, League L → children at Div N+1, Leagues (2L-1) and (2L)
     *
     * Division 1 has no promotion (already the top).
     * Lowest division has no relegation (no lower division).
     */
    private void processPromoForCountryFormat(String country, String format, int season) {
        List<League> leagues = leagueRepository
                .findByCountryIgnoreCaseAndFormatOrderByDivisionAscLeagueNumberAsc(country, format);
        if (leagues.isEmpty()) return;

        // FC: skip if any league still has unfinished fixtures
        if ("FC".equals(format) &&
                leagues.stream().anyMatch(l ->
                        fixtureRepository.countByLeagueIdAndStatusNot(l.getId(), "COMPLETED") > 0)) {
            return;
        }

        // Phase 1: Compute all standings before any swaps
        Map<UUID, List<UUID>> standings = new LinkedHashMap<>();
        for (League league : leagues) {
            standings.put(league.getId(), computeStandings(league));
        }

        Map<Integer, List<League>> byDiv = leagues.stream()
                .collect(Collectors.groupingBy(League::getDivision));
        int maxDiv = byDiv.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);

        // Phase 2: Collect all swaps
        List<UUID[]> swaps = new ArrayList<>(); // each: [fromLeague, toLeague, teamId]

        for (int div = 1; div < maxDiv; div++) {
            List<League> upperLeagues = byDiv.getOrDefault(div, List.of());
            List<League> lowerLeagues = byDiv.getOrDefault(div + 1, List.of());

            for (League parent : upperLeagues) {
                int childNum1 = 2 * parent.getLeagueNumber() - 1;
                int childNum2 = 2 * parent.getLeagueNumber();

                League child1 = lowerLeagues.stream()
                        .filter(l -> l.getLeagueNumber() == childNum1).findFirst().orElse(null);
                League child2 = lowerLeagues.stream()
                        .filter(l -> l.getLeagueNumber() == childNum2).findFirst().orElse(null);
                if (child1 == null || child2 == null) continue;

                List<UUID> pStand = standings.get(parent.getId());
                List<UUID> c1Stand = standings.get(child1.getId());
                List<UUID> c2Stand = standings.get(child2.getId());
                if (pStand.size() < 8 || c1Stand.isEmpty() || c2Stand.isEmpty()) continue;

                UUID rel1 = pStand.get(6);  // 7th place → relegated to child1
                UUID rel2 = pStand.get(7);  // 8th place → relegated to child2
                UUID pro1 = c1Stand.get(0); // 1st in child1 → promoted
                UUID pro2 = c2Stand.get(0); // 1st in child2 → promoted

                // Relegations
                swaps.add(new UUID[]{parent.getId(), child1.getId(), rel1});
                swaps.add(new UUID[]{parent.getId(), child2.getId(), rel2});
                // Promotions
                swaps.add(new UUID[]{child1.getId(), parent.getId(), pro1});
                swaps.add(new UUID[]{child2.getId(), parent.getId(), pro2});

                logTeamMovement(rel1, format, div, div + 1, false);
                logTeamMovement(rel2, format, div, div + 1, false);
                logTeamMovement(pro1, format, div + 1, div, true);
                logTeamMovement(pro2, format, div + 1, div, true);

                log.info("{} {} Div {}.{}: Relegated 7th & 8th ↔ Promoted 1st from {}.{} & {}.{}",
                        country, format, div, parent.getLeagueNumber(),
                        div + 1, childNum1, div + 1, childNum2);
            }
        }

        // Phase 3a: Remove teams from old leagues
        for (UUID[] swap : swaps) {
            leagueTeamRepository.findByLeagueId(swap[0]).stream()
                    .filter(lt -> lt.getTeam().getId().equals(swap[2]))
                    .findFirst()
                    .ifPresent(leagueTeamRepository::delete);
        }
        leagueTeamRepository.flush();

        // Phase 3b: Add teams to new leagues
        for (UUID[] swap : swaps) {
            League toLeague = leagueRepository.findById(swap[1]).orElse(null);
            Team team = teamRepository.findById(swap[2]).orElse(null);
            if (toLeague != null && team != null) {
                leagueTeamRepository.save(LeagueTeam.builder()
                        .league(toLeague).team(team).build());
            }
        }
    }

    /* ═══════════════ Day 2: Season Change ═══════════════ */

    private void processSeasonChange() {
        List<League> allLeagues = leagueRepository.findAll();
        for (League league : allLeagues) {
            league.setSeason(league.getSeason() + 1);
        }
        leagueRepository.saveAll(allLeagues);
        log.info("Season changed: all leagues incremented to season {}",
                allLeagues.isEmpty() ? "?" : allLeagues.get(0).getSeason());
    }

    /* ═══════════════ Day 2: Fixture Generation ═══════════════ */

    private void generateNewFixtures() {
        List<League> allLeagues = leagueRepository.findAll();
        int generated = 0;
        for (League league : allLeagues) {
            long before = fixtureRepository.countByLeagueIdAndStatusNot(league.getId(), "COMPLETED");
            if (before > 0) continue; // still has upcoming fixtures (e.g. FC R8-R14)
            fixtureService.generateFixtures(league);
            generated++;
        }
        log.info("Fixtures generated for {} leagues", generated);
    }

    /* ═══════════════ helpers ═══════════════ */

    /**
     * Compute league standings as an ordered list of team IDs (index 0 = 1st place).
     * Uses points (Win=2, Tie=1) then wins as tiebreaker.
     */
    private List<UUID> computeStandings(League league) {
        List<LeagueTeam> leagueTeams = leagueTeamRepository.findByLeagueId(league.getId());
        Map<UUID, int[]> stats = new LinkedHashMap<>(); // [points, wins]
        for (LeagueTeam lt : leagueTeams) {
            stats.put(lt.getTeam().getId(), new int[2]);
        }

        List<Fixture> fixtures = fixtureRepository.findByLeagueId(league.getId());
        for (Fixture f : fixtures) {
            if (!"COMPLETED".equals(f.getStatus())) continue;
            matchResultRepository.findByFixtureId(f.getId()).ifPresent(mr -> {
                List<Innings> innList = mr.getInningsList();
                if (innList.isEmpty()) return;
                UUID teamA = innList.get(0).getBattingTeam().getId();
                UUID teamB = innList.get(0).getBowlingTeam().getId();
                int[] sA = stats.get(teamA);
                int[] sB = stats.get(teamB);
                if (sA == null || sB == null) return;

                if ("TIE".equals(mr.getResultType())) {
                    sA[0] += 1;
                    sB[0] += 1;
                } else if (mr.getWinner() != null) {
                    UUID winnerId = mr.getWinner().getId();
                    if (winnerId.equals(teamA)) { sA[0] += 2; sA[1]++; }
                    else if (winnerId.equals(teamB)) { sB[0] += 2; sB[1]++; }
                }
            });
        }

        List<UUID> sorted = new ArrayList<>(stats.keySet());
        sorted.sort((a, b) -> {
            int cmp = Integer.compare(stats.get(b)[0], stats.get(a)[0]); // points DESC
            if (cmp != 0) return cmp;
            return Integer.compare(stats.get(b)[1], stats.get(a)[1]); // wins DESC
        });
        return sorted;
    }

    private void logTeamMovement(UUID teamId, String format, int fromDiv, int toDiv, boolean promoted) {
        teamRepository.findById(teamId).ifPresent(team -> {
            String action = promoted ? "Promoted" : "Relegated";
            activityLogService.log(team, "season",
                    String.format("%s in %s: Division %d → Division %d", action, format, fromDiv, toDiv));
        });
    }

    private int getAppStateInt(String key, int defaultVal) {
        return appStateRepository.findById(key)
                .map(s -> Integer.parseInt(s.getValue()))
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
                .team(team).type(type).amount(amount)
                .description(desc).balanceAfter(balanceAfter)
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
