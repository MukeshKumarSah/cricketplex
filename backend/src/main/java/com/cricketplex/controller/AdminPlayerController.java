package com.cricketplex.controller;

import com.cricketplex.service.AdminPlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/players")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlayerController {

    private final AdminPlayerService adminPlayerService;

    // ── Stats ──
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(adminPlayerService.getStats());
    }

    // ── Pool by country ──
    @GetMapping("/pool/{country}")
    public ResponseEntity<?> getPool(@PathVariable String country) {
        return ResponseEntity.ok(adminPlayerService.getPoolByCountry(country));
    }

    // ── First Names ──
    @PostMapping("/first-names")
    public ResponseEntity<?> addFirstNames(@RequestBody Map<String, Object> body) {
        String country = (String) body.get("country");
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) body.get("names");
        return ResponseEntity.ok(adminPlayerService.addFirstNames(country, names));
    }

    @DeleteMapping("/first-names/{id}")
    public ResponseEntity<?> deleteFirstName(@PathVariable UUID id) {
        adminPlayerService.deleteFirstName(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Last Names ──
    @PostMapping("/last-names")
    public ResponseEntity<?> addLastNames(@RequestBody Map<String, Object> body) {
        String country = (String) body.get("country");
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) body.get("names");
        return ResponseEntity.ok(adminPlayerService.addLastNames(country, names));
    }

    @DeleteMapping("/last-names/{id}")
    public ResponseEntity<?> deleteLastName(@PathVariable UUID id) {
        adminPlayerService.deleteLastName(id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
