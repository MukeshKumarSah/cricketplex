package com.cricketplex.controller;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import com.cricketplex.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final ActivityLogRepository activityLogRepository;

    @GetMapping
    public ResponseEntity<?> getRecentActivities(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Team team = teamRepository.findByOwner(user)
                .orElseThrow(() -> new IllegalArgumentException("No team found"));

        List<ActivityLog> logs = activityLogRepository.findTop50ByTeamIdOrderByCreatedAtDesc(team.getId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (ActivityLog log : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", log.getId());
            m.put("type", log.getType());
            m.put("text", log.getText());
            m.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }
}
