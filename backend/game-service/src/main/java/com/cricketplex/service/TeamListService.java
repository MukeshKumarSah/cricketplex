package com.cricketplex.service;

import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TeamListService {

    private final TeamRepository teamRepository;

    public List<Map<String, Object>> getAllTeams() {
        List<Team> teams = teamRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Team team : teams) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", team.getId());
            entry.put("teamName", team.getTeamName());
            entry.put("country", team.getCountry());
            entry.put("teamProfilePicUrl", team.getTeamProfilePicUrl());
            entry.put("odiRating", team.getOdiRating());
            entry.put("t20Rating", team.getT20Rating());
            entry.put("fcRating", team.getFcRating());

            User owner = team.getOwner();
            entry.put("managerName", owner != null ? owner.getName() : "Bot");
            entry.put("managerProfilePicUrl", owner != null ? owner.getProfilePicUrl() : null);
            entry.put("activityStatus", owner != null ? computeActivityStatus(owner.getLastActiveAt()) : "GREEN");
            entry.put("isBot", team.getIsBot());

            result.add(entry);
        }

        return result;
    }

    /**
     * GREEN  = active now or within last few minutes
     * YELLOW = active in last 24 hours
     * GREY   = active in last 7 days
     * ORANGE = active in last 30 days
     * RED    = inactive > 30 days or never
     */
    private String computeActivityStatus(LocalDateTime lastActiveAt) {
        if (lastActiveAt == null) {
            return "RED";
        }
        long minutesAgo = ChronoUnit.MINUTES.between(lastActiveAt, LocalDateTime.now());

        if (minutesAgo <= 5) return "GREEN";
        if (minutesAgo <= 24 * 60) return "YELLOW";
        if (minutesAgo <= 7 * 24 * 60) return "GREY";
        if (minutesAgo <= 30 * 24 * 60) return "ORANGE";
        return "RED";
    }
}
