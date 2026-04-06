package com.cricketplex.service;

import com.cricketplex.entity.Fixture;
import com.cricketplex.entity.Innings;
import com.cricketplex.entity.League;
import com.cricketplex.entity.MatchResult;
import com.cricketplex.repository.BallEventRepository;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.LeagueRepository;
import com.cricketplex.repository.LeagueTeamRepository;
import com.cricketplex.repository.MatchResultRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Runs every 60 seconds.
 * Uses TransactionTemplate so each fixture gets its own committed transaction.
 * If one fixture fails, others still succeed.
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
    private final TransactionTemplate txTemplate;
    private final LeagueRepository leagueRepository;
    private final LeagueTeamRepository leagueTeamRepository;
    private final FixtureService fixtureService;

    /**
     * On startup, generate fixtures for any league that has 8 teams but no fixtures.
     * This covers leagues created via DB migration (V9/V10) + bot assignment that
     * predates the eager fixture generation in createLeague().
     */
    @PostConstruct
    public void generateMissingFixturesOnStartup() {
        try {
            List<League> allLeagues = leagueRepository.findAll();
            int generated = 0;
            for (League league : allLeagues) {
                long teamCount = leagueTeamRepository.countByLeagueId(league.getId());
                if (teamCount >= 8 && fixtureRepository.countByLeagueId(league.getId()) == 0) {
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

            if (candidates == null || candidates.isEmpty()) return;

            log.info("MatchScheduler: {} SCHEDULED candidates for {} or earlier", candidates.size(), today);

            for (Fixture f : candidates) {
                try {
                    if (f.getLeague() == null) continue;

                    String startTimeStr = f.getLeague().getMatchStartTime();
                    if (startTimeStr == null) startTimeStr = "14:00";
                    LocalTime matchTime = LocalTime.parse(startTimeStr);
                    LocalDateTime matchStart = LocalDateTime.of(f.getMatchDate(), matchTime);

                    if (nowUtc.isBefore(matchStart)) {
                        log.info("Skipping fixture {} — not yet start time (now={}, start={})",
                                f.getId(), nowUtc, matchStart);
                        continue;
                    }

                    // Check if already simulated (result exists)
                    Boolean hasResult = txTemplate.execute(status ->
                        matchResultRepository.existsByFixtureId(f.getId())
                    );

                    if (Boolean.TRUE.equals(hasResult)) {
                        // Fix stuck status if result exists but fixture still SCHEDULED
                        txTemplate.executeWithoutResult(status -> {
                            Fixture fresh = fixtureRepository.findById(f.getId()).orElse(null);
                            if (fresh != null && "SCHEDULED".equals(fresh.getStatus())) {
                                fresh.setStatus("IN_PROGRESS");
                                fixtureRepository.save(fresh);
                                log.info("Fixed stuck fixture {} SCHEDULED→IN_PROGRESS", f.getId());
                            }
                        });
                        continue;
                    }

                    log.info("Auto-simulating fixture {} (league {} R{}, date={}, start={})",
                            f.getId(), f.getLeague().getId(), f.getRound(),
                            f.getMatchDate(), startTimeStr);

                    // simulateMatch() is @Transactional — runs in its own transaction
                    matchEngine.simulateMatch(f.getId());

                    // Override COMPLETED → IN_PROGRESS for live ball-by-ball viewing
                    txTemplate.executeWithoutResult(status -> {
                        Fixture fresh = fixtureRepository.findById(f.getId()).orElse(null);
                        if (fresh != null) {
                            fresh.setStatus("IN_PROGRESS");
                            fixtureRepository.save(fresh);
                            log.info("Set fixture {} to IN_PROGRESS for live viewing", f.getId());
                        }
                    });
                } catch (Exception e) {
                    log.error("Failed to auto-simulate fixture {}: {}", f.getId(), e.getMessage());
                }
            }

            // ── Pass 2: Auto-complete IN_PROGRESS league fixtures ──
            List<Fixture> inProgress = txTemplate.execute(status ->
                fixtureRepository.findByStatus("IN_PROGRESS")
            );

            if (inProgress != null) {
                for (Fixture f : inProgress) {
                    try {
                        if (f.getLeague() == null) continue;

                        txTemplate.executeWithoutResult(status -> {
                            matchResultRepository.findByFixtureIdWithInnings(f.getId()).ifPresent(mr -> {
                                if (isMatchTimeElapsed(mr)) {
                                    Fixture fresh = fixtureRepository.findById(f.getId()).orElse(null);
                                    if (fresh != null) {
                                        fresh.setStatus("COMPLETED");
                                        fixtureRepository.save(fresh);
                                        log.info("Auto-completed fixture {} after live duration elapsed", f.getId());
                                    }
                                }
                            });
                        });
                    } catch (Exception e) {
                        log.error("Failed to auto-complete fixture {}: {}", f.getId(), e.getMessage());
                    }
                }
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

        long totalSeconds = totalBalls * BALL_INTERVAL_SECONDS
                + (long) breakCount * INNINGS_BREAK_SECONDS;
        long elapsed = java.time.Duration.between(result.getCreatedAt(), LocalDateTime.now()).getSeconds();

        return elapsed >= totalSeconds;
    }
}
