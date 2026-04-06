package com.cricketplex.controller;

import com.cricketplex.entity.Player;
import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.PlayerRepository;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/squad")
@RequiredArgsConstructor
public class SquadController {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;

    @GetMapping
    public ResponseEntity<?> getSquad(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Team team = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("No team found"));

        List<Player> players = playerRepository.findByTeam(team);

        List<Map<String, Object>> squad = players.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("firstName", p.getFirstName());
            m.put("lastName", p.getLastName());
            m.put("country", p.getCountry());
            m.put("role", p.getRole());
            m.put("age", p.getAge());
            m.put("ageDays", p.getAgeDays());
            m.put("batHand", p.getBatHand());
            m.put("bowlHand", p.getBowlHand());
            m.put("bowlType", p.getBowlType());
            m.put("batRating", p.getBatRating());
            m.put("bowlRating", p.getBowlRating());
            m.put("keeperRating", p.getKeeperRating());
            m.put("fldRating", p.getFldRating());
            m.put("experience", p.getExperience());
            m.put("stamina", p.getStamina());
            m.put("fitness", p.getFitness());
            m.put("batAggression", p.getBatAggression());
            m.put("bowlAggression", p.getBowlAggression());
            m.put("nationality", p.getNationality());
            m.put("wage", p.getWage());
            m.put("rating", p.getRating());
            m.put("confidence", p.getConfidence());
            return m;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "teamName", team.getTeamName(),
                "country", team.getCountry(),
                "players", squad
        ));
    }
}
