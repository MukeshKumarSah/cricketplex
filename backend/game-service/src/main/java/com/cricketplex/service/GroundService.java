package com.cricketplex.service;

import com.cricketplex.entity.*;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.MatchResultRepository;
import com.cricketplex.repository.StadiumSeatsRepository;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.util.TeamHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GroundService {

    private final TeamRepository teamRepository;
    private final StadiumSeatsRepository stadiumSeatsRepository;
    private final FixtureRepository fixtureRepository;
    private final MatchResultRepository matchResultRepository;
    private final UserRepository userRepository;
    private final TeamHelper teamHelper;

    private Team getTeam(User user) {
        return teamHelper.getActiveTeam(user)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));
    }

    // ── Stadium Seats ──

    @Transactional(readOnly = true)
    public Map<String, Object> getSeats(User user) {
        Team team = getTeam(user);
        StadiumSeats seats = stadiumSeatsRepository.findByTeam(team)
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        if (seats != null) {
            result.put("premium", seats.getPremium());
            result.put("standard", seats.getStandard());
            result.put("economy", seats.getEconomy());
            result.put("standing", seats.getStanding());
            result.put("defaultPitch", seats.getDefaultPitch());
        } else {
            result.put("premium", 500);
            result.put("standard", 1000);
            result.put("economy", 1500);
            result.put("standing", 2000);
            result.put("defaultPitch", "STANDARD");
        }
        int total = ((Number) result.get("premium")).intValue()
                + ((Number) result.get("standard")).intValue()
                + ((Number) result.get("economy")).intValue()
                + ((Number) result.get("standing")).intValue();
        result.put("total", total);
        return result;
    }

    @Transactional
    public Map<String, Object> updateSeats(User user, Map<String, Integer> request) {
        Team team = getTeam(user);
        StadiumSeats seats = stadiumSeatsRepository.findByTeam(team)
                .orElseGet(() -> StadiumSeats.builder().team(team).build());

        if (request.containsKey("premium")) seats.setPremium(Math.max(0, request.get("premium")));
        if (request.containsKey("standard")) seats.setStandard(Math.max(0, request.get("standard")));
        if (request.containsKey("economy")) seats.setEconomy(Math.max(0, request.get("economy")));
        if (request.containsKey("standing")) seats.setStanding(Math.max(0, request.get("standing")));

        // Enforce ordering: premium ≤ standard ≤ economy ≤ standing
        if (seats.getPremium() > seats.getStandard()
                || seats.getStandard() > seats.getEconomy()
                || seats.getEconomy() > seats.getStanding()) {
            throw new IllegalArgumentException(
                    "Seat capacity must follow: Premium ≤ Standard ≤ Economy ≤ Standing");
        }

        // Soft cap: total capacity ≤ 50,000
        int total = seats.getPremium() + seats.getStandard() + seats.getEconomy() + seats.getStanding();
        if (total > 50000) {
            throw new IllegalArgumentException(
                    "Total stadium capacity cannot exceed 50,000 seats (currently " + total + ")");
        }

        stadiumSeatsRepository.save(seats);
        return getSeats(user);
    }

    // ── Home Fixtures & Pitch Setup ──

    private static final Map<String, String> FORMAT_COMP = Map.of(
            "T20", "T20 League", "ODI", "One Day League", "FC", "First Class Shield");

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUpcomingHomeMatches(User user) {
        Team team = getTeam(user);
        List<Fixture> fixtures = fixtureRepository
                .findByHomeTeamAndMatchDateGreaterThanEqualOrderByMatchDateAsc(team, LocalDate.now());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Fixture f : fixtures) {
            if (f.getLeague() == null) continue; // skip friendly matches
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", f.getId());
            map.put("opponentName", f.getAwayTeam().getTeamName());
            map.put("opponentPicUrl", f.getAwayTeam().getTeamProfilePicUrl());
            map.put("opponentIsBot", f.getAwayTeam().getIsBot());
            map.put("matchDate", f.getMatchDate().toString());
            map.put("competition", FORMAT_COMP.getOrDefault(f.getLeague().getFormat(), f.getLeague().getFormat() + " League"));
            map.put("format", f.getLeague().getFormat());
            map.put("round", f.getRound());
            map.put("leagueLabel", f.getLeague().getDivision() + "." + f.getLeague().getLeagueNumber());
            map.put("pitchType", f.getPitchType());
            map.put("matchStartTimeUtc", f.getLeague().getMatchStartTime());
            result.add(map);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> updateMatchPitch(User user, UUID matchId, String pitchType) {
        Team team = getTeam(user);
        Fixture fixture = fixtureRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        if (!fixture.getHomeTeam().getId().equals(team.getId())) {
            throw new IllegalArgumentException("You can only set pitch for your home matches");
        }

        List<String> validPitches = List.of("STANDARD", "DUSTY", "GREEN", "FLAT", "UNEVEN", "DRY", "SLOW", "BOUNCY");
        String normalized = pitchType.toUpperCase();
        if (!validPitches.contains(normalized)) {
            throw new IllegalArgumentException("Invalid pitch type: " + pitchType);
        }

        fixture.setPitchType(normalized);
        fixture.setPitchLocked(true);
        fixtureRepository.save(fixture);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", fixture.getId());
        result.put("pitchType", fixture.getPitchType());
        return result;
    }

    @Transactional
    public Map<String, Object> updateDefaultPitch(User user, String pitchType) {
        Team team = getTeam(user);
        List<String> validPitches = List.of("STANDARD", "DUSTY", "GREEN", "FLAT", "UNEVEN", "DRY", "SLOW", "BOUNCY");
        String normalized = pitchType.toUpperCase();
        if (!validPitches.contains(normalized)) {
            throw new IllegalArgumentException("Invalid pitch type: " + pitchType);
        }
        StadiumSeats seats = stadiumSeatsRepository.findByTeam(team)
                .orElseGet(() -> StadiumSeats.builder().team(team).build());
        String oldDefault = seats.getDefaultPitch() != null ? seats.getDefaultPitch() : "STANDARD";
        seats.setDefaultPitch(normalized);
        stadiumSeatsRepository.save(seats);

        // Immediately propagate to future home fixtures that still inherit default pitch.
        // Locked fixtures were manually set and must not be overridden.
        List<Fixture> upcomingHome = fixtureRepository.findByHomeTeamAndStatusAndMatchDateGreaterThanEqual(
                team, "SCHEDULED", LocalDate.now());
        int updatedFixtures = 0;
        for (Fixture fixture : upcomingHome) {
            if (Boolean.TRUE.equals(fixture.getPitchLocked())) continue;
            if (!oldDefault.equalsIgnoreCase(fixture.getPitchType())) continue;
            fixture.setPitchType(normalized);
            updatedFixtures++;
        }
        if (updatedFixtures > 0) {
            fixtureRepository.saveAll(upcomingHome);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("defaultPitch", normalized);
        result.put("updatedUpcomingHomeFixtures", updatedFixtures);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentHomeAttendance(User user) {
        Team team = getTeam(user);
        List<MatchResult> results = matchResultRepository.findRecentHomeAttendance(team.getId());

        List<Map<String, Object>> list = new ArrayList<>();
        for (MatchResult mr : results) {
            if (list.size() >= 5) break;
            Fixture f = mr.getFixture();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("opponent", f.getAwayTeam().getTeamName());
            m.put("format", f.getFormat());
            m.put("matchDate", f.getMatchDate().toString());
            m.put("attendance", mr.getAttendance());
            list.add(m);
        }
        return list;
    }
}
