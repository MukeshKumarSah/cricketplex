package com.cricketplex.controller;

import com.cricketplex.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/managers")
    public ResponseEntity<?> searchManagers(@RequestParam String q) {
        return ResponseEntity.ok(searchService.searchManagers(q));
    }

    @GetMapping("/players")
    public ResponseEntity<?> searchPlayers(@RequestParam String q) {
        return ResponseEntity.ok(searchService.searchPlayers(q));
    }

    @GetMapping("/teams")
    public ResponseEntity<?> searchTeams(@RequestParam String q) {
        return ResponseEntity.ok(searchService.searchTeams(q));
    }

    @GetMapping("/leagues")
    public ResponseEntity<?> searchLeagues(@RequestParam String q) {
        return ResponseEntity.ok(searchService.searchLeagues(q));
    }
}
