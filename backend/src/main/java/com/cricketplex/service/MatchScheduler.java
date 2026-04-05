package com.cricketplex.service;

import com.cricketplex.entity.Fixture;
import com.cricketplex.entity.Innings;
import com.cricketplex.entity.MatchResult;
import com.cricketplex.repository.BallEventRepository;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.MatchResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Runs every 60 seconds.
 * 1. Finds all SCHEDULED league fixtures whose date+time (UTC) has passed,
 *    simulates them, and sets them to IN_PROGRESS for live ball-by-ball viewing.
 * 2. Finds all IN_PROGRESS league fixtures whose simulation time has elapsed,
 *    and transitions them to COMPLETED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchScheduler {

    private static final int BALL_INTERVAL_SECONDS = 5;
    private static final int INNINGS_BREAK_SECONDS = 300;

    private final FixtureRepository fixtureRepository;
    private final MatchResultRepository matchResultRepository;
    private final BallEventRepository ballEventRepository;
    private final MatchEngine matchEngine;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void autoSimulateMatches() {
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalDate today = nowUtc.toLocalDate();

        // ── Pass 1: Simulate SCHEDULED fixtures and put them IN_PROGRESS ──
        List<Fixture> candidates = fixtureRepository
                .findScheduledWithLeague("SCHEDULED", today);

        for (Fixture f : candidates) {
            try {
                // Skip friendly matches (no league)
                if (f.getLeague() == null) continue;

                // Parse the league's match start time
                String startTimeStr = f.getLeague().getMatchStartTime();
                if (startTimeStr == null) startTimeStr = "14:00";
                LocalTime matchTime = LocalTime.parse(startTimeStr);
                LocalDateTime matchStart = LocalDateTime.of(f.getMatchDate(), matchTime);

                // Only simulate if we've passed the start time
                if (nowUtc.isBefore(matchStart)) continue;

                // Skip if already has a result
                if (matchResultRepository.existsByFixtureId(f.getId())) continue;

                log.info("Auto-simulating fixture {} (league {} R{})",
                        f.getId(), f.getLeague().getId(), f.getRound());
                matchEngine.simulateMatch(f.getId());

                // Override to IN_PROGRESS so frontend shows live ball-by-ball
                f.setStatus("IN_PROGRESS");
                fixtureRepository.save(f);
            } catch (Exception e) {
                log.error("Failed to auto-simulate fixture {}: {}", f.getId(), e.getMessage());
            }
        }

        // ── Pass 2: Auto-complete IN_PROGRESS league fixtures whose time has elapsed ──
        List<Fixture> inProgress = fixtureRepository.findByStatus("IN_PROGRESS");
        for (Fixture f : inProgress) {
            try {
                if (f.getLeague() == null) continue; // friendlies handled by MatchSimController
                matchResultRepository.findByFixtureId(f.getId()).ifPresent(mr -> {
                    if (isMatchTimeElapsed(mr)) {
                        f.setStatus("COMPLETED");
                        fixtureRepository.save(f);
                        log.info("Auto-completed league fixture {} after live duration elapsed", f.getId());
                    }
                });
            } catch (Exception e) {
                log.error("Failed to auto-complete fixture {}: {}", f.getId(), e.getMessage());
            }
        }
    }

    /**
     * Check if enough wall-clock time has elapsed since the match was simulated
     * to have "played" all balls at the configured interval.
     */
    private boolean isMatchTimeElapsed(MatchResult result) {
        if (result.getCreatedAt() == null) return true;

        long totalBalls = 0;
        for (Innings inn : result.getInningsList()) {
            totalBalls += ballEventRepository.countByInningsId(inn.getId());
        }
        int inningsCount = result.getInningsList().size();
        int breakCount = inningsCount > 1 ? inningsCount - 1 : 0;

        long totalSeconds = totalBalls * BALL_INTERVAL_SECONDS
                + (long) breakCount * INNINGS_BREAK_SECONDS;
        long elapsed = java.time.Duration.between(result.getCreatedAt(), LocalDateTime.now()).getSeconds();

        return elapsed >= totalSeconds;
    }
}
