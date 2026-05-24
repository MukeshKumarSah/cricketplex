package com.cricketplex.controller;

import com.cricketplex.entity.User;
import com.cricketplex.repository.UserRepository;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.CommentaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/commentary")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommentaryController {

    private final CommentaryService commentaryService;
    private final UserRepository userRepository;

    private User requireAuthenticatedUser(UserPrincipal principal) {
        if (principal == null) {
            throw new RuntimeException("Authentication required");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    /**
     * Get pending submissions for review
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingSubmissions() {
        try {
            return ResponseEntity.ok(Map.of(
                    "submissions", commentaryService.getSubmissionsByStatus("pending")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get approved submissions
     */
    @GetMapping("/approved")
    public ResponseEntity<?> getApprovedSubmissions() {
        try {
            return ResponseEntity.ok(Map.of(
                    "submissions", commentaryService.getSubmissionsByStatus("approved")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get rejected submissions
     */
    @GetMapping("/rejected")
    public ResponseEntity<?> getRejectedSubmissions() {
        try {
            return ResponseEntity.ok(Map.of(
                    "submissions", commentaryService.getSubmissionsByStatus("rejected")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Approve commentary
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveCommentary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        try {
            User admin = requireAuthenticatedUser(principal);
            commentaryService.approveCommentary(id, admin);
            return ResponseEntity.ok(Map.of("message", "Commentary approved"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reject commentary
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectCommentary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody Map<String, String> request
    ) {
        try {
            User admin = requireAuthenticatedUser(principal);
            String reason = request.get("reason");
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Rejection reason is required"));
            }
            commentaryService.rejectCommentary(id, admin, reason);
            return ResponseEntity.ok(Map.of("message", "Commentary rejected"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Edit any commentary (admin privilege)
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> editCommentary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody Map<String, String> request
    ) {
        try {
            User admin = requireAuthenticatedUser(principal);
            Map<String, Object> result = commentaryService.editCommentary(
                    id,
                    admin,
                    request.get("commentaryText"),
                    true // is admin
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete any commentary (admin privilege)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCommentary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        try {
            User admin = requireAuthenticatedUser(principal);
            commentaryService.deleteCommentary(id, admin, true);
            return ResponseEntity.ok(Map.of("message", "Commentary deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Bulk approve
     */
    @PostMapping("/bulk-approve")
    public ResponseEntity<?> bulkApprove(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, java.util.List<String>> request
    ) {
        try {
            User admin = requireAuthenticatedUser(principal);
            java.util.List<String> ids = request.get("ids");
            int approved = 0;
            java.util.List<String> failures = new java.util.ArrayList<>();

            for (String idStr : ids) {
                try {
                    UUID id = UUID.fromString(idStr);
                    commentaryService.approveCommentary(id, admin);
                    approved++;
                } catch (Exception e) {
                    failures.add(idStr + ": " + e.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of(
                    "approved", approved,
                    "failed", failures.size(),
                    "failures", failures
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Bulk reject
     */
    @PostMapping("/bulk-reject")
    public ResponseEntity<?> bulkReject(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> request
    ) {
        try {
            User admin = requireAuthenticatedUser(principal);
            java.util.List<String> ids = (java.util.List<String>) request.get("ids");
            String reason = (String) request.get("reason");

            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Rejection reason is required"));
            }

            int rejected = 0;
            java.util.List<String> failures = new java.util.ArrayList<>();

            for (String idStr : ids) {
                try {
                    UUID id = UUID.fromString(idStr);
                    commentaryService.rejectCommentary(id, admin, reason);
                    rejected++;
                } catch (Exception e) {
                    failures.add(idStr + ": " + e.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of(
                    "rejected", rejected,
                    "failed", failures.size(),
                    "failures", failures
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStatistics() {
        // TODO: Implement coverage gaps, top contributors, usage stats
        return ResponseEntity.ok(Map.of("message", "Statistics coming soon"));
    }
}
