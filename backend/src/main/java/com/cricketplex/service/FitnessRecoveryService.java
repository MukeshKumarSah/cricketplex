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
 * Recovers fitness for all players daily at 00:15 UTC.
 *
 * Formula:  recovery = 1 + (stamina * 5 / 100)
 *   - stamina 10 → +1  (integer math: 1 + 50/100 = 1)
 *   - stamina 20 → +2  (1 + 100/100 = 2)
 *   - stamina 25 → +2  (1 + 125/100 = 2)
 *   - stamina 50 → +3  (1 + 250/100 = 3)
 *
 * T20 match loss is ~3-7, so daily recovery (1-3 for real players)
 * never exceeds T20 loss. Capped at 100.
 *
 * Uses app_state table for resilience — catches up missed days on startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FitnessRecoveryService {

    private static final String KEY = "last_fitness_recovery_date";

    private final PlayerRepository playerRepository;
    private final AppStateRepository appStateRepository;
    private final TransactionTemplate txTemplate;

    @PostConstruct
    public void catchUpOnStartup() {
        log.info("Checking for missed fitness recovery days...");
        txTemplate.executeWithoutResult(status -> applyMissedDays());
    }

    @Scheduled(cron = "0 15 0 * * *", zone = "UTC")
    public void scheduledRecovery() {
        applyMissedDays();
    }

    @Transactional
    public void applyMissedDays() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        AppState state = appStateRepository.findById(KEY).orElse(null);

        LocalDate lastRecovered;
        if (state == null) {
            state = new AppState(KEY, today.minusDays(1).toString());
            appStateRepository.save(state);
            lastRecovered = today.minusDays(1);
        } else {
            lastRecovered = LocalDate.parse(state.getValue());
        }

        long missedDays = ChronoUnit.DAYS.between(lastRecovered, today);
        if (missedDays <= 0) {
            log.info("Fitness recovery already up-to-date (last: {})", lastRecovered);
            return;
        }

        if (missedDays > 60) {
            log.warn("Missed {} fitness recovery days exceeds safety cap of 60 — capping", missedDays);
            missedDays = 60;
        }

        log.info("Applying {} missed fitness recovery day(s) (last: {}, today: {})", missedDays, lastRecovered, today);
        for (long i = 0; i < missedDays; i++) {
            int recovered = playerRepository.recoverFitness();
            log.debug("Fitness recovery day {}/{}: {} players recovered", i + 1, missedDays, recovered);
        }

        state.setValue(today.toString());
        appStateRepository.save(state);
        log.info("Fitness recovery complete — {} day(s) applied, last_recovery_date set to {}", missedDays, today);
    }
}
