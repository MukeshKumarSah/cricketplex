package com.cricketplex.service;

import com.cricketplex.entity.Fixture;
import com.cricketplex.entity.StadiumSeats;
import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.FixtureRepository;
import com.cricketplex.repository.StadiumSeatsRepository;
import com.cricketplex.repository.TeamRepository;
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

    private Team getTeam(User user) {
        return teamRepository.findByOwner(user)
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
        } else {
            result.put("premium", 2000);
            result.put("standard", 8000);
            result.put("economy", 12000);
            result.put("standing", 3000);
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
        fixtureRepository.save(fixture);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", fixture.getId());
        result.put("pitchType", fixture.getPitchType());
        return result;
    }
}
