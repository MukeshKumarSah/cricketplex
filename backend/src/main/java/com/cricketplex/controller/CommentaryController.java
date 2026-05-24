package com.cricketplex.controller;

import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.TeamRepository;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.CommentaryImportService;
import com.cricketplex.service.CommentaryService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/commentary")
@RequiredArgsConstructor
public class CommentaryController {

    private final CommentaryService commentaryService;
    private final CommentaryImportService importService;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    private User requireAuthenticatedUser(UserPrincipal principal) {
        if (principal == null) {
            throw new RuntimeException("Authentication required");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    /**
     * Submit single commentary
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitCommentary(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> request
    ) {
        try {
            User user = requireAuthenticatedUser(principal);
            UUID teamId = UUID.fromString((String) request.get("teamId"));
            Team team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new RuntimeException("Team not found"));

            // Parse extra tags
            Set<String> extraTags = null;
            if (request.get("extraTags") != null) {
                extraTags = Set.copyOf((java.util.List<String>) request.get("extraTags"));
            }

            Map<String, Object> result = commentaryService.submitCommentary(
                    user,
                    team,
                    (String) request.get("commentaryText"),
                    (String) request.get("matchFormat"),
                    (String) request.get("phase"),
                    (String) request.get("bowlerType"),
                    (String) request.get("eventType"),
                    (String) request.get("wicketSituation"),
                    (String) request.get("batsmanState"),
                    (String) request.get("matchPressure"),
                    extraTags
            );

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get user's submissions
     */
    @GetMapping("/my-submissions")
    public ResponseEntity<?> getMySubmissions(@AuthenticationPrincipal UserPrincipal principal) {
        try {
            User user = requireAuthenticatedUser(principal);
            Map<String, Object> result = commentaryService.getUserSubmissions(user.getId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Edit own pending commentary
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> editCommentary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody Map<String, String> request
    ) {
        try {
            User user = requireAuthenticatedUser(principal);
            Map<String, Object> result = commentaryService.editCommentary(
                    id,
                    user,
                    request.get("commentaryText"),
                    false // not admin
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete own pending commentary
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCommentary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        try {
            User user = requireAuthenticatedUser(principal);
            commentaryService.deleteCommentary(id, user, false);
            return ResponseEntity.ok(Map.of("message", "Commentary deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Upload and validate Excel file
     */
    @PostMapping("/import/validate")
    public ResponseEntity<?> validateImport(@RequestParam("file") MultipartFile file) {
        try {
            if (!file.getOriginalFilename().endsWith(".xlsx")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only .xlsx files are supported"));
            }

            Map<String, Object> result = importService.parseAndValidate(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Confirm and import validated rows
     */
    @PostMapping("/import/confirm")
    public ResponseEntity<?> confirmImport(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> request
    ) {
        try {
            User user = requireAuthenticatedUser(principal);
            UUID teamId = UUID.fromString((String) request.get("teamId"));
            Team team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new RuntimeException("Team not found"));

            java.util.List<Map<String, Object>> rows = 
                    (java.util.List<Map<String, Object>>) request.get("rows");

            Map<String, Object> result = importService.importRows(rows, user, team);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Download Excel template
     */
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        try {
            Workbook workbook = importService.generateTemplate();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "commentary_template.xlsx");

            return new ResponseEntity<>(outputStream.toByteArray(), headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get filter options for form
     */
    @GetMapping("/filter-options")
    public ResponseEntity<?> getFilterOptions() {
        return ResponseEntity.ok(Map.ofEntries(
                Map.entry("matchFormats", java.util.List.of("T20", "ODI", "FC", "all")),
                Map.entry("phases", java.util.List.of("powerplay", "middle", "death", "all")),
                Map.entry("bowlerTypes", java.util.List.of("F", "FM", "MF", "M", "FS", "WS", "LAP", "PACE", "SPINNER", "ALL")),
                Map.entry("eventTypes", java.util.List.of(
                        "0", "1", "2", "3", "4", "5", "6",
                        "1LB", "2LB", "3LB", "4LB",
                    "1BYE", "2BYE", "3BYE", "4BYE",
                        "1WD", "2WD", "3WD", "4WD", "5WD", "6WD", "7WD",
                        "1NB", "2NB", "3NB", "4NB", "5NB", "6NB", "7NB",
                    "BOWLED", "CAUGHT", "CAUGHT_BEHIND", "LBW", "RUN_OUT_0", "RUN_OUT_1", "RUN_OUT", "STUMPED", "HIT_WICKET", "CAUGHT_AND_BOWLED"
                )),
                Map.entry("wicketSituations", java.util.List.of(
                        "run_out_striker", "run_out_non_striker"
                )),
                Map.entry("batsmanStates", java.util.List.of("new_batsman", "settling", "set", "milestone_approaching")),
                Map.entry("matchPressures", java.util.List.of("low", "medium", "high")),
                Map.entry("extraTags", java.util.List.of(
                        "catch_dropped", "great_fielding", "misfield",
                    "free_hit",
                        "strike_farming_strong_early", "strike_farming_strong_late",
                        "strike_farming_weak_early", "strike_farming_weak_late",
                        "strike_farming",
                        "milestone_3w_haul", "milestone_5w_haul",
                        "milestone_50", "milestone_100", "milestone_150", "milestone_200",
                        "partnership_50", "partnership_100", "partnership_150", "partnership_200", "partnership_250"
                ))
        ));
    }
}
