package com.cricketplex.service;

import com.cricketplex.entity.League;
import com.cricketplex.entity.Player;
import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.LeagueRepository;
import com.cricketplex.repository.PlayerRepository;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final LeagueRepository leagueRepository;

    public List<Map<String, Object>> searchManagers(String query) {
        if (query == null || query.isBlank()) return List.of();

        List<User> users = userRepository.findByNameContainingIgnoreCase(query.trim());
        List<Map<String, Object>> results = new ArrayList<>();

        for (User u : users) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", u.getId());
            entry.put("name", u.getName());
            entry.put("username", u.getUsername());
            entry.put("profilePicUrl", u.getProfilePicUrl());
            entry.put("teamSetupDone", u.getTeamSetupDone());
            results.add(entry);
        }
        return results;
    }

    public List<Map<String, Object>> searchPlayers(String query) {
        if (query == null || query.isBlank()) return List.of();

        List<Player> players = playerRepository.searchByName(query.trim());
        List<Map<String, Object>> results = new ArrayList<>();

        for (Player p : players) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", p.getId());
            entry.put("firstName", p.getFirstName());
            entry.put("lastName", p.getLastName());
            entry.put("country", p.getCountry());
            entry.put("nationality", p.getNationality());
            entry.put("role", p.getRole());
            entry.put("age", p.getAge());
            entry.put("ageDays", p.getAgeDays());
            entry.put("rating", p.getRating());
            entry.put("teamName", p.getTeam().getTeamName());
            entry.put("teamId", p.getTeam().getId());
            results.add(entry);
        }
        return results;
    }

    public List<Map<String, Object>> searchTeams(String query) {
        if (query == null || query.isBlank()) return List.of();

        List<Team> teams = teamRepository.findByTeamNameContainingIgnoreCase(query.trim());
        List<Map<String, Object>> results = new ArrayList<>();

        for (Team t : teams) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", t.getId());
            entry.put("teamName", t.getTeamName());
            entry.put("country", t.getCountry());
            entry.put("teamProfilePicUrl", t.getTeamProfilePicUrl());
            entry.put("odiRating", t.getOdiRating());
            entry.put("t20Rating", t.getT20Rating());
            entry.put("fcRating", t.getFcRating());
            entry.put("managerName", t.getOwner() != null ? t.getOwner().getName() : "Bot");
            entry.put("isBot", t.getIsBot());
            results.add(entry);
        }
        return results;
    }

    public List<Map<String, Object>> searchLeagues(String query) {
        if (query == null || query.isBlank()) return List.of();

        String q = query.trim();

        // Try to parse "India 2.1" format → country search + division.league filter
        List<League> leagues = leagueRepository.searchByCountry(q);
        List<Map<String, Object>> results = new ArrayList<>();

        for (League l : leagues) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", l.getId());
            entry.put("country", l.getCountry());
            entry.put("division", l.getDivision());
            entry.put("leagueNumber", l.getLeagueNumber());
            entry.put("leagueId", l.getDivision() + "." + l.getLeagueNumber());
            entry.put("format", l.getFormat());
            entry.put("season", l.getSeason());
            results.add(entry);
        }
        return results;
    }
}
