package com.cricketplex.service;

import com.cricketplex.entity.Fixture;
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
 * Finds all SCHEDULED league fixtures whose date+time (UTC) has passed,
 * and auto-simulates them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchScheduler {

    private final FixtureRepository fixtureRepository;
    private final MatchResultRepository matchResultRepository;
    private final MatchEngine matchEngine;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void autoSimulateMatches() {
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalDate today = nowUtc.toLocalDate();

        // Get all fixtures that are on or before today and still SCHEDULED (with league eagerly loaded)
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
            } catch (Exception e) {
                log.error("Failed to auto-simulate fixture {}: {}", f.getId(), e.getMessage());
            }
        }
    }
}
