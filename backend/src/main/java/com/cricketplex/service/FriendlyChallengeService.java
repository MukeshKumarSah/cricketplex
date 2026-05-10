package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FriendlyChallengeService {

    private final FriendlyChallengeRepository challengeRepository;
    private final TeamRepository teamRepository;
    private final FixtureRepository fixtureRepository;
    private final MatchLineupRepository matchLineupRepository;
    private final MatchResultRepository matchResultRepository;
    private final UserRepository userRepository;
    private final MatchEngine matchEngine;
    private final NotificationService notificationService;

    private static final Set<String> ALLOWED_TIMES = Set.of("02:00", "07:00", "07:30", "12:00", "17:00", "21:00");

    // ─── Send a challenge ────────────────────────────────────────

    @Transactional
    public FriendlyChallenge sendChallenge(User user, UUID opponentTeamId, String format,
                                           String pitchType, LocalDate matchDate, String matchTime, String message) {
        Team myTeam = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("You don't have a team"));

        Team opponentTeam = teamRepository.findById(opponentTeamId)
                .orElseThrow(() -> new IllegalArgumentException("Opponent team not found"));

        if (myTeam.getId().equals(opponentTeamId)) {
            throw new IllegalArgumentException("You cannot challenge your own team");
        }
        if (opponentTeam.getIsBot()) {
            throw new IllegalArgumentException("You cannot challenge a bot team to a friendly");
        }
        if (!"T20".equalsIgnoreCase(format) && !"ODI".equalsIgnoreCase(format) && !"FC".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException("Format must be T20, ODI, or FC");
        }
        if (matchTime == null || !ALLOWED_TIMES.contains(matchTime)) {
            throw new IllegalArgumentException("Match time must be one of: 02:00, 07:00, 12:00, 17:00, 21:00");
        }

        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalDate today = nowUtc.toLocalDate();

        if (matchDate.isBefore(today)) {
            throw new IllegalArgumentException("Match date cannot be in the past");
        }
        if (matchDate.isAfter(today.plusDays(10))) {
            throw new IllegalArgumentException("Match date cannot be more than 10 days from now");
        }

        // Can't challenge for a time that's already passed today
        LocalDateTime challengeDateTime = LocalDateTime.of(matchDate, LocalTime.parse(matchTime));
        if (!challengeDateTime.isAfter(nowUtc)) {
            throw new IllegalArgumentException("Cannot challenge for a time that has already passed");
        }

        // Limit pending challenges per team
        long pendingSent = challengeRepository.countByChallengerTeamIdAndStatus(myTeam.getId(), "PENDING");
        if (pendingSent >= 5) {
            throw new IllegalArgumentException("You can only have 5 pending outgoing challenges at a time");
        }

        FriendlyChallenge challenge = FriendlyChallenge.builder()
                .challengerTeam(myTeam)
                .challengedTeam(opponentTeam)
                .format(format.toUpperCase())
                .pitchType(pitchType != null ? pitchType : "STANDARD")
                .matchDate(matchDate)
                .matchTime(matchTime)
                .message(message)
                .status("PENDING")
                .build();

        challenge = challengeRepository.save(challenge);

        // Notify the challenged team's owner
        if (opponentTeam.getOwner() != null) {
            notificationService.create(
                    opponentTeam.getOwner().getId(),
                    "CHALLENGE_RECEIVED",
                    "Friendly Challenge Received",
                    myTeam.getTeamName() + " has challenged you to a " + format.toUpperCase() + " friendly!",
                    "/challenges"
            );
        }

        return challenge;
    }

    // ─── Accept a challenge ──────────────────────────────────────

    @Transactional
    public FriendlyChallenge acceptChallenge(User user, UUID challengeId) {
        Team myTeam = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("You don't have a team"));

        FriendlyChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        if (!challenge.getChallengedTeam().getId().equals(myTeam.getId())) {
            throw new IllegalArgumentException("This challenge is not for your team");
        }
        if (!"PENDING".equals(challenge.getStatus())) {
            throw new IllegalArgumentException("Challenge is no longer pending");
        }

        // Can't accept if the scheduled time has passed
        if (challenge.getMatchTime() != null) {
            LocalDateTime matchDateTime = LocalDateTime.of(challenge.getMatchDate(), LocalTime.parse(challenge.getMatchTime()));
            if (!matchDateTime.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                throw new IllegalArgumentException("This challenge has expired — the match time has passed");
            }
        }

        // Create a friendly fixture
        Fixture fixture = Fixture.builder()
                .homeTeam(challenge.getChallengerTeam())
                .awayTeam(challenge.getChallengedTeam())
                .matchDate(challenge.getMatchDate())
                .matchTime(challenge.getMatchTime())
                .pitchType(challenge.getPitchType())
                .matchType("FRIENDLY")
                .format(challenge.getFormat())
                .status("SCHEDULED")
                .round(0)
                .matchNumber(0)
                .build();
        fixture = fixtureRepository.save(fixture);

        challenge.setFixture(fixture);
        challenge.setStatus("ACCEPTED");
        challenge = challengeRepository.save(challenge);

        // Notify the challenger (sender) that their challenge was accepted
        Team challengerTeam = challenge.getChallengerTeam();
        if (challengerTeam.getOwner() != null) {
            notificationService.create(
                    challengerTeam.getOwner().getId(),
                    "CHALLENGE_ACCEPTED",
                    "Challenge Accepted!",
                    myTeam.getTeamName() + " accepted your " + challenge.getFormat() + " challenge. Set your lineup!",
                    "/match/" + fixture.getId() + "/lineup"
            );
        }

        return challenge;
    }

    // ─── Decline a challenge ─────────────────────────────────────

    @Transactional
    public FriendlyChallenge declineChallenge(User user, UUID challengeId) {
        Team myTeam = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("You don't have a team"));

        FriendlyChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        if (!challenge.getChallengedTeam().getId().equals(myTeam.getId())) {
            throw new IllegalArgumentException("This challenge is not for your team");
        }
        if (!"PENDING".equals(challenge.getStatus())) {
            throw new IllegalArgumentException("Challenge is no longer pending");
        }

        challenge.setStatus("DECLINED");
        challenge = challengeRepository.save(challenge);

        // Notify the challenger (sender) that their challenge was declined
        Team challengerTeam = challenge.getChallengerTeam();
        if (challengerTeam.getOwner() != null) {
            notificationService.create(
                    challengerTeam.getOwner().getId(),
                    "CHALLENGE_DECLINED",
                    "Challenge Declined",
                    myTeam.getTeamName() + " declined your " + challenge.getFormat() + " challenge.",
                    "/challenges"
            );
        }

        return challenge;
    }

    // ─── Cancel a challenge (by sender) ──────────────────────────

    @Transactional
    public FriendlyChallenge cancelChallenge(User user, UUID challengeId) {
        Team myTeam = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("You don't have a team"));

        FriendlyChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        if (!challenge.getChallengerTeam().getId().equals(myTeam.getId())) {
            throw new IllegalArgumentException("Only the sender can cancel a challenge");
        }
        if (!"PENDING".equals(challenge.getStatus())) {
            throw new IllegalArgumentException("Challenge is no longer pending");
        }

        challenge.setStatus("CANCELLED");
        return challengeRepository.save(challenge);
    }

    // ─── Simulate friendly match ─────────────────────────────────

    @Transactional
    public MatchResult simulateFriendly(User user, UUID challengeId) {
        Team myTeam = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("You don't have a team"));

        FriendlyChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));

        if (!challenge.getChallengerTeam().getId().equals(myTeam.getId())
                && !challenge.getChallengedTeam().getId().equals(myTeam.getId())) {
            throw new IllegalArgumentException("You are not part of this challenge");
        }
        if (!"ACCEPTED".equals(challenge.getStatus())) {
            throw new IllegalArgumentException("Challenge must be accepted before simulation");
        }
        if (challenge.getFixture() == null) {
            throw new IllegalArgumentException("No fixture created for this challenge");
        }

        // Check both lineups are set
        UUID fixtureId = challenge.getFixture().getId();
        List<MatchLineup> lineups = matchLineupRepository.findByFixtureId(fixtureId);
        if (lineups.size() < 2) {
            throw new IllegalArgumentException("Both teams must set their lineups before simulation");
        }

        // Simulate
        MatchResult result = matchEngine.simulateMatch(fixtureId);

        if ("FC_DAY1_COMPLETE".equals(result.getFixture().getStatus())) {
            // Advance challenge date so Day 2 can be triggered tomorrow without showing as "expired"
            challenge.setMatchDate(result.getFixture().getMatchDate());
            challengeRepository.save(challenge);
            
            // Override fixture to IN_PROGRESS so frontend shows live ball-by-ball viewing
            result.getFixture().setStatus("IN_PROGRESS");
            fixtureRepository.save(result.getFixture());
        } else if ("COMPLETED".equals(result.getFixture().getStatus())) {
            challenge.setStatus("COMPLETED");
            challengeRepository.save(challenge);
            
            // Override fixture to IN_PROGRESS so frontend shows live ball-by-ball viewing
            result.getFixture().setStatus("IN_PROGRESS");
            fixtureRepository.save(result.getFixture());
        }
        return result;
    }

    // ─── Get all challenges for my team ──────────────────────────

    public long getPendingIncomingCount(User user) {
        return teamRepository.findByOwner(user)
                .map(team -> challengeRepository.countByChallengedTeamIdAndStatus(team.getId(), "PENDING"))
                .orElse(0L);
    }

    public List<Map<String, Object>> getChallenges(User user) {
        Team myTeam = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("You don't have a team"));

        List<FriendlyChallenge> challenges = challengeRepository.findAllByTeam(myTeam.getId());
        List<Map<String, Object>> result = new ArrayList<>();

        for (FriendlyChallenge c : challenges) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", c.getId());
            dto.put("challengerTeamId", c.getChallengerTeam().getId());
            dto.put("challengerTeamName", c.getChallengerTeam().getTeamName());
            dto.put("challengerTeamPic", c.getChallengerTeam().getTeamProfilePicUrl());
            dto.put("challengedTeamId", c.getChallengedTeam().getId());
            dto.put("challengedTeamName", c.getChallengedTeam().getTeamName());
            dto.put("challengedTeamPic", c.getChallengedTeam().getTeamProfilePicUrl());
            dto.put("format", c.getFormat());
            dto.put("pitchType", c.getPitchType());
            dto.put("matchDate", c.getMatchDate());
            dto.put("matchTime", c.getMatchTime());
            dto.put("status", c.getStatus());
            dto.put("message", c.getMessage());
            dto.put("createdAt", c.getCreatedAt());
            dto.put("isSender", c.getChallengerTeam().getId().equals(myTeam.getId()));
            dto.put("isReceiver", c.getChallengedTeam().getId().equals(myTeam.getId()));

            // Compute whether this challenge's scheduled time has passed
            boolean expired = false;
            if (c.getMatchTime() != null) {
                LocalDateTime matchDateTime = LocalDateTime.of(c.getMatchDate(), LocalTime.parse(c.getMatchTime()));
                expired = !matchDateTime.isAfter(LocalDateTime.now(ZoneOffset.UTC));
            }
            dto.put("expired", expired);

            if (c.getFixture() != null) {
                dto.put("fixtureId", c.getFixture().getId());
                // Check if lineups are set
                List<MatchLineup> lineups = matchLineupRepository.findByFixtureId(c.getFixture().getId());
                boolean myLineupSet = lineups.stream()
                        .anyMatch(l -> l.getTeam().getId().equals(myTeam.getId()));
                boolean opponentLineupSet = lineups.stream()
                        .anyMatch(l -> !l.getTeam().getId().equals(myTeam.getId()));
                dto.put("myLineupSet", myLineupSet);
                dto.put("opponentLineupSet", opponentLineupSet);
                dto.put("bothLineupsSet", myLineupSet && opponentLineupSet);

                // Check if result exists
                boolean resultExists = matchResultRepository.existsByFixtureId(c.getFixture().getId());
                dto.put("resultExists", resultExists);
                dto.put("fixtureStatus", c.getFixture().getStatus());
            } else {
                dto.put("fixtureId", null);
                dto.put("myLineupSet", false);
                dto.put("opponentLineupSet", false);
                dto.put("bothLineupsSet", false);
                dto.put("resultExists", false);
            }

            result.add(dto);
        }

        return result;
    }

    // ─── Get opponent teams to challenge (non-bot teams) ─────────

    public List<Map<String, Object>> getChallengeable(User user) {
        Team myTeam = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("You don't have a team"));

        List<Team> allTeams = teamRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Team t : allTeams) {
            if (t.getId().equals(myTeam.getId())) continue;
            if (t.getIsBot()) continue;

            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", t.getId());
            dto.put("teamName", t.getTeamName());
            dto.put("country", t.getCountry());
            dto.put("teamProfilePicUrl", t.getTeamProfilePicUrl());
            dto.put("odiRating", t.getOdiRating());
            dto.put("t20Rating", t.getT20Rating());
            dto.put("ownerName", t.getOwner() != null ? t.getOwner().getName() : "Unknown");
            result.add(dto);
        }

        return result;
    }
}
