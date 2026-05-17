package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendlyTournamentService {

    private static final int LEAGUE_MIN = 4;
    private static final int LEAGUE_MAX = 8;
    private static final int KNOCKOUT_MIN = 8;

    private final FriendlyTournamentRepository tournamentRepo;
    private final FriendlyTournamentTeamRepository tournamentTeamRepo;
    private final FriendlyTournamentStandingRepository standingRepo;
    private final FixtureRepository fixtureRepo;
    private final MatchResultRepository matchResultRepo;
    private final UserRepository userRepo;
    private final TeamRepository teamRepo;

    @Value("${app.season1-start}")
    private String season1StartStr;
    private static final int SEASON_DAYS = 56;

    // ═══════════════════════════════════════════════════════════════
    //  CREATE / MANAGE TOURNAMENT
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public FriendlyTournament createTournament(UUID creatorId, Map<String, Object> body) {
        User creator = userRepo.findById(creatorId).orElseThrow();
        boolean isAdmin  = Boolean.TRUE.equals(body.get("_isAdmin"));
        boolean isSup    = Boolean.TRUE.equals(creator.getIsSupporter());
        if (!isAdmin && !isSup) throw new IllegalStateException("Only supporters or admins can create tournaments");

        String type   = required(body, "type");
        String format = required(body, "format");
        if (!List.of("LEAGUE", "KNOCKOUT").contains(type))
            throw new IllegalArgumentException("type must be LEAGUE or KNOCKOUT");
        if (!List.of("T20", "ODI").contains(format))
            throw new IllegalArgumentException("format must be T20 or ODI");

        String scheduleType = (String) body.getOrDefault("scheduleType", "WEEKLY");
        String scheduleDays = (String) body.getOrDefault("scheduleDays", "SATURDAY");
        Boolean isPublic    = body.get("isPublic") == null || Boolean.TRUE.equals(body.get("isPublic"));

        LocalDate startDate = null;
        if (body.get("startDate") != null) startDate = LocalDate.parse(body.get("startDate").toString());

        LocalDateTime regDeadline = null;
        if (body.get("registrationDeadline") != null)
            regDeadline = LocalDateTime.parse(body.get("registrationDeadline").toString());

        FriendlyTournament t = FriendlyTournament.builder()
                .name(required(body, "name"))
                .type(type)
                .format(format)
                .createdBy(creator)
                .isPublic(isPublic)
                .joinCode(generateJoinCode())
                .scheduleType(scheduleType)
                .scheduleDays(scheduleDays)
                .startDate(startDate)
                .registrationDeadline(regDeadline)
                .build();

        return tournamentRepo.save(t);
    }

    @Transactional
    public void inviteTeam(UUID tournamentId, UUID requesterId, UUID teamId) {
        inviteTeamInternal(tournamentId, requesterId, teamId, false);
    }

    @Transactional
    public void inviteTeamAsAdmin(UUID tournamentId, UUID teamId) {
        inviteTeamInternal(tournamentId, null, teamId, true);
    }

    private void inviteTeamInternal(UUID tournamentId, UUID requesterId, UUID teamId, boolean isAdmin) {
        FriendlyTournament t = getTournamentOrThrow(tournamentId);
        requireRegistration(t);
        if (!isAdmin) requireCreatorOrAdmin(t, requesterId);

        if (tournamentTeamRepo.existsByTournamentIdAndTeamId(tournamentId, teamId))
            throw new IllegalStateException("Team already invited");

        Team team = teamRepo.findById(teamId).orElseThrow(() -> new IllegalArgumentException("Team not found"));

        tournamentTeamRepo.save(FriendlyTournamentTeam.builder()
                .tournament(t)
                .team(team)
                .inviteStatus("INVITED")
                .build());
    }

    @Transactional
    public void joinByCode(UUID userId, String code) {
        FriendlyTournament t = tournamentRepo.findByJoinCode(code.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid join code"));
        requireRegistration(t);
        if (!Boolean.TRUE.equals(t.getIsPublic()))
            throw new IllegalStateException("Tournament is private — use an invitation");

        User user = userRepo.findById(userId).orElseThrow();
        UUID activeTeamId = user.getActiveTeamId();
        if (activeTeamId == null) throw new IllegalStateException("You don't have an active team");
        if (tournamentTeamRepo.existsByTournamentIdAndTeamId(t.getId(), activeTeamId))
            throw new IllegalStateException("Your team is already in this tournament");

        Team team = teamRepo.findById(activeTeamId).orElseThrow();
        tournamentTeamRepo.save(FriendlyTournamentTeam.builder()
                .tournament(t)
                .team(team)
                .inviteStatus("ACCEPTED")
                .invitedAt(LocalDateTime.now())
                .respondedAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void respondToInvite(UUID userId, UUID tournamentId, boolean accept) {
        FriendlyTournament t = getTournamentOrThrow(tournamentId);
        requireRegistration(t);

        // Find the pending invite for any team owned by this user
        FriendlyTournamentTeam invite = tournamentTeamRepo
                .findByTournamentId(tournamentId).stream()
                .filter(ftt -> "INVITED".equals(ftt.getInviteStatus())
                        && ftt.getTeam().getOwner() != null
                        && ftt.getTeam().getOwner().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No pending invite found"));

        invite.setInviteStatus(accept ? "ACCEPTED" : "DECLINED");
        invite.setRespondedAt(LocalDateTime.now());
        tournamentTeamRepo.save(invite);
    }

    @Transactional
    public void cancelTournament(UUID tournamentId, UUID requesterId, boolean isAdmin) {
        FriendlyTournament t = getTournamentOrThrow(tournamentId);
        if (!isAdmin) requireCreatorOrAdmin(t, requesterId);
        t.setStatus("CANCELLED");
        tournamentRepo.save(t);
    }

    public UUID getCreatorId(UUID tournamentId) {
        return getTournamentOrThrow(tournamentId).getCreatedBy().getId();
    }

    // ═══════════════════════════════════════════════════════════════
    //  START TOURNAMENT — GENERATE FIXTURES
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public void startTournament(UUID tournamentId, UUID requesterId, boolean isAdmin) {
        FriendlyTournament t = getTournamentOrThrow(tournamentId);
        if (!isAdmin) requireCreatorOrAdmin(t, requesterId);

        if (!"REGISTRATION".equals(t.getStatus()))
            throw new IllegalStateException("Tournament has already started or ended");

        List<Team> acceptedTeams = tournamentTeamRepo
                .findByTournamentIdAndInviteStatus(tournamentId, "ACCEPTED")
                .stream().map(FriendlyTournamentTeam::getTeam).toList();

        if ("LEAGUE".equals(t.getType())) {
            if (acceptedTeams.size() < LEAGUE_MIN || acceptedTeams.size() > LEAGUE_MAX)
                throw new IllegalStateException("League requires " + LEAGUE_MIN + "-" + LEAGUE_MAX + " accepted teams (have " + acceptedTeams.size() + ")");
            generateLeagueFixtures(t, new ArrayList<>(acceptedTeams));
        } else {
            if (acceptedTeams.size() < KNOCKOUT_MIN)
                throw new IllegalStateException("Knockout requires at least " + KNOCKOUT_MIN + " accepted teams (have " + acceptedTeams.size() + ")");
            generateKnockoutFirstRound(t, new ArrayList<>(acceptedTeams));
        }

        t.setStatus("ACTIVE");
        tournamentRepo.save(t);
        log.info("Friendly tournament {} started with {} teams", t.getName(), acceptedTeams.size());
    }

    // ─── League: double round-robin ─────────────────────────────
    private void generateLeagueFixtures(FriendlyTournament t, List<Team> teams) {
        boolean hasOdd = (teams.size() % 2 != 0);
        List<Team> ring = new ArrayList<>(teams);
        if (hasOdd) ring.add(null); // BYE slot
        int size = ring.size(); // now even
        int totalRounds = size - 1;

        List<DayOfWeek> days = parseDays(t.getScheduleDays());
        LocalDate start = t.getStartDate() != null ? t.getStartDate() : LocalDate.now().plusDays(7);
        List<LocalDate> dates = generateMatchDates(totalRounds * 2, start, days);

        List<Fixture> leg1 = new ArrayList<>();

        for (int round = 0; round < totalRounds; round++) {
            LocalDate date = dates.get(round);
            int matchNum = 0;
            for (int i = 0; i < size / 2; i++) {
                Team home = ring.get(i);
                Team away = ring.get(size - 1 - i);
                if (home == null || away == null) continue; // BYE
                matchNum++;
                Fixture f = Fixture.builder()
                        .friendlyTournament(t)
                        .matchType("FRIENDLY")
                        .format(t.getFormat())
                        .season(seasonForDate(date))
                        .round(round + 1)
                        .matchNumber(matchNum)
                        .homeTeam(home)
                        .awayTeam(away)
                        .matchDate(date)
                        .status("SCHEDULED")
                        .tournamentLeg(1)
                        .pitchType("STANDARD")
                        .build();
                leg1.add(fixtureRepo.save(f));
            }
            // Rotate ring: keep index 0 fixed, rotate the rest right
            Team last = ring.remove(size - 1);
            ring.add(1, last);
        }

        // Leg 2 — same matchups, home/away swapped
        for (Fixture f1 : leg1) {
            int leg2Round = totalRounds + f1.getRound();
            LocalDate date  = dates.get(totalRounds + f1.getRound() - 1);
            fixtureRepo.save(Fixture.builder()
                    .friendlyTournament(t)
                    .matchType("FRIENDLY")
                    .format(t.getFormat())
                    .season(seasonForDate(date))
                    .round(leg2Round)
                    .matchNumber(f1.getMatchNumber())
                    .homeTeam(f1.getAwayTeam())
                    .awayTeam(f1.getHomeTeam())
                    .matchDate(date)
                    .status("SCHEDULED")
                    .tournamentLeg(2)
                    .pitchType("STANDARD")
                    .build());
        }

        // Create standings row for every team
        for (Team team : teams) {
            standingRepo.save(FriendlyTournamentStanding.builder()
                    .tournament(t).team(team).build());
        }
    }

    // ─── Knockout: first round ───────────────────────────────────
    private void generateKnockoutFirstRound(FriendlyTournament t, List<Team> teams) {
        Collections.shuffle(teams);
        int n = teams.size();
        int mainSize      = Integer.highestOneBit(n); // largest power-of-2 ≤ n
        int prelimMatches = n - mainSize;             // 0 if n is a power of 2

        List<DayOfWeek> days  = parseDays(t.getScheduleDays());
        LocalDate start = t.getStartDate() != null ? t.getStartDate() : LocalDate.now().plusDays(7);

        if (prelimMatches == 0) {
            // All teams directly into the first full round (QF / R16 / etc.)
            String roundName = knockoutRoundName(mainSize);
            List<LocalDate> dates = generateMatchDates(mainSize / 2, start, days);
            for (int i = 0; i < mainSize / 2; i++) {
                fixtureRepo.save(buildKnockoutFixture(t, teams.get(2 * i), teams.get(2 * i + 1),
                        1, roundName, i + 1, dates.get(i)));
            }
        } else {
            // Preliminary round: 2*prelimMatches teams play for spots in main bracket
            List<LocalDate> dates = generateMatchDates(prelimMatches, start, days);
            for (int i = 0; i < prelimMatches; i++) {
                fixtureRepo.save(buildKnockoutFixture(t, teams.get(2 * i), teams.get(2 * i + 1),
                        1, "Preliminary", i + 1, dates.get(i)));
            }
            // Remaining teams are byes — they'll enter when advanceKnockoutRound is called
        }
    }

    // ─── Knockout: advance to next round ────────────────────────
    @Transactional
    public void advanceKnockoutRound(UUID tournamentId, UUID requesterId, boolean isAdmin) {
        FriendlyTournament t = getTournamentOrThrow(tournamentId);
        if (!isAdmin) requireCreatorOrAdmin(t, requesterId);
        if (!"ACTIVE".equals(t.getStatus()))
            throw new IllegalStateException("Tournament is not active");

        List<Fixture> allFixtures = fixtureRepo.findByFriendlyTournamentIdOrderByRoundAscMatchNumberAsc(tournamentId);

        // Find current round (max round number)
        int currentRound = allFixtures.stream().mapToInt(Fixture::getRound).max().orElse(0);
        if (currentRound == 0) throw new IllegalStateException("No fixtures generated yet");

        List<Fixture> currentFixtures = allFixtures.stream()
                .filter(f -> f.getRound() == currentRound).toList();
        String currentRoundName = currentFixtures.get(0).getTournamentRoundName();

        boolean allDone = currentFixtures.stream().allMatch(f -> "COMPLETED".equals(f.getStatus()));
        if (!allDone) throw new IllegalStateException("Not all matches in the current round are completed");

        // If current round is the Final, complete the tournament
        if ("Final".equals(currentRoundName)) {
            t.setStatus("COMPLETED");
            tournamentRepo.save(t);
            return;
        }

        // Collect winners from current round
        List<Team> nextRoundTeams = new ArrayList<>();
        for (Fixture f : currentFixtures.stream()
                .sorted(Comparator.comparingInt(Fixture::getTournamentSlot)).toList()) {
            matchResultRepo.findByFixtureId(f.getId()).ifPresent(mr -> {
                if (mr.getWinner() != null) nextRoundTeams.add(mr.getWinner());
            });
        }

        // If current round was Preliminary, also add the bye teams
        if ("Preliminary".equals(currentRoundName)) {
            Set<UUID> teamsInPrelim = currentFixtures.stream()
                    .flatMap(f -> Set.of(f.getHomeTeam().getId(), f.getAwayTeam().getId()).stream())
                    .collect(Collectors.toSet());
            tournamentTeamRepo.findByTournamentIdAndInviteStatus(tournamentId, "ACCEPTED")
                    .stream()
                    .map(FriendlyTournamentTeam::getTeam)
                    .filter(team -> !teamsInPrelim.contains(team.getId()))
                    .forEach(nextRoundTeams::add);
        }

        String nextRoundName = nextKnockoutRoundName(currentRoundName);
        if (nextRoundName == null) {
            t.setStatus("COMPLETED");
            tournamentRepo.save(t);
            return;
        }

        // Generate next round fixtures
        List<DayOfWeek> days = parseDays(t.getScheduleDays());
        LocalDate nextStart = currentFixtures.stream()
                .map(Fixture::getMatchDate).max(Comparator.naturalOrder())
                .orElse(LocalDate.now()).plusDays(1);
        List<LocalDate> dates = generateMatchDates(nextRoundTeams.size() / 2, nextStart, days);

        int nextRoundNum = currentRound + 1;
        for (int i = 0; i < nextRoundTeams.size() / 2; i++) {
            fixtureRepo.save(buildKnockoutFixture(t,
                    nextRoundTeams.get(2 * i), nextRoundTeams.get(2 * i + 1),
                    nextRoundNum, nextRoundName, i + 1, dates.get(i)));
        }
        log.info("Knockout round '{}' generated for tournament {}", nextRoundName, t.getName());
    }

    // ═══════════════════════════════════════════════════════════════
    //  STANDINGS UPDATE (called by MatchEngine after match completes)
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public void onMatchCompleted(Fixture fixture, MatchResult mr) {
        FriendlyTournament t = fixture.getFriendlyTournament();
        if (t == null || !"LEAGUE".equals(t.getType())) return; // knockouts don't use standings

        Team home = fixture.getHomeTeam();
        Team away = fixture.getAwayTeam();
        Team winner = mr.getWinner();

        FriendlyTournamentStanding sHome = getOrCreateStanding(t, home);
        FriendlyTournamentStanding sAway = getOrCreateStanding(t, away);

        sHome.setPlayed(sHome.getPlayed() + 1);
        sAway.setPlayed(sAway.getPlayed() + 1);

        if (winner != null && winner.getId().equals(home.getId())) {
            sHome.setWon(sHome.getWon() + 1);   sHome.setPoints(sHome.getPoints() + 2);
            sAway.setLost(sAway.getLost() + 1);
        } else if (winner != null && winner.getId().equals(away.getId())) {
            sAway.setWon(sAway.getWon() + 1);   sAway.setPoints(sAway.getPoints() + 2);
            sHome.setLost(sHome.getLost() + 1);
        } else { // tie / no result
            sHome.setDrawn(sHome.getDrawn() + 1); sHome.setPoints(sHome.getPoints() + 1);
            sAway.setDrawn(sAway.getDrawn() + 1); sAway.setPoints(sAway.getPoints() + 1);
        }

        // NRR: accumulate runs/overs from innings
        List<Innings> innings = mr.getInningsList();
        if (innings != null && innings.size() >= 2) {
            for (Innings inn : innings) {
                Team battingTeam  = inn.getBattingTeam();
                double overs      = toActualOvers(inn.getTotalOvers());
                int    runs       = inn.getTotalRuns();
                FriendlyTournamentStanding batting  = battingTeam.getId().equals(home.getId()) ? sHome : sAway;
                FriendlyTournamentStanding bowling  = battingTeam.getId().equals(home.getId()) ? sAway : sHome;
                batting.setRunsScoredTotal(batting.getRunsScoredTotal() + runs);
                batting.setOversFacedTotal(batting.getOversFacedTotal() + overs);
                bowling.setRunsConcededTotal(bowling.getRunsConcededTotal() + runs);
                bowling.setOversBowledTotal(bowling.getOversBowledTotal() + overs);
            }
            recalcNrr(sHome);
            recalcNrr(sAway);
        }

        standingRepo.save(sHome);
        standingRepo.save(sAway);

        // Check if all league fixtures are done → auto-complete
        long remaining = fixtureRepo.findByFriendlyTournamentIdOrderByRoundAscMatchNumberAsc(t.getId())
                .stream().filter(f -> !"COMPLETED".equals(f.getStatus())).count();
        if (remaining == 0) {
            t.setStatus("COMPLETED");
            tournamentRepo.save(t);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  QUERIES — DETAIL / LIST
    // ═══════════════════════════════════════════════════════════════

    public Map<String, Object> getTournamentDetail(UUID tournamentId, UUID currentUserId) {
        FriendlyTournament t = getTournamentOrThrow(tournamentId);
        Map<String, Object> out = new LinkedHashMap<>();

        out.put("id", t.getId());
        out.put("name", t.getName());
        out.put("type", t.getType());
        out.put("format", t.getFormat());
        out.put("status", t.getStatus());
        out.put("isPublic", t.getIsPublic());
        out.put("joinCode", t.getJoinCode());
        out.put("scheduleType", t.getScheduleType());
        out.put("scheduleDays", t.getScheduleDays());
        out.put("startDate", t.getStartDate());
        out.put("registrationDeadline", t.getRegistrationDeadline());
        out.put("createdAt", t.getCreatedAt());
        out.put("createdBy", Map.of(
                "id",   t.getCreatedBy().getId(),
                "name", t.getCreatedBy().getName()
        ));

        boolean isCreator = t.getCreatedBy().getId().equals(currentUserId);
        out.put("isCreator", isCreator);

        // Teams
        List<Map<String, Object>> teams = tournamentTeamRepo.findByTournamentId(tournamentId)
                .stream().map(ftt -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("teamId", ftt.getTeam().getId());
                    m.put("teamName", ftt.getTeam().getTeamName());
                    m.put("inviteStatus", ftt.getInviteStatus());
                    return m;
                }).toList();
        out.put("teams", teams);

        // Fixtures
        List<Map<String, Object>> fixtures = fixtureRepo
                .findByFriendlyTournamentIdOrderByRoundAscMatchNumberAsc(tournamentId)
                .stream().map(this::serializeFixture).toList();
        out.put("fixtures", fixtures);

        // Standings (league only)
        if ("LEAGUE".equals(t.getType())) {
            out.put("standings", standingRepo.findByTournamentIdOrdered(tournamentId)
                    .stream().map(this::serializeStanding).toList());
        }

        return out;
    }

    public List<Map<String, Object>> listTournaments(UUID userId, String filter) {
        List<FriendlyTournament> list = switch (filter == null ? "public" : filter) {
            case "mine"    -> tournamentRepo.findByCreatedByIdOrderByCreatedAtDesc(userId);
            case "joined"  -> tournamentRepo.findJoinedByUserId(userId);
            case "pending" -> tournamentRepo.findPendingInvitesByUserId(userId);
            default        -> tournamentRepo.findByIsPublicTrueAndStatusNotOrderByCreatedAtDesc("CANCELLED");
        };
        return list.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("type", t.getType());
            m.put("format", t.getFormat());
            m.put("status", t.getStatus());
            m.put("isPublic", t.getIsPublic());
            m.put("joinCode", t.getJoinCode());
            m.put("startDate", t.getStartDate());
            m.put("createdBy", t.getCreatedBy().getName());
            long accepted = tournamentTeamRepo.countByTournamentIdAndInviteStatus(t.getId(), "ACCEPTED");
            m.put("acceptedTeams", accepted);
            return m;
        }).toList();
    }

    // ═══════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private Fixture buildKnockoutFixture(FriendlyTournament t, Team home, Team away,
                                          int round, String roundName, int slot, LocalDate date) {
        return Fixture.builder()
                .friendlyTournament(t)
                .matchType("FRIENDLY")
                .format(t.getFormat())
                .season(seasonForDate(date))
                .round(round)
                .matchNumber(slot)
                .homeTeam(home)
                .awayTeam(away)
                .matchDate(date)
                .status("SCHEDULED")
                .tournamentRoundName(roundName)
                .tournamentSlot(slot)
                .pitchType("STANDARD")
                .build();
    }

    private int seasonForDate(LocalDate date) {
        LocalDate s1Start = LocalDate.parse(season1StartStr);
        if (date.isBefore(s1Start)) return 1;
        long days = ChronoUnit.DAYS.between(s1Start, date);
        return 1 + (int)(days / SEASON_DAYS);
    }

    private FriendlyTournamentStanding getOrCreateStanding(FriendlyTournament t, Team team) {
        return standingRepo.findByTournamentIdAndTeamId(t.getId(), team.getId())
                .orElseGet(() -> standingRepo.save(
                        FriendlyTournamentStanding.builder().tournament(t).team(team).build()));
    }

    private void recalcNrr(FriendlyTournamentStanding s) {
        double nrr = 0;
        if (s.getOversFacedTotal() > 0 && s.getOversBowledTotal() > 0) {
            nrr = (s.getRunsScoredTotal() / s.getOversFacedTotal())
                - (s.getRunsConcededTotal() / s.getOversBowledTotal());
        }
        s.setNrr(Math.round(nrr * 1000.0) / 1000.0);
    }

    /** Convert cricket overs notation (e.g. 19.3 = 19 overs 3 balls) to decimal overs. */
    private double toActualOvers(double cricketOvers) {
        int completed = (int) cricketOvers;
        int balls = (int) Math.round((cricketOvers - completed) * 10);
        return completed + balls / 6.0;
    }

    /**
     * Generate dates for `count` match slots by cycling through the scheduled days of week,
     * always advancing forward from `start`.
     */
    private List<LocalDate> generateMatchDates(int count, LocalDate start, List<DayOfWeek> days) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = start;
        int idx = 0;
        while (dates.size() < count) {
            DayOfWeek target = days.get(idx % days.size());
            cursor = nextDayOfWeek(cursor, target);
            dates.add(cursor);
            cursor = cursor.plusDays(1);
            idx++;
        }
        return dates;
    }

    private LocalDate nextDayOfWeek(LocalDate from, DayOfWeek target) {
        int diff = (target.getValue() - from.getDayOfWeek().getValue() + 7) % 7;
        return from.plusDays(diff);
    }

    private List<DayOfWeek> parseDays(String scheduleDays) {
        if (scheduleDays == null || scheduleDays.isBlank()) return List.of(DayOfWeek.SATURDAY);
        return Arrays.stream(scheduleDays.split(","))
                .map(String::trim)
                .map(s -> switch (s.toUpperCase()) {
                    case "MON", "MONDAY"    -> DayOfWeek.MONDAY;
                    case "TUE", "TUESDAY"   -> DayOfWeek.TUESDAY;
                    case "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
                    case "THU", "THURSDAY"  -> DayOfWeek.THURSDAY;
                    case "FRI", "FRIDAY"    -> DayOfWeek.FRIDAY;
                    case "SAT", "SATURDAY"  -> DayOfWeek.SATURDAY;
                    case "SUN", "SUNDAY"    -> DayOfWeek.SUNDAY;
                    default -> DayOfWeek.SATURDAY;
                })
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /** Largest power-of-2 ≤ n → determines bracket size and first full round name. */
    private String knockoutRoundName(int bracketSize) {
        return switch (bracketSize) {
            case 2  -> "Final";
            case 4  -> "Semi Final";
            case 8  -> "Quarter Final";
            case 16 -> "Round of 16";
            case 32 -> "Round of 32";
            default -> "Round of " + bracketSize;
        };
    }

    private String nextKnockoutRoundName(String current) {
        return switch (current) {
            case "Preliminary" -> null; // will be determined by mainSize when advancing
            case "Round of 32" -> "Round of 16";
            case "Round of 16" -> "Quarter Final";
            case "Quarter Final" -> "Semi Final";
            case "Semi Final"   -> "Final";
            case "Final"        -> null; // tournament over
            default             -> null;
        };
    }

    private String generateJoinCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rng = new Random();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    private Map<String, Object> serializeFixture(Fixture f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("round", f.getRound());
        m.put("matchNumber", f.getMatchNumber());
        m.put("homeTeam", Map.of("id", f.getHomeTeam().getId(), "name", f.getHomeTeam().getTeamName()));
        m.put("awayTeam", Map.of("id", f.getAwayTeam().getId(), "name", f.getAwayTeam().getTeamName()));
        m.put("matchDate", f.getMatchDate());
        m.put("status", f.getStatus());
        m.put("leg", f.getTournamentLeg());
        m.put("roundName", f.getTournamentRoundName());
        m.put("slot", f.getTournamentSlot());
        matchResultRepo.findByFixtureId(f.getId()).ifPresent(mr -> {
            m.put("winner", mr.getWinner() != null
                    ? Map.of("id", mr.getWinner().getId(), "name", mr.getWinner().getTeamName()) : null);
            m.put("resultType", mr.getResultType());
        });
        return m;
    }

    private Map<String, Object> serializeStanding(FriendlyTournamentStanding s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("teamId", s.getTeam().getId());
        m.put("teamName", s.getTeam().getTeamName());
        m.put("played", s.getPlayed());
        m.put("won", s.getWon());
        m.put("drawn", s.getDrawn());
        m.put("lost", s.getLost());
        m.put("points", s.getPoints());
        m.put("nrr", s.getNrr());
        return m;
    }

    // ─── Guards ──────────────────────────────────────────────────
    private FriendlyTournament getTournamentOrThrow(UUID id) {
        return tournamentRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));
    }

    private void requireRegistration(FriendlyTournament t) {
        if (!"REGISTRATION".equals(t.getStatus()))
            throw new IllegalStateException("Tournament registration is closed");
    }

    private void requireCreatorOrAdmin(FriendlyTournament t, UUID requesterId) {
        // requesterId is from the controller — admin flag passed separately via the body map _isAdmin
        // Here we just check creator; admin bypass is done in the controller before calling
        if (!t.getCreatedBy().getId().equals(requesterId))
            throw new IllegalStateException("Only the tournament creator can perform this action");
    }

    private String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) throw new IllegalArgumentException(key + " is required");
        return v.toString().trim();
    }
}
