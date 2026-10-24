package com.cricketplex.service;

import com.cricketplex.entity.BallEvent;
import com.cricketplex.entity.Fixture;
import com.cricketplex.entity.FriendlyChallenge;
import com.cricketplex.entity.Innings;
import com.cricketplex.entity.League;
import com.cricketplex.entity.MatchLineup;
import com.cricketplex.entity.MatchResult;
import com.cricketplex.entity.Cup;
import com.cricketplex.repository.BallEventRepository;
import com.cricketplex.repository.CupRepository;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.FriendlyChallengeRepository;
import com.cricketplex.repository.LeagueRepository;
import com.cricketplex.repository.LeagueTeamRepository;
import com.cricketplex.repository.MatchLineupRepository;
import com.cricketplex.repository.MatchResultRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Runs every 60 seconds.
 * Uses TransactionTemplate so each fixture gets its own committed transaction.
 * If one fixture fails, others still succeed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchScheduler {

    private static final int BALL_INTERVAL_T20_SECONDS = 24;
    private static final int BALL_INTERVAL_OD_SECONDS  = 22;
    private static final int BALL_INTERVAL_FC_SECONDS  = 20;
    private static final int INNINGS_BREAK_SECONDS = 300;
    private static final int SESSION_BREAK_SECONDS = 180;
    private static final int SESSION_OVERS = 50;

    private final FixtureRepository fixtureRepository;
    private final MatchResultRepository matchResultRepository;
    private final BallEventRepository ballEventRepository;
    private final MatchEngine matchEngine;
    private final TransactionTemplate txTemplate;
    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final FixtureService fixtureService;
    private final CupService cupService;
    private final CupRepository cupRepository;
    private final FriendlyChallengeRepository friendlyChallengeRepository;
    private final MatchLineupRepository matchLineupRepository;

    @Autowired
    @Qualifier("matchStartExecutor")
    private Executor matchStartExecutor;

    private static final int START_RETRIES = 4;

    private int getBallIntervalSeconds(String format) {
        if ("FC".equalsIgnoreCase(format)) return BALL_INTERVAL_FC_SECONDS;
        if ("OD".equalsIgnoreCase(format) || "ODI".equalsIgnoreCase(format)) return BALL_INTERVAL_OD_SECONDS;
        return BALL_INTERVAL_T20_SECONDS;
    }

    /**
     * Counts FC session breaks by walking over boundaries across innings.
     * A session break occurs after every completed SESSION_OVERS overs.
     */
    private int computeSessionBreakCount(MatchResult result) {
        String fmt = result.getFixture().getLeague() != null
                ? result.getFixture().getLeague().getFormat()
                : result.getFixture().getFormat();
        if (!"FC".equalsIgnoreCase(fmt)) return 0;

        int cumulativeOvers = 0;
        int sessionBreaks = 0;

        for (Innings inn : result.getInningsList()) {
            List<BallEvent> events = ballEventRepository.findByInningsIdOrderByOverNumberAscBallNumberAsc(inn.getId());
            int lastSeenOver = 0;
            for (int j = 0; j < events.size(); j++) {
                int curOver = events.get(j).getOverNumber(); // 1-based
                if (curOver != lastSeenOver) {
                    if (lastSeenOver > 0) {
                        cumulativeOvers++;
                        if (cumulativeOvers % SESSION_OVERS == 0) {
                            sessionBreaks++;
                        }
                    }
                    lastSeenOver = curOver;
                }
            }
            if (inn.getTotalOvers() != null && inn.getTotalOvers() > 0
                    && Math.abs(inn.getTotalOvers() - Math.floor(inn.getTotalOvers())) < 0.01) {
                cumulativeOvers++;
            }
        }
        return sessionBreaks;
    }

    /**
     * On startup, generate fixtures for any league that has 8 teams but no fixtures
     * for its current season. This covers leagues created via DB migration (V9/V10) +
     * bot assignment that predates the eager fixture generation in createLeague(), and
     * heals bot-only leagues whose season rollover fixture generation was missed
     * (they have no human visitors to trigger lazy generation).
     */
    @PostConstruct
    public void generateMissingFixturesOnStartup() {
        try {
            List<League> allLeagues = leagueRepository.findAll();
            int generated = 0;
            for (League league : allLeagues) {
                long teamCount = leagueTeamRepository.countByLeagueIdAndSeason(league.getId(), league.getSeason());
                if (teamCount >= 8 && fixtureRepository.countByLeagueIdAndSeason(league.getId(), league.getSeason()) == 0) {
                    try {
                        txTemplate.executeWithoutResult(status ->
                            fixtureService.generateFixtures(league)
                        );
                        generated++;
                        log.info("Startup: generated fixtures for {} {} {}.{}",
                                league.getCountry(), league.getFormat(),
                                league.getDivision(), league.getLeagueNumber());
                    } catch (Exception e) {
                        log.error("Startup: failed to generate fixtures for league {}: {}",
                                league.getId(), e.getMessage());
                    }
                }
            }
            if (generated > 0) {
                log.info("Startup: generated fixtures for {} leagues", generated);
            } else {
                log.info("Startup: all leagues already have fixtures");
            }
        } catch (Exception e) {
            log.error("Startup fixture check error: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void autoSimulateMatches() {
        try {
            LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
            LocalDate today = nowUtc.toLocalDate();

            // Read candidates in a short read-only transaction
            List<Fixture> candidates = txTemplate.execute(status -> 
                fixtureRepository.findScheduledWithLeague("SCHEDULED", today)
            );

            if (candidates == null || candidates.isEmpty()) {
                log.debug("MatchScheduler: no league SCHEDULED candidates for {} or earlier", today);
            } else {
                log.info("MatchScheduler: {} SCHEDULED candidates for {} or earlier", candidates.size(), today);
                List<Runnable> leagueStarts = new ArrayList<>();
                for (Fixture f : candidates) {
                    if (f.getLeague() == null) continue;
                    String startTimeStr = f.getLeague().getMatchStartTime();
                    if (startTimeStr == null) startTimeStr = "14:00";
                    LocalDateTime matchStart = LocalDateTime.of(f.getMatchDate(), LocalTime.parse(startTimeStr));
                    if (nowUtc.isBefore(matchStart)) continue;
                    leagueStarts.add(() -> startDueLeagueFixture(f, matchStart));
                }
                runStartsInParallel(leagueStarts);
            }

            // ── Pass 2: Auto-complete IN_PROGRESS fixtures (league + friendly) ──
            List<Fixture> inProgress = txTemplate.execute(status ->
                fixtureRepository.findByStatus("IN_PROGRESS")
            );

            if (inProgress != null) {
                for (Fixture f : inProgress) {
                    try {
                        txTemplate.executeWithoutResult(status -> {
                            matchResultRepository.findByFixtureIdWithInnings(f.getId()).ifPresent(mr -> {
                                if (isMatchTimeElapsed(mr)) {
                                    Fixture fresh = fixtureRepository.findById(f.getId()).orElse(null);
                                    if (fresh != null) {
                                        String format = fresh.getLeague() != null ? fresh.getLeague().getFormat() : fresh.getFormat();
                                        boolean isFcDay1Partial = "FC".equalsIgnoreCase(format)
                                                && "PENDING".equals(mr.getResultType());
                                        if (isFcDay1Partial) {
                                            fresh.setFcDay(1);
                                            fresh.setStatus("FC_DAY1_COMPLETE");
                                        } else {
                                            fresh.setStatus("COMPLETED");
                                            // Log match activity now that the live window has ended
                                            if (fresh.getSimSessionId() == null) {
                                                matchEngine.logMatchActivity(mr, fresh);
                                            }
                                        }
                                        fixtureRepository.save(fresh);
                                        log.info("Updated fixture {} after live duration elapsed to {}",
                                                f.getId(), fresh.getStatus());
                                    }
                                }
                            });
                        });
                    } catch (Exception e) {
                        log.error("Failed to auto-complete fixture {}: {}", f.getId(), e.getMessage());
                    }
                }
            }

            // ── Pass 3: FC Day 2 — simulate remaining overs for FC_DAY1_COMPLETE fixtures ──
            List<Fixture> fcDay1Done = txTemplate.execute(status ->
                fixtureRepository.findByStatus("FC_DAY1_COMPLETE")
            );

            if (fcDay1Done != null) {
                List<Runnable> day2Starts = new ArrayList<>();
                for (Fixture f : fcDay1Done) {
                    if (f.getFcDay() != null && f.getFcDay() != 1) continue;
                    LocalDate day2Date = f.getMatchDate();
                    if (today.isBefore(day2Date)) continue;
                    String startTimeStr;
                    if (f.getLeague() != null) {
                        startTimeStr = f.getLeague().getMatchStartTime();
                        if (startTimeStr == null) startTimeStr = "14:00";
                    } else {
                        FriendlyChallenge fc = friendlyChallengeRepository.findByFixtureId(f.getId()).orElse(null);
                        startTimeStr = (fc != null && fc.getMatchTime() != null) ? fc.getMatchTime() : "14:00";
                    }
                    LocalDateTime day2Start = LocalDateTime.of(day2Date, LocalTime.parse(startTimeStr));
                    if (nowUtc.isBefore(day2Start)) continue;
                    day2Starts.add(() -> startDueFcDay2(f, day2Start));
                }
                runStartsInParallel(day2Starts);
            }

            // ── Pass 4: Expire PENDING challenges whose scheduled time has passed ──
            try {
                List<FriendlyChallenge> pending = txTemplate.execute(status ->
                    friendlyChallengeRepository.findByStatus("PENDING")
                );
                if (pending != null) {
                    for (FriendlyChallenge fc : pending) {
                        try {
                            if (fc.getMatchTime() == null) continue;
                            LocalDateTime matchDateTime = LocalDateTime.of(fc.getMatchDate(), LocalTime.parse(fc.getMatchTime()));
                            if (!nowUtc.isAfter(matchDateTime)) continue;

                            txTemplate.executeWithoutResult(status -> {
                                FriendlyChallenge fresh = friendlyChallengeRepository.findById(fc.getId()).orElse(null);
                                if (fresh != null && "PENDING".equals(fresh.getStatus())) {
                                    fresh.setStatus("EXPIRED");
                                    friendlyChallengeRepository.save(fresh);
                                    log.info("Expired PENDING challenge {} (time {} passed)", fc.getId(), fc.getMatchTime());
                                }
                            });
                        } catch (Exception e) {
                            log.error("Failed to expire challenge {}: {}", fc.getId(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Pass 4 (expire challenges) error: {}", e.getMessage());
            }

            // ── Pass 5: Auto-start ACCEPTED friendly matches at their scheduled time ──
            try {
                List<FriendlyChallenge> accepted = txTemplate.execute(status ->
                    friendlyChallengeRepository.findByStatus("ACCEPTED")
                );
                if (accepted != null) {
                    List<Runnable> friendlyStarts = new ArrayList<>();
                    for (FriendlyChallenge fc : accepted) {
                        if (fc.getMatchTime() == null || fc.getFixture() == null) continue;
                        LocalDateTime matchDateTime = LocalDateTime.of(fc.getMatchDate(), LocalTime.parse(fc.getMatchTime()));
                        if (nowUtc.isBefore(matchDateTime)) continue;
                        friendlyStarts.add(() -> startDueFriendly(fc, matchDateTime));
                    }
                    runStartsInParallel(friendlyStarts);
                }
            } catch (Exception e) {
                log.error("Pass 5 (auto-start friendlies) error: {}", e.getMessage());
            }

            // ── Pass 6: Initialise Cup for current season if not yet done ─────
            try {
                int currentSeason = cupService.computeCurrentSeason();
                LocalDate seasonStart = cupService.getSeasonStart(currentSeason);
                if (!today.isBefore(seasonStart) && !cupRepository.existsBySeason(currentSeason)) {
                    log.info("MatchScheduler: initialising Cup for season {}", currentSeason);
                    txTemplate.executeWithoutResult(status ->
                        cupService.initializeCupForSeason(currentSeason)
                    );
                }
            } catch (Exception e) {
                log.error("Pass 6 (cup init) error: {}", e.getMessage(), e);
            }

            // ── Pass 7: Simulate SCHEDULED cup fixtures ───────────────────────
            try {
                List<Fixture> cupCandidates = txTemplate.execute(status ->
                    fixtureRepository.findByMatchTypeAndStatusAndMatchDateLessThanEqual(
                            "CUP", "SCHEDULED", today)
                );
                if (cupCandidates != null) {
                    List<Runnable> cupStarts = new ArrayList<>();
                    for (Fixture f : cupCandidates) {
                        String startTimeStr = f.getMatchTime() != null
                                ? f.getMatchTime() : CupService.CUP_MATCH_TIME;
                        LocalDateTime matchStart = LocalDateTime.of(
                                f.getMatchDate(), LocalTime.parse(startTimeStr));
                        if (nowUtc.isBefore(matchStart)) continue;
                        cupStarts.add(() -> startDueCupFixture(f, matchStart));
                    }
                    runStartsInParallel(cupStarts);
                }
            } catch (Exception e) {
                log.error("Pass 7 (cup simulation) error: {}", e.getMessage(), e);
            }

            // ── Pass 8: Advance cup rounds when all matches in a round complete ─
            try {
                List<Cup> ongoingCups = txTemplate.execute(status ->
                    cupRepository.findByStatus("ONGOING")
                );
                if (ongoingCups != null) {
                    for (Cup cup : ongoingCups) {
                        try {
                            txTemplate.executeWithoutResult(status ->
                                cupService.checkAndAdvanceCupRound(cup.getId())
                            );
                        } catch (Exception e) {
                            log.error("Failed to advance cup {}: {}", cup.getId(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Pass 8 (cup advance) error: {}", e.getMessage(), e);
            }

        } catch (Exception e) {
            log.error("MatchScheduler top-level error: {}", e.getMessage(), e);
        }
    }

    private boolean isMatchTimeElapsed(MatchResult result) {
        if (result.getCreatedAt() == null) return true;

        long totalBalls = 0;
        for (Innings inn : result.getInningsList()) {
            totalBalls += ballEventRepository.countByInningsId(inn.getId());
        }
        int inningsCount = result.getInningsList().size();
        int breakCount = inningsCount > 1 ? inningsCount - 1 : 0;
        int sessionBreakCount = computeSessionBreakCount(result);
        String format = result.getFixture().getLeague() != null
                ? result.getFixture().getLeague().getFormat()
                : result.getFixture().getFormat();

        long totalSeconds = totalBalls * getBallIntervalSeconds(format)
                + (long) breakCount * INNINGS_BREAK_SECONDS
                + (long) sessionBreakCount * SESSION_BREAK_SECONDS;
        long elapsed = java.time.Duration.between(result.getCreatedAt(), LocalDateTime.now()).getSeconds();

        return elapsed >= totalSeconds;
    }

    private void runStartsInParallel(List<Runnable> jobs) {
        if (jobs == null || jobs.isEmpty()) return;
        CompletableFuture<?>[] futures = jobs.stream()
                .map(job -> CompletableFuture.runAsync(job, matchStartExecutor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();
    }

    private void startWithRetry(String label, UUID id, Runnable attempt) {
        Exception last = null;
        for (int i = 1; i <= START_RETRIES; i++) {
            try {
                attempt.run();
                return;
            } catch (Exception e) {
                last = e;
                log.warn("Match start retry {}/{} for {} {}: {}", i, START_RETRIES, label, id, e.getMessage());
                try {
                    Thread.sleep(50L * i);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.error("Failed to start {} {}: {}", label, id, last != null ? last.getMessage() : "unknown");
    }

    private void stampLiveStart(UUID fixtureId, LocalDateTime kickoff) {
        if (fixtureId == null || kickoff == null) return;
        txTemplate.executeWithoutResult(status ->
                matchResultRepository.updateCreatedAt(fixtureId, kickoff)
        );
    }

    private void startDueLeagueFixture(Fixture f, LocalDateTime kickoff) {
        startWithRetry("league fixture", f.getId(), () -> {
            Boolean hasResult = txTemplate.execute(status ->
                    matchResultRepository.existsByFixtureId(f.getId())
            );
            if (Boolean.TRUE.equals(hasResult)) {
                txTemplate.executeWithoutResult(status -> {
                    Fixture fresh = fixtureRepository.findById(f.getId()).orElse(null);
                    if (fresh != null && "SCHEDULED".equals(fresh.getStatus())) {
                        fresh.setStatus("IN_PROGRESS");
                        fixtureRepository.save(fresh);
                    }
                });
                return;
            }

            log.info("Auto-simulating fixture {} (league {} R{}, date={}, start={})",
                    f.getId(), f.getLeague().getId(), f.getRound(),
                    f.getMatchDate(), kickoff.toLocalTime());

            matchEngine.simulateMatch(f.getId());

            txTemplate.executeWithoutResult(status -> {
                Fixture fresh = fixtureRepository.findById(f.getId()).orElse(null);
                if (fresh != null && ("COMPLETED".equals(fresh.getStatus()) || "FC_DAY1_COMPLETE".equals(fresh.getStatus()))) {
                    fresh.setStatus("IN_PROGRESS");
                    fixtureRepository.save(fresh);
                }
            });
            stampLiveStart(f.getId(), kickoff);
        });
    }

    private void startDueFcDay2(Fixture f, LocalDateTime kickoff) {
        startWithRetry("FC Day 2", f.getId(), () -> {
            log.info("Auto-simulating FC Day 2 for fixture {} (day2Date={})", f.getId(), f.getMatchDate());
            matchEngine.simulateMatch(f.getId());
            txTemplate.executeWithoutResult(status -> {
                Fixture fresh = fixtureRepository.findById(f.getId()).orElse(null);
                if (fresh != null && "COMPLETED".equals(fresh.getStatus())) {
                    fresh.setStatus("IN_PROGRESS");
                    fixtureRepository.save(fresh);
                    if (fresh.getLeague() == null) {
                        friendlyChallengeRepository.findByFixtureId(fresh.getId()).ifPresent(challenge -> {
                            challenge.setStatus("COMPLETED");
                            friendlyChallengeRepository.save(challenge);
                        });
                    }
                }
            });
            stampLiveStart(f.getId(), kickoff);
        });
    }

    private void startDueFriendly(FriendlyChallenge fc, LocalDateTime kickoff) {
        UUID fixtureId = fc.getFixture().getId();
        startWithRetry("friendly", fc.getId(), () -> {
            Boolean hasResult = txTemplate.execute(status ->
                    matchResultRepository.existsByFixtureId(fixtureId)
            );
            if (Boolean.TRUE.equals(hasResult)) return;

            log.info("Auto-starting friendly match {} (challenge {}, time={})",
                    fixtureId, fc.getId(), fc.getMatchTime());

            matchEngine.simulateMatch(fixtureId);

            txTemplate.executeWithoutResult(status -> {
                Fixture fresh = fixtureRepository.findById(fixtureId).orElse(null);
                FriendlyChallenge fcFresh = friendlyChallengeRepository.findById(fc.getId()).orElse(null);

                if (fresh != null && "FC_DAY1_COMPLETE".equals(fresh.getStatus())) {
                    fresh.setStatus("IN_PROGRESS");
                    fixtureRepository.save(fresh);
                    if (fcFresh != null) {
                        fcFresh.setMatchDate(fresh.getMatchDate());
                        friendlyChallengeRepository.save(fcFresh);
                    }
                } else if (fresh != null && "COMPLETED".equals(fresh.getStatus())) {
                    fresh.setStatus("IN_PROGRESS");
                    fixtureRepository.save(fresh);
                    if (fcFresh != null) {
                        fcFresh.setStatus("COMPLETED");
                        friendlyChallengeRepository.save(fcFresh);
                    }
                }
            });
            stampLiveStart(fixtureId, kickoff);
        });
    }

    private void startDueCupFixture(Fixture f, LocalDateTime kickoff) {
        startWithRetry("cup fixture", f.getId(), () -> {
            Boolean hasResult = txTemplate.execute(status ->
                    matchResultRepository.existsByFixtureId(f.getId())
            );
            if (Boolean.TRUE.equals(hasResult)) return;

            log.info("Auto-simulating cup fixture {} R{} M{} ({})",
                    f.getId(), f.getRound(), f.getMatchNumber(), f.getMatchDate());

            matchEngine.simulateMatch(f.getId());

            txTemplate.executeWithoutResult(status -> {
                Fixture fresh = fixtureRepository.findById(f.getId()).orElse(null);
                if (fresh != null && "COMPLETED".equals(fresh.getStatus())) {
                    fresh.setStatus("IN_PROGRESS");
                    fixtureRepository.save(fresh);
                }
            });
            stampLiveStart(f.getId(), kickoff);
        });
    }
}
