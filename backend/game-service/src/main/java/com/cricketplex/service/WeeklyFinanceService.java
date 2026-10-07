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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyFinanceService {

    private static final String KEY = "last_weekly_finance_date";

    /* ── Academy maintenance per level ── */
    private static final Map<Integer, Long> ACADEMY_COST = Map.of(
            1, 10_000L, 2, 30_000L, 3, 65_000L, 4, 100_000L
    );

    /* ── Ground maintenance per seat ── */
    private static final double STANDING_COST = 0.20;
    private static final double ECONOMY_COST  = 0.40;
    private static final double STANDARD_COST = 0.70;
    private static final double PREMIUM_COST  = 1.00;

    /* ── Sponsorship ── */
    private static final long   SPONSOR_BASE         = 10_000;
    private static final double SPONSOR_MORALE_MAX   = 20_000;
    private static final double SPONSOR_FAN_MAX      = 15_000;
    private static final double SPONSOR_POSITION_MAX = 10_000;
    private static final double FAN_CAP              = 10_000.0;

    /* ── Interest ── */
    private static final double INTEREST_RATE = 0.02;
    private static final long   INTEREST_CAP  = 50_000;

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final StadiumSeatsRepository stadiumSeatsRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final FixtureRepository fixtureRepository;
    private final MatchResultRepository matchResultRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final AppStateRepository appStateRepository;
    private final ActivityLogService activityLogService;
    private final TransactionTemplate txTemplate;

    /* ═══════════════ scheduling + resilience ═══════════════ */

    @PostConstruct
    public void catchUpOnStartup() {
        log.info("Checking for missed weekly finance runs...");
        txTemplate.executeWithoutResult(status -> applyMissedWeeks());
    }

    @Scheduled(cron = "0 5 0 * * SAT", zone = "UTC")
    public void scheduledWeeklyFinance() {
        applyMissedWeeks();
    }

    @Transactional
    public void applyMissedWeeks() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        AppState state = appStateRepository.findById(KEY).orElse(null);

        LocalDate lastProcessed;
        if (state == null) {
            /* first run ever — initialise to the most recent Saturday so we
               don't retroactively charge for weeks before the feature existed */
            LocalDate lastSat = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
            state = new AppState(KEY, lastSat.toString());
            appStateRepository.save(state);
            lastProcessed = lastSat;
        } else {
            lastProcessed = LocalDate.parse(state.getValue());
        }

        /* walk forward one Saturday at a time */
        LocalDate saturday = lastProcessed.plusWeeks(1);
        /* ensure we land on a Saturday (safety) */
        while (saturday.getDayOfWeek() != DayOfWeek.SATURDAY) saturday = saturday.plusDays(1);

        int processed = 0;
        while (!saturday.isAfter(today)) {
            processWeeklyFinance();
            saturday = saturday.plusWeeks(1);
            processed++;
            if (processed >= 12) {
                log.warn("Capped weekly finance catch-up at 12 weeks");
                break;
            }
        }

        /* update state to the last Saturday we processed */
        LocalDate latestDone = saturday.minusWeeks(1);
        if (latestDone.isAfter(lastProcessed)) {
            state.setValue(latestDone.toString());
            appStateRepository.save(state);
            log.info("Weekly finance: processed {} week(s), updated to {}", processed, latestDone);
        } else {
            log.info("Weekly finance already up-to-date (last: {})", lastProcessed);
        }
    }

    /* ═══════════════ core processing ═══════════════ */

    private void processWeeklyFinance() {
        List<Team> allTeams = teamRepository.findAll();
        Map<UUID, Integer> bestPosition = computeBestPositions();
        Map<UUID, Integer> leagueSizes = computeLeagueSizes();

        for (Team team : allTeams) {
            int leagueSize = leagueSizes.getOrDefault(team.getId(), 8);
            int position   = bestPosition.getOrDefault(team.getId(), (leagueSize + 1) / 2);

            long sponsorship = calcSponsorship(team, position, leagueSize);
            long interest    = calcInterest(team.getFunds());
            long academyCost = ACADEMY_COST.getOrDefault(team.getAcademyLevel(), 10_000L);
            long salary      = calcSalary(team);
            long groundCost  = calcGroundCost(team);

            long balance = team.getFunds();

            /* credits */
            balance += sponsorship;
            saveTx(team, "SPONSORSHIP", sponsorship,
                    String.format("Sponsorship (morale %d, fans %s, pos %d/%d)",
                            team.getMorale(), fmtNum(team.getFans()), position, leagueSize),
                    balance);

            balance += interest;
            saveTx(team, "INTEREST", interest,
                    "Interest on funds (2%, cap $50K)", balance);

            /* debits */
            balance -= academyCost;
            saveTx(team, "ACADEMY_MAINTENANCE", -academyCost,
                    "Academy Level " + team.getAcademyLevel() + " maintenance", balance);

            int playerCount = playerRepository.findByTeamId(team.getId()).size();
            balance -= salary;
            saveTx(team, "PLAYER_SALARY", -salary,
                    "Player salaries (" + playerCount + " players)", balance);

            balance -= groundCost;
            saveTx(team, "GROUND_MAINTENANCE", -groundCost,
                    "Ground maintenance", balance);

            team.setFunds(balance);
            teamRepository.save(team);

            /* activity log */
            long net = (sponsorship + interest) - (academyCost + salary + groundCost);
            String dir = net >= 0 ? "profit" : "loss";
            activityLogService.log(team, "finance",
                    String.format("Weekly financial update: %s$%s (%s)",
                            net >= 0 ? "+" : "-", fmtNum(Math.abs(net)), dir));
        }
    }

    /* ═══════════════ calculations ═══════════════ */

    private long calcSponsorship(Team team, int position, int leagueSize) {
        double moraleBonus  = (team.getMorale() / 100.0) * SPONSOR_MORALE_MAX;
        double fanBonus     = (Math.min(team.getFans(), FAN_CAP) / FAN_CAP) * SPONSOR_FAN_MAX;
        double posBonus     = ((double)(leagueSize + 1 - position) / leagueSize) * SPONSOR_POSITION_MAX;
        return SPONSOR_BASE + Math.round(moraleBonus) + Math.round(fanBonus) + Math.round(posBonus);
    }

    private long calcInterest(long funds) {
        if (funds <= 0) return 0;
        return Math.min(Math.round(funds * INTEREST_RATE), INTEREST_CAP);
    }

    private long calcSalary(Team team) {
        return playerRepository.findByTeamId(team.getId())
                .stream().mapToLong(Player::getWage).sum();
    }

    private long calcGroundCost(Team team) {
        StadiumSeats seats = stadiumSeatsRepository.findByTeam(team).orElse(null);
        if (seats == null) return 0;
        return Math.round(
                seats.getStanding() * STANDING_COST
              + seats.getEconomy()  * ECONOMY_COST
              + seats.getStandard() * STANDARD_COST
              + seats.getPremium()  * PREMIUM_COST
        );
    }

    /* ═══════════════ league position helpers ═══════════════ */

    private Map<UUID, Integer> computeBestPositions() {
        Map<UUID, Integer> bestPos = new HashMap<>();

        List<LeagueTeam> allLt = leagueTeamRepository.findAll();
        Map<UUID, List<LeagueTeam>> byLeague = allLt.stream()
                .collect(Collectors.groupingBy(lt -> lt.getLeague().getId()));

        for (var entry : byLeague.entrySet()) {
            UUID leagueId = entry.getKey();
            List<LeagueTeam> teams = entry.getValue();

            // [points, wins]
            Map<UUID, int[]> stats = new HashMap<>();
            for (LeagueTeam lt : teams) stats.put(lt.getTeam().getId(), new int[2]);

            List<Fixture> fixtures = fixtureRepository.findByLeagueId(leagueId);

            List<UUID> completedFixtureIds = new ArrayList<>();
            for (Fixture f : fixtures) {
                if ("COMPLETED".equals(f.getStatus())) completedFixtureIds.add(f.getId());
            }

            Map<UUID, MatchResult> resultsByFixture = new HashMap<>();
            if (!completedFixtureIds.isEmpty()) {
                for (MatchResult result : matchResultRepository.findAllByFixtureIds(completedFixtureIds)) {
                    if (result.getFixture() != null) {
                        resultsByFixture.put(result.getFixture().getId(), result);
                    }
                }
            }

            for (Fixture f : fixtures) {
                if (!"COMPLETED".equals(f.getStatus())) continue;
                MatchResult mr = resultsByFixture.get(f.getId());
                if (mr == null) continue;

                UUID homeId = f.getHomeTeam().getId();
                UUID awayId = f.getAwayTeam().getId();

                if ("TIE".equals(mr.getResultType())) {
                    addPts(stats, homeId, 1, 0);
                    addPts(stats, awayId, 1, 0);
                } else if (mr.getWinner() != null) {
                    addPts(stats, mr.getWinner().getId(), 2, 1);
                }
            }

            List<UUID> sorted = new ArrayList<>(stats.keySet());
            sorted.sort((a, b) -> {
                int cmp = Integer.compare(stats.get(b)[0], stats.get(a)[0]);
                return cmp != 0 ? cmp : Integer.compare(stats.get(b)[1], stats.get(a)[1]);
            });

            for (int i = 0; i < sorted.size(); i++) {
                bestPos.merge(sorted.get(i), i + 1, Math::min);
            }
        }
        return bestPos;
    }

    private Map<UUID, Integer> computeLeagueSizes() {
        Map<UUID, Integer> sizes = new HashMap<>();
        List<LeagueTeam> allLt = leagueTeamRepository.findAll();
        Map<UUID, List<LeagueTeam>> byLeague = allLt.stream()
                .collect(Collectors.groupingBy(lt -> lt.getLeague().getId()));

        for (var entry : byLeague.entrySet()) {
            int size = entry.getValue().size();
            for (LeagueTeam lt : entry.getValue()) {
                sizes.merge(lt.getTeam().getId(), size, Math::max);
            }
        }
        return sizes;
    }

    private void addPts(Map<UUID, int[]> stats, UUID teamId, int pts, int wins) {
        int[] s = stats.get(teamId);
        if (s != null) { s[0] += pts; s[1] += wins; }
    }

    /* ═══════════════ transaction logging ═══════════════ */

    private void saveTx(Team team, String type, long amount, String desc, long balanceAfter) {
        transactionLogRepository.save(TransactionLog.builder()
                .team(team)
                .type(type)
                .amount(amount)
                .description(desc)
                .balanceAfter(balanceAfter)
                .build());
    }

    private String fmtNum(long n) {
        return String.format("%,d", n);
    }
}
