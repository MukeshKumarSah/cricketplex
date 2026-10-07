package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the global knockout Cup that runs once per season.
 *
 * Season parity rule  : odd season → ODI Cup, even season → T20 Cup.
 * Match time          : 07:00 UTC for all cup fixtures.
 * Tie-breaker         : 1) fewer wickets lost, 2) more boundaries hit, 3) home team.
 * Bracket seeding     : standard single-elimination – Seed 1 vs Seed N in R1,
 *                       Seeds 1 & 2 can only meet in the final.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CupService {

    // ── Season timing ────────────────────────────────────────────────────────
    @Value("${app.season1-start}")
    private String season1StartStr;
    private static final int SEASON_DAYS = 56;
    static final String CUP_MATCH_TIME = "07:00";

    // ── Round schedule: season-day numbers for each round, by bracket size ──
    private static final int[] DAYS_256  = {3, 10, 17, 24, 31, 38, 45, 52};
    private static final int[] DAYS_512  = {3, 10, 17, 24, 31, 38, 45, 52, 54};
    private static final int[] DAYS_1024 = {3, 10, 17, 24, 31, 38, 45, 52, 53, 54};
    private static final int[] DAYS_2048 = {3, 10, 17, 24, 31, 38, 45, 52, 53, 54, 55};
    private static final int[] DAYS_4096 = {2, 3, 10, 17, 24, 31, 38, 45, 52, 53, 54, 55};

    // ── Prize money (coins) per round exit (0-indexed by round number 1..12) ─
    // Index 0 is unused; index R = prize when eliminated in round R.
    private static final long[] ROUND_EXIT_PRIZES = {
        0L,          // placeholder
        0L,          // R1 exit  – no prize (just participation)
        500L,        // R2 exit
        1_000L,      // R3 exit
        2_500L,      // R4 exit
        5_000L,      // R5 exit
        10_000L,     // R6 exit
        20_000L,     // R7 exit
        40_000L,     // R8 exit
        75_000L,     // R9 exit
        125_000L,    // R10 exit
        200_000L,    // R11 exit (runner-up in 2048-bracket)
        350_000L,    // R12 exit (runner-up in 4096-bracket)
    };
    private static final long WINNER_PRIZE = 500_000L;

    // ── Dependencies ─────────────────────────────────────────────────────────
    private final CupRepository       cupRepository;
    private final CupTeamRepository   cupTeamRepository;
    private final TeamRepository      teamRepository;
    private final FixtureRepository   fixtureRepository;
    private final MatchResultRepository matchResultRepository;
    private final InningsRepository   inningsRepository;

    // ═════════════════════════════════════════════════════════════════════════
    //  Public helpers
    // ═════════════════════════════════════════════════════════════════════════

    /** Compute the global season number from today's UTC date. */
    public int computeCurrentSeason() {
        LocalDate today    = LocalDate.now(ZoneOffset.UTC);
        LocalDate s1Start  = LocalDate.parse(season1StartStr);
        if (today.isBefore(s1Start)) return 1;
        long days = ChronoUnit.DAYS.between(s1Start, today);
        return 1 + (int)(days / SEASON_DAYS);
    }

    /** UTC start date of a given season. */
    public LocalDate getSeasonStart(int season) {
        return LocalDate.parse(season1StartStr).plusDays((long)(season - 1) * SEASON_DAYS);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Cup initialisation  (called once at season start from MatchScheduler)
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public Cup initializeCupForSeason(int season) {
        // Idempotent – if already created, return existing
        Optional<Cup> existing = cupRepository.findBySeason(season);
        if (existing.isPresent()) return existing.get();

        // Count human teams
        List<Team> humanTeams = teamRepository.findByIsBotFalse();
        int humanCount = humanTeams.size();
        int bracketSize = computeBracketSize(humanCount);
        int totalRounds = intLog2(bracketSize);

        // Format: odd season → ODI, even → T20
        String format = (season % 2 == 1) ? "ODI" : "T20";

        LocalDate seasonStart = getSeasonStart(season);
        int[] roundDays = getRoundDays(bracketSize);
        LocalDate startDate = seasonStart.plusDays(roundDays[0] - 1);

        Cup cup = cupRepository.save(Cup.builder()
                .season(season)
                .format(format)
                .status("ONGOING")
                .bracketSize(bracketSize)
                .totalRounds(totalRounds)
                .currentRound(1)
                .startDate(startDate)
                .build());

        // Build seed-ordered participant list
        List<Team> participants = buildSeedOrderedList(humanTeams, format, bracketSize);

        // Compute bracket positions using standard seeding algorithm
        List<Integer> bracketSeeding = generateBracketSeeding(bracketSize);
        // bracketSeeding.get(bracketPos-1) = seedRank of team at that bracket position
        // We need the inverse: seedRank → bracketPosition
        int[] seedToBracketPos = new int[bracketSize + 1]; // index = seedRank (1-based)
        for (int pos = 0; pos < bracketSize; pos++) {
            seedToBracketPos[bracketSeeding.get(pos)] = pos + 1;
        }

        // Save CupTeam entries
        for (int i = 0; i < bracketSize; i++) {
            int seedRank = i + 1;
            Team team = participants.get(i);
            cupTeamRepository.save(CupTeam.builder()
                    .cup(cup)
                    .team(team)
                    .seedRank(seedRank)
                    .bracketPosition(seedToBracketPos[seedRank])
                    .eliminatedRound(null)
                    .prizeWon(0L)
                    .build());
        }

        // Schedule Round 1 fixtures
        List<CupTeam> orderedByPos = cupTeamRepository
                .findByCupIdAndEliminatedRoundIsNullOrderByBracketPositionAsc(cup.getId());
        scheduleRoundFixtures(cup, 1, orderedByPos, seasonStart);

        log.info("Cup Season {}: {} format, {} teams, {} rounds, starts {}",
                season, format, bracketSize, totalRounds, startDate);
        return cup;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Round advancement  (called from MatchScheduler after every cup fixture)
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public void checkAndAdvanceCupRound(UUID cupId) {
        Cup cup = cupRepository.findById(cupId).orElse(null);
        if (cup == null || "COMPLETED".equals(cup.getStatus())) return;

        int round = cup.getCurrentRound();
        long total     = fixtureRepository.countByCupIdAndRound(cupId, round);
        long completed = fixtureRepository.countByCupIdAndRoundAndStatus(cupId, round, "COMPLETED");

        if (total == 0 || completed < total) return; // round not finished yet

        log.info("Cup {} season {}: round {} complete – advancing", cupId, cup.getSeason(), round);

        // Find winners and eliminate losers
        List<Fixture> roundFixtures = fixtureRepository
                .findByCupIdAndRoundOrderByMatchNumberAsc(cupId, round);

        for (Fixture f : roundFixtures) {
            MatchResult mr = matchResultRepository.findByFixtureId(f.getId()).orElse(null);
            if (mr == null) continue;

            Team winner = resolveWinner(mr);
            Team loser  = winner.getId().equals(f.getHomeTeam().getId())
                          ? f.getAwayTeam() : f.getHomeTeam();

            eliminateTeam(cup, loser, round);
        }

        if (round == cup.getTotalRounds()) {
            // Final complete – award champion prize
            CupTeam champion = cupTeamRepository
                    .findByCupIdAndEliminatedRoundIsNullOrderByBracketPositionAsc(cupId)
                    .stream().findFirst().orElse(null);
            if (champion != null) {
                champion.setPrizeWon(WINNER_PRIZE);
                cupTeamRepository.save(champion);
                Team t = champion.getTeam();
                t.setFunds(t.getFunds() + WINNER_PRIZE);
                teamRepository.save(t);
                log.info("Cup {} champion: {} – prize {}", cupId, t.getTeamName(), WINNER_PRIZE);
            }
            cup.setStatus("COMPLETED");
            cupRepository.save(cup);
            return;
        }

        // Schedule next round
        int nextRound = round + 1;
        List<CupTeam> survivors = cupTeamRepository
                .findByCupIdAndEliminatedRoundIsNullOrderByBracketPositionAsc(cupId);
        scheduleRoundFixtures(cup, nextRound, survivors, getSeasonStart(cup.getSeason()));

        cup.setCurrentRound(nextRound);
        cupRepository.save(cup);
        log.info("Cup {} season {}: scheduled round {}", cupId, cup.getSeason(), nextRound);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Bot replacement when a human team joins mid-season
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional
    public void replaceBotWithHuman(Team humanTeam) {
        int season = computeCurrentSeason();
        Cup cup = cupRepository.findBySeason(season).orElse(null);
        if (cup == null || "COMPLETED".equals(cup.getStatus())) return;

        // If the human team is already in this cup, nothing to do
        if (cupTeamRepository.existsByCupIdAndTeamId(cup.getId(), humanTeam.getId())) return;

        // Find the worst-seeded surviving bot slot
        List<CupTeam> botSlots = cupTeamRepository.findSurvivingBotsByCupId(cup.getId());
        if (botSlots.isEmpty()) return;

        CupTeam slot = botSlots.get(0); // lowest-seeded surviving bot
        Team oldBot  = slot.getTeam();

        slot.setTeam(humanTeam);
        cupTeamRepository.save(slot);

        // Swap bot → human in any SCHEDULED cup fixtures
        List<Fixture> scheduled = fixtureRepository
                .findByCupIdOrderByRoundAscMatchNumberAsc(cup.getId())
                .stream()
                .filter(f -> "SCHEDULED".equals(f.getStatus()))
                .collect(Collectors.toList());

        for (Fixture f : scheduled) {
            boolean changed = false;
            if (f.getHomeTeam().getId().equals(oldBot.getId())) {
                f.setHomeTeam(humanTeam); changed = true;
            }
            if (f.getAwayTeam().getId().equals(oldBot.getId())) {
                f.setAwayTeam(humanTeam); changed = true;
            }
            if (changed) fixtureRepository.save(f);
        }

        log.info("Cup {} season {}: replaced bot {} with human team {}",
                cup.getId(), season, oldBot.getTeamName(), humanTeam.getTeamName());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Read API helpers (for controller)
    // ═════════════════════════════════════════════════════════════════════════

    /** Full bracket data for the frontend. */
    public Map<String, Object> getCupBracket(int season) {
        Cup cup = cupRepository.findBySeason(season).orElse(null);
        if (cup == null) return Map.of("exists", false);

        List<CupTeam> allSlots = cupTeamRepository.findByCupIdOrderBySeedRankAsc(cup.getId());
        List<Map<String, Object>> teams = allSlots.stream().map(ct -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("teamId",          ct.getTeam().getId());
            m.put("teamName",        ct.getTeam().getTeamName());
            m.put("country",         ct.getTeam().getCountry());
            m.put("isBot",           ct.getTeam().getIsBot());
            m.put("seedRank",        ct.getSeedRank());
            m.put("bracketPosition", ct.getBracketPosition());
            m.put("eliminatedRound", ct.getEliminatedRound());
            m.put("prizeWon",        ct.getPrizeWon());
            return m;
        }).collect(Collectors.toList());

        List<Fixture> allFixtures = fixtureRepository
                .findByCupIdOrderByRoundAscMatchNumberAsc(cup.getId());

        Map<Integer, List<Map<String, Object>>> roundMap = new LinkedHashMap<>();
        for (Fixture f : allFixtures) {
            MatchResult mr = matchResultRepository.findByFixtureId(f.getId()).orElse(null);
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("fixtureId",   f.getId());
            fm.put("matchNumber", f.getMatchNumber());
            fm.put("matchDate",   f.getMatchDate());
            fm.put("status",      f.getStatus());
            fm.put("homeTeam", Map.of(
                    "id",   f.getHomeTeam().getId(),
                    "name", f.getHomeTeam().getTeamName()));
            fm.put("awayTeam", Map.of(
                    "id",   f.getAwayTeam().getId(),
                    "name", f.getAwayTeam().getTeamName()));
            if (mr != null && mr.getWinner() != null) {
                fm.put("winner", Map.of(
                        "id",   mr.getWinner().getId(),
                        "name", mr.getWinner().getTeamName()));
            } else {
                fm.put("winner", null);
            }
            roundMap.computeIfAbsent(f.getRound(), k -> new ArrayList<>()).add(fm);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exists",       true);
        out.put("season",       cup.getSeason());
        out.put("format",       cup.getFormat());
        out.put("status",       cup.getStatus());
        out.put("bracketSize",  cup.getBracketSize());
        out.put("totalRounds",  cup.getTotalRounds());
        out.put("currentRound", cup.getCurrentRound());
        out.put("startDate",    cup.getStartDate());
        out.put("teams",        teams);
        out.put("rounds",       roundMap);
        return out;
    }

    /** Returns the CupTeam entry for a specific team in a specific season's cup. */
    public Optional<Map<String, Object>> getMyStatus(int season, UUID teamId) {
        Cup cup = cupRepository.findBySeason(season).orElse(null);
        if (cup == null) return Optional.empty();
        return cupTeamRepository.findByCupIdAndTeamId(cup.getId(), teamId)
                .map(ct -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("seedRank",        ct.getSeedRank());
                    m.put("bracketPosition", ct.getBracketPosition());
                    m.put("eliminatedRound", ct.getEliminatedRound());
                    m.put("prizeWon",        ct.getPrizeWon());
                    m.put("stillAlive",      ct.getEliminatedRound() == null);
                    m.put("cupStatus",       cup.getStatus());
                    m.put("currentRound",    cup.getCurrentRound());
                    m.put("totalRounds",     cup.getTotalRounds());
                    return m;
                });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Private helpers
    // ═════════════════════════════════════════════════════════════════════════

    /** Determine bracket size based on the number of human teams. */
    private int computeBracketSize(int humanCount) {
        if (humanCount <= 256)  return 256;
        if (humanCount <= 512)  return 512;
        if (humanCount <= 1024) return 1024;
        if (humanCount <= 2048) return 2048;
        return 4096;
    }

    private int[] getRoundDays(int bracketSize) {
        switch (bracketSize) {
            case 256:  return DAYS_256;
            case 512:  return DAYS_512;
            case 1024: return DAYS_1024;
            case 4096: return DAYS_4096;
            default:   return DAYS_2048;
        }
    }

    private static int intLog2(int n) {
        int r = 0;
        while (n > 1) { n >>= 1; r++; }
        return r;
    }

    /**
     * Standard single-elimination bracket seeding.
     * Returns a list of size {@code size} where {@code list.get(i)} is the
     * seed rank of the team placed at bracket position {@code i+1}.
     * Guarantees: Seed 1 at pos 1, Seed 2 can only meet Seed 1 in the final.
     */
    private List<Integer> generateBracketSeeding(int size) {
        if (size == 1) {
            List<Integer> r = new ArrayList<>();
            r.add(1);
            return r;
        }
        List<Integer> smaller = generateBracketSeeding(size / 2);
        List<Integer> result  = new ArrayList<>(size);
        for (int s : smaller) {
            result.add(s);             // odd position → seed s
            result.add(size + 1 - s); // even position → complement (R1 opponent)
        }
        return result;
    }

    /**
     * Build an ordered list of {@code bracketSize} participants.
     * Human teams come first (sorted by format rating desc),
     * then bot teams fill remaining slots (also by format rating desc).
     */
    private List<Team> buildSeedOrderedList(List<Team> humanTeams, String format, int bracketSize) {
        Comparator<Team> byRating = Comparator.comparingInt(
                (Team t) -> "T20".equals(format) ? t.getT20Rating() : t.getOdiRating()
        ).reversed();

        List<Team> humans = new ArrayList<>(humanTeams);
        humans.sort(byRating);

        List<Team> result = new ArrayList<>(humans);

        int botsNeeded = bracketSize - humans.size();
        if (botsNeeded > 0) {
            Set<UUID> humanIds = humans.stream().map(Team::getId).collect(Collectors.toSet());
            List<Team> bots = teamRepository.findByIsBotTrue().stream()
                    .filter(b -> !humanIds.contains(b.getId()))
                    .sorted(byRating)
                    .limit(botsNeeded)
                    .collect(Collectors.toList());
            result.addAll(bots);
        }

        // Pad with nulls guard (shouldn't happen in practice)
        if (result.size() < bracketSize) {
            log.warn("Cup: only {} teams available, bracket needs {}", result.size(), bracketSize);
        }

        return result;
    }

    /** Generate and save Fixture records for one cup round. */
    private void scheduleRoundFixtures(Cup cup, int round,
                                       List<CupTeam> orderedTeams, LocalDate seasonStart) {
        int[] roundDays = getRoundDays(cup.getBracketSize());
        int dayOfSeason  = roundDays[round - 1];
        LocalDate matchDate = seasonStart.plusDays(dayOfSeason - 1);

        java.util.Random rng = new java.util.Random();
        List<Fixture> fixtures = new ArrayList<>();
        for (int i = 0; i + 1 < orderedTeams.size(); i += 2) {
            Team teamA = orderedTeams.get(i).getTeam();
            Team teamB = orderedTeams.get(i + 1).getTeam();
            // Randomly decide which team is home
            Team home = rng.nextBoolean() ? teamA : teamB;
            Team away = home == teamA ? teamB : teamA;
            fixtures.add(Fixture.builder()
                    .cup(cup)
                    .round(round)
                    .matchNumber((i / 2) + 1)
                    .matchType("CUP")
                    .format(cup.getFormat())
                    .season(cup.getSeason())
                    .homeTeam(home)
                    .awayTeam(away)
                    .matchDate(matchDate)
                    .matchTime(CUP_MATCH_TIME)
                    .status("SCHEDULED")
                    .pitchType("STANDARD")
                    .pitchLocked(false)
                    .build());
        }
        fixtureRepository.saveAll(fixtures);
    }

    /** Determine the winner of a completed cup match.
     *  Tie-breakers: 1) fewer wickets lost, 2) more boundaries hit, 3) home team. */
    private Team resolveWinner(MatchResult mr) {
        if (mr.getWinner() != null) return mr.getWinner();

        Map<UUID, Integer> wicketsLost  = new HashMap<>();
        Map<UUID, Integer> boundariesHit = new HashMap<>();

        for (Innings inn : mr.getInningsList()) {
            if (inn.getBattingTeam() == null) continue;
            UUID tid = inn.getBattingTeam().getId();
            wicketsLost.merge(tid, inn.getTotalWickets(), Integer::sum);
            int boundaries = inn.getBattingCards().stream()
                    .mapToInt(bc -> (bc.getFours() == null ? 0 : bc.getFours())
                                 + (bc.getSixes() == null ? 0 : bc.getSixes()))
                    .sum();
            boundariesHit.merge(tid, boundaries, Integer::sum);
        }

        Team home = mr.getFixture().getHomeTeam();
        Team away = mr.getFixture().getAwayTeam();
        int homeW = wicketsLost.getOrDefault(home.getId(), 0);
        int awayW = wicketsLost.getOrDefault(away.getId(), 0);

        // 1st tie-breaker: fewer wickets lost
        if (homeW != awayW) return homeW < awayW ? home : away;

        // 2nd tie-breaker: more boundaries hit
        int homeB = boundariesHit.getOrDefault(home.getId(), 0);
        int awayB = boundariesHit.getOrDefault(away.getId(), 0);
        if (homeB != awayB) return homeB > awayB ? home : away;

        // Last resort: home team
        return home;
    }

    /** Mark a team as eliminated, award prize money, and update team funds. */
    private void eliminateTeam(Cup cup, Team loser, int round) {
        CupTeam ct = cupTeamRepository
                .findByCupIdAndTeamId(cup.getId(), loser.getId()).orElse(null);
        if (ct == null || ct.getEliminatedRound() != null) return;

        long prize = round < ROUND_EXIT_PRIZES.length ? ROUND_EXIT_PRIZES[round] : 0L;
        ct.setEliminatedRound(round);
        ct.setPrizeWon(prize);
        cupTeamRepository.save(ct);

        if (prize > 0) {
            loser.setFunds(loser.getFunds() + prize);
            teamRepository.save(loser);
        }
    }
}
