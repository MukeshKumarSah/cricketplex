package com.cricketplex.service;

import com.cricketplex.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ages all players by 1 day every day at 00:30 UTC.
 * A season lasts 56 days: age goes from X years 0 days → X years 55 days,
 * then rolls over to (X+1) years 0 days.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerAgingService {

    private static final int DAYS_PER_SEASON = 56;

    private final PlayerRepository playerRepository;

    @Scheduled(cron = "0 30 0 * * *", zone = "UTC")
    @Transactional
    public void ageAllPlayers() {
        // Increment ageDays for all players
        int updated = playerRepository.incrementAgeDays();
        // Roll over players who hit 56 days → age + 1, ageDays = 0
        int rolledOver = playerRepository.rollOverAge(DAYS_PER_SEASON);
        log.info("Daily aging complete: {} players aged, {} rolled over to next year", updated, rolledOver);
    }
}
