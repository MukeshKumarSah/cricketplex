package com.cricketplex.controller;

import com.cricketplex.entity.SimSession;
import com.cricketplex.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/sim")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SimSessionController {

    private final SimulationService simulationService;

    /** POST /api/admin/sim/sessions — create and asynchronously run a simulation */
    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(@RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "Unnamed Sim");
        String format = (String) body.getOrDefault("format", "T20");
        int numMatches = 10;
        Object nm = body.get("numMatches");
        if (nm instanceof Number) numMatches = ((Number) nm).intValue();

        String configJson;
        try {
            Object configObj = body.get("config");
            if (configObj == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "config field is required"));
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            configJson = mapper.writeValueAsString(configObj);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid config JSON"));
        }

        SimSession session = simulationService.createSession(name, format, numMatches, configJson);
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionDto(session));
    }

    /** GET /api/admin/sim/sessions — list all sessions (newest first) */
    @GetMapping("/sessions")
    public ResponseEntity<?> listSessions() {
        List<Map<String, Object>> dtos = simulationService.listSessions().stream()
                .map(this::sessionDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /** GET /api/admin/sim/sessions/{id} — get one session with results */
    @GetMapping("/sessions/{id}")
    public ResponseEntity<?> getSession(@PathVariable UUID id) {
        return simulationService.getSession(id)
                .map(s -> ResponseEntity.ok(sessionDto(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** DELETE /api/admin/sim/sessions/{id} — delete session and all generated data */
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<?> deleteSession(@PathVariable UUID id) {
        if (simulationService.getSession(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        simulationService.deleteSession(id);
        return ResponseEntity.ok(Map.of("message", "Session deleted"));
    }

    // ─────────────────────────────────────────────────────────────────
    // DTO helper
    // ─────────────────────────────────────────────────────────────────

    private Map<String, Object> sessionDto(SimSession s) {
        java.util.LinkedHashMap<String, Object> dto = new java.util.LinkedHashMap<>();
        dto.put("id", s.getId());
        dto.put("name", s.getName());
        dto.put("format", s.getFormat());
        dto.put("numMatches", s.getNumMatches());
        dto.put("status", s.getStatus());
        dto.put("createdAt", s.getCreatedAt());
        dto.put("errorMessage", s.getErrorMessage());
        // Parse result JSON back to object so it's not double-encoded
        if (s.getResultJson() != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                dto.put("result", mapper.readValue(s.getResultJson(), Object.class));
            } catch (Exception e) {
                dto.put("result", null);
            }
        } else {
            dto.put("result", null);
        }
        return dto;
    }
}
