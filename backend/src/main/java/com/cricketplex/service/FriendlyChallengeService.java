package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    // ─── Send a challenge ────────────────────────────────────────

    @Transactional
    public FriendlyChallenge sendChallenge(User user, UUID opponentTeamId, String format,
                                           String pitchType, LocalDate matchDate, String message) {
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
        if (matchDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Match date must be today or later");
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
                .message(message)
                .status("PENDING")
                .build();

        return challengeRepository.save(challenge);
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

        // Create a friendly fixture
        Fixture fixture = Fixture.builder()
                .homeTeam(challenge.getChallengerTeam())
                .awayTeam(challenge.getChallengedTeam())
                .matchDate(challenge.getMatchDate())
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
        return challengeRepository.save(challenge);
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
        return challengeRepository.save(challenge);
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

        // Override fixture to IN_PROGRESS for live time-based viewing
        Fixture fixture = challenge.getFixture();
        fixture.setStatus("IN_PROGRESS");
        fixtureRepository.save(fixture);

        challenge.setStatus("COMPLETED");
        challengeRepository.save(challenge);
        return result;
    }

    // ─── Get all challenges for my team ──────────────────────────

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
            dto.put("status", c.getStatus());
            dto.put("message", c.getMessage());
            dto.put("createdAt", c.getCreatedAt());
            dto.put("isSender", c.getChallengerTeam().getId().equals(myTeam.getId()));
            dto.put("isReceiver", c.getChallengedTeam().getId().equals(myTeam.getId()));

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
