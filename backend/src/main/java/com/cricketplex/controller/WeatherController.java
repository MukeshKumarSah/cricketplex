package com.cricketplex.controller;

import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    /**
     * 5-day forecast for the user's home country.
     */
    @GetMapping("/forecast")
    public ResponseEntity<?> getForecast(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Optional<Team> teamOpt = teamRepository.findByOwner(user);
        if (teamOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("available", false));
        }

        String country = teamOpt.get().getCountry();
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> forecast = weatherService.getForecast(country, today);

        return ResponseEntity.ok(Map.of(
                "country", country,
                "forecast", forecast
        ));
    }
}
