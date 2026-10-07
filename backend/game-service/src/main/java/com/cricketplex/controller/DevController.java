package com.cricketplex.controller;

import com.cricketplex.entity.Fixture;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.service.MatchEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Dev-only endpoints for testing. ADMIN role required.
 * DO NOT expose in production.
 */
@RestController
@RequestMapping("/api/admin/dev")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class DevController {

    /** Must stay in sync with FixtureService / SeasonalUpdateService. */
    @org.springframework.beans.factory.annotation.Value("${app.season1-start}")
    private String season1StartStr;
    private LocalDate getSeason1Start() { return LocalDate.parse(season1StartStr); }
    private static final int SEASON_DAYS = 56;

    /** Parallel threads for simulation. Kept low to avoid deadlocks on shared team/player rows. */
    private static final int POOL_SIZE = 8;
    private static final int MAX_RETRIES = 4;

    private final FixtureRepository fixtureRepository;
    private final MatchEngine matchEngine;

    /**
     * Fast-forward the season by instantly simulating all LEAGUE fixtures
     * whose match date falls strictly before the start of {@code targetWeek}.
     *
     * Runs in two parallel phases:
     *   Phase 1 — T20/ODI SCHEDULED + FC Day 1 SCHEDULED  (all parallel)
     *   Phase 2 — FC Day 2: pre-existing FC_DAY1_COMPLETE + any that just
     *              finished Day 1 in Phase 1               (all parallel)
     *
     * COMPLETED and IN_PROGRESS fixtures are never touched.
     *
     * Example: POST /api/admin/dev/fast-forward?targetWeek=7
     */
    @PostMapping("/fast-forward")
    public ResponseEntity<?> fastForward(
            @RequestParam(defaultValue = "7") int targetWeek) {

        if (targetWeek < 2 || targetWeek > 9) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "targetWeek must be between 2 and 9"));
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate SEASON_1_START = getSeason1Start();
        long daysSince = ChronoUnit.DAYS.between(SEASON_1_START, today);
        int currentSeason = 1 + (int) Math.max(0, daysSince / SEASON_DAYS);
        LocalDate seasonStart = SEASON_1_START.plusDays((long)(currentSeason - 1) * SEASON_DAYS);
        LocalDate cutoffDate  = seasonStart.plusWeeks(targetWeek - 1);

        log.info("FastForward: season={} cutoff={} (weeks 1–{})", currentSeason, cutoffDate, targetWeek - 1);

        List<Fixture> all = fixtureRepository.findAll().stream()
                .filter(f -> "LEAGUE".equals(f.getMatchType()))
                .filter(f -> f.getMatchDate() != null && f.getMatchDate().isBefore(cutoffDate))
                .filter(f -> "SCHEDULED".equals(f.getStatus()) || "FC_DAY1_COMPLETE".equals(f.getStatus()))
                .collect(Collectors.toList());

        log.info("FastForward: {} candidate fixtures (matchDate < {})", all.size(), cutoffDate);

        if (all.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "season", currentSeason, "targetWeek", targetWeek,
                    "cutoffDate", cutoffDate.toString(), "weeksCleared", targetWeek - 1,
                    "simulated", 0, "skipped", 0, "errors", List.of()));
        }

        // Partition into the three groups
        List<Fixture> limitedOvers = all.stream()
                .filter(f -> !"FC".equalsIgnoreCase(getFormat(f)))
                .filter(f -> "SCHEDULED".equals(f.getStatus()))
                .collect(Collectors.toList());

        List<Fixture> fcDay1 = all.stream()
                .filter(f -> "FC".equalsIgnoreCase(getFormat(f)))
                .filter(f -> "SCHEDULED".equals(f.getStatus()))
                .collect(Collectors.toList());

        List<Fixture> fcDay2Existing = all.stream()
                .filter(f -> "FC_DAY1_COMPLETE".equals(f.getStatus()))
                .collect(Collectors.toList());

        AtomicInteger simulated = new AtomicInteger(0);
        AtomicInteger skipped   = new AtomicInteger(0);
        List<String> errors = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);
        try {
            // Phase 1: T20/ODI + FC Day 1, all in parallel
            List<Fixture> phase1 = new ArrayList<>();
            phase1.addAll(limitedOvers);
            phase1.addAll(fcDay1);
            runParallel(phase1, pool, simulated, skipped, errors);

            // Phase 2: FC Day 2 — must wait for Phase 1 to finish first
            List<UUID> fcDay1Ids = fcDay1.stream().map(Fixture::getId).collect(Collectors.toList());
            List<Fixture> newlyDay2 = fcDay1Ids.isEmpty() ? List.of()
                    : fixtureRepository.findAllById(fcDay1Ids).stream()
                            .filter(f -> "FC_DAY1_COMPLETE".equals(f.getStatus()))
                            .collect(Collectors.toList());

            List<Fixture> phase2 = new ArrayList<>();
            phase2.addAll(fcDay2Existing);
            phase2.addAll(newlyDay2);
            runParallel(phase2, pool, simulated, skipped, errors);

        } finally {
            pool.shutdown();
        }

        return ResponseEntity.ok(Map.of(
                "season",       currentSeason,
                "targetWeek",   targetWeek,
                "cutoffDate",   cutoffDate.toString(),
                "weeksCleared", targetWeek - 1,
                "simulated",    simulated.get(),
                "skipped",      skipped.get(),
                "errors",       errors));
    }

    private void runParallel(List<Fixture> fixtures, ExecutorService pool,
                             AtomicInteger simulated, AtomicInteger skipped,
                             List<String> errors) {
        if (fixtures.isEmpty()) return;

        List<CompletableFuture<Void>> futures = fixtures.stream()
                .map(f -> CompletableFuture.runAsync(() ->
                        simulateWithRetry(f, simulated, skipped, errors), pool))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Simulate a single fixture, retrying up to MAX_RETRIES times on deadlock.
     * PostgreSQL aborts the transaction on deadlock, so each retry is a fresh attempt.
     */
    private void simulateWithRetry(Fixture f, AtomicInteger simulated,
                                   AtomicInteger skipped, List<String> errors) {
        Exception lastEx = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            MatchEngine.setSkipBallEvents(true);
            try {
                matchEngine.simulateMatch(f.getId());
                simulated.incrementAndGet();
                if (attempt > 1) {
                    log.info("FastForward: done R{} {} {} (attempt {})",
                            f.getRound(), getFormat(f), f.getMatchDate(), attempt);
                } else {
                    log.info("FastForward: done R{} {} {}", f.getRound(), getFormat(f), f.getMatchDate());
                }
                return; // success
            } catch (Exception e) {
                lastEx = e;
                boolean isDeadlock = e.getMessage() != null
                        && e.getMessage().toLowerCase().contains("deadlock");
                if (isDeadlock && attempt < MAX_RETRIES) {
                    long backoff = 200L * attempt + (long)(Math.random() * 300);
                    log.warn("FastForward deadlock R{} {} attempt {}/{} — retrying in {}ms",
                            f.getRound(), f.getId(), attempt, MAX_RETRIES, backoff);
                    try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    break; // non-deadlock error or retries exhausted
                }
            } finally {
                MatchEngine.setSkipBallEvents(false);
            }
        }
        // All attempts failed
        skipped.incrementAndGet();
        String msg = lastEx != null ? lastEx.getMessage() : "unknown";
        errors.add(String.format("Fixture %s R%d (%s): %s", f.getId(), f.getRound(), f.getMatchDate(), msg));
        log.warn("FastForward failed fixture {} R{} after {} attempts: {}",
                f.getId(), f.getRound(), MAX_RETRIES, msg);
    }

    private static String getFormat(Fixture f) {
        return f.getLeague() != null ? f.getLeague().getFormat() : f.getFormat();
    }
}
