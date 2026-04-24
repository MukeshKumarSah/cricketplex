package com.cricketplex.service;

import com.cricketplex.entity.AppState;
import com.cricketplex.repository.AppStateRepository;
import com.cricketplex.repository.PlayerRepository;
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

/**
 * Ages all players by 1 day every real-world day at 00:30 UTC.
 * A season lasts 56 days: ageDays goes 0 → 55, then rolls over to age+1, ageDays=0.
 *
 * Resilience: tracks last_aging_date in app_state table.
 * On startup and each scheduled tick, catches up any missed days
 * (e.g. server was down for 3 days → applies 3 increments).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerAgingService {

    private static final int DAYS_PER_SEASON = 56;
    private static final String KEY = "last_aging_date";

    private final PlayerRepository playerRepository;
    private final AppStateRepository appStateRepository;
    private final TransactionTemplate txTemplate;

    @PostConstruct
    public void catchUpOnStartup() {
        log.info("Checking for missed player aging days...");
        txTemplate.executeWithoutResult(status -> applyMissedDays());
    }

    @Scheduled(cron = "0 30 0 * * *", zone = "UTC")
    @Transactional
    public void scheduledAging() {
        applyMissedDays();
    }

    @Transactional
    public void applyMissedDays() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        AppState state = appStateRepository.findById(KEY).orElse(null);

        LocalDate lastAged;
        if (state == null) {
            // First run ever — seed it with yesterday so today's aging applies
            state = new AppState(KEY, today.minusDays(1).toString());
            appStateRepository.save(state);
            lastAged = today.minusDays(1);
        } else {
            lastAged = LocalDate.parse(state.getValue());
        }

        long missedDays = ChronoUnit.DAYS.between(lastAged, today);
        if (missedDays <= 0) {
            log.info("Player aging already up-to-date (last: {})", lastAged);
            return;
        }

        // Cap at a reasonable maximum to prevent runaway if date is corrupt
        if (missedDays > 60) {
            log.warn("Missed {} days exceeds safety cap of 60 — capping", missedDays);
            missedDays = 60;
        }

        log.info("Applying {} missed aging day(s) (last aged: {}, today: {})", missedDays, lastAged, today);
        for (long i = 0; i < missedDays; i++) {
            int updated = playerRepository.incrementAgeDays();
            int rolledOver = playerRepository.rollOverAge(DAYS_PER_SEASON);
            log.debug("Aging day {}/{}: {} incremented, {} rolled over", i + 1, missedDays, updated, rolledOver);
        }

        state.setValue(today.toString());
        appStateRepository.save(state);
        log.info("Player aging complete — {} day(s) applied, last_aging_date set to {}", missedDays, today);
    }
}
