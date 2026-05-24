package com.cricketplex.service;

import com.cricketplex.entity.CommentarySubmission;
import com.cricketplex.entity.Team;
import com.cricketplex.entity.User;
import com.cricketplex.repository.CommentarySubmissionRepository;
import com.cricketplex.util.PlaceholderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentaryService {

    private final CommentarySubmissionRepository commentaryRepository;

    /**
     * Submit new commentary
     */
    @Transactional
    public Map<String, Object> submitCommentary(
            User user,
            Team team,
            String commentaryText,
            String matchFormat,
            String phase,
            String bowlerType,
            String eventType,
            String wicketSituation,
            String batsmanState,
            String matchPressure,
            Set<String> extraTags
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate length
        if (commentaryText == null || commentaryText.trim().length() < 10) {
            errors.add("Commentary must be at least 10 characters long");
        }
        if (commentaryText != null && commentaryText.length() > 500) {
            errors.add("Commentary must not exceed 500 characters");
        }

        // Validate placeholders
        List<String> invalidPlaceholders = PlaceholderValidator.validatePlaceholders(commentaryText);
        if (!invalidPlaceholders.isEmpty()) {
            errors.add("Invalid placeholders: " + String.join(", ", invalidPlaceholders));
        }

        // Validate placeholder context
        List<String> contextErrors = PlaceholderValidator.validateContext(commentaryText, eventType, extraTags);
        errors.addAll(contextErrors);

        // Check exact duplicate
        if (commentaryRepository.existsByCommentaryTextAndEventType(commentaryText.trim(), eventType)) {
            errors.add("This exact commentary already exists for this event type");
        }

        // Check similarity (80% threshold within same event type)
        List<CommentarySubmission> similar = commentaryRepository.findSimilarCommentary(commentaryText, eventType);
        if (!similar.isEmpty()) {
            warnings.add("Similar commentary found by " + similar.get(0).getUser().getUsername());
            result.put("similarCommentary", similar.stream().limit(3).map(c -> Map.of(
                    "text", c.getCommentaryText(),
                    "author", c.getUser().getUsername(),
                    "status", c.getStatus()
            )).collect(Collectors.toList()));
        }

        if (!errors.isEmpty()) {
            result.put("success", false);
            result.put("errors", errors);
            result.put("warnings", warnings);
            return result;
        }

        // Extract and store placeholders
        List<String> placeholders = PlaceholderValidator.extractPlaceholders(commentaryText);
        String placeholdersJson = PlaceholderValidator.toJsonArray(placeholders);

        // Convert extra tags to JSON
        String extraTagsJson = extraTags == null || extraTags.isEmpty()
                ? null
                : "[\"" + String.join("\",\"", extraTags) + "\"]";

        // Create submission
        CommentarySubmission submission = CommentarySubmission.builder()
                .user(user)
                .team(team)
                .commentaryText(commentaryText.trim())
                .status("pending")
                .matchFormat(matchFormat)
                .phase(phase)
                .bowlerType(bowlerType)
                .eventType(eventType)
                .wicketSituation(wicketSituation)
                .batsmanState(batsmanState)
                .matchPressure(matchPressure)
                .extraTags(extraTagsJson)
                .placeholdersUsed(placeholdersJson)
                .build();

        commentaryRepository.save(submission);

        result.put("success", true);
        result.put("warnings", warnings);
        result.put("submissionId", submission.getId());
        result.put("message", "Commentary submitted for review");

        return result;
    }

    /**
     * Get user's submissions with counts
     */
    public Map<String, Object> getUserSubmissions(UUID userId) {
        List<CommentarySubmission> all = commentaryRepository.findByUserIdOrderByCreatedAtDesc(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("pending", commentaryRepository.countByUserIdAndStatus(userId, "pending"));
        result.put("approved", commentaryRepository.countByUserIdAndStatus(userId, "approved"));
        result.put("rejected", commentaryRepository.countByUserIdAndStatus(userId, "rejected"));
        result.put("submissions", all.stream().map(this::toMap).collect(Collectors.toList()));

        return result;
    }

    /**
     * Get submissions by status (for admin)
     */
    public List<Map<String, Object>> getSubmissionsByStatus(String status) {
        return commentaryRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream()
                .map(this::toMapWithUser)
                .collect(Collectors.toList());
    }

    /**
     * Admin approve commentary
     */
    @Transactional
    public void approveCommentary(UUID submissionId, User admin) {
        CommentarySubmission submission = commentaryRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Commentary not found"));

        submission.setStatus("approved");
        submission.setReviewedBy(admin);
        submission.setReviewedAt(LocalDateTime.now());
        submission.setAdminNotes(null); // Clear rejection notes

        commentaryRepository.save(submission);
    }

    /**
     * Admin reject commentary
     */
    @Transactional
    public void rejectCommentary(UUID submissionId, User admin, String reason) {
        CommentarySubmission submission = commentaryRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Commentary not found"));

        submission.setStatus("rejected");
        submission.setReviewedBy(admin);
        submission.setReviewedAt(LocalDateTime.now());
        submission.setAdminNotes(reason);

        commentaryRepository.save(submission);
    }

    /**
     * Edit commentary (user can edit pending, admin can edit any)
     */
    @Transactional
    public Map<String, Object> editCommentary(
            UUID submissionId,
            User editor,
            String newText,
            boolean isAdmin
    ) {
        CommentarySubmission submission = commentaryRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Commentary not found"));

        // Permission check
        if (!isAdmin && !submission.getUser().getId().equals(editor.getId())) {
            throw new RuntimeException("You can only edit your own commentary");
        }

        if (!isAdmin && !"pending".equals(submission.getStatus())) {
            throw new RuntimeException("You can only edit pending commentary");
        }

        // Validate new text
        List<String> errors = PlaceholderValidator.validatePlaceholders(newText);
        if (!errors.isEmpty()) {
            return Map.of("success", false, "errors", errors);
        }

        // Update
        submission.setCommentaryText(newText.trim());
        submission.setLastEditedBy(editor);
        submission.setLastEditedAt(LocalDateTime.now());

        // Re-extract placeholders
        List<String> placeholders = PlaceholderValidator.extractPlaceholders(newText);
        submission.setPlaceholdersUsed(PlaceholderValidator.toJsonArray(placeholders));

        commentaryRepository.save(submission);

        return Map.of("success", true, "message", "Commentary updated");
    }

    /**
     * Delete commentary (own pending only, or admin any)
     */
    @Transactional
    public void deleteCommentary(UUID submissionId, User user, boolean isAdmin) {
        CommentarySubmission submission = commentaryRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Commentary not found"));

        if (!isAdmin) {
            if (!submission.getUser().getId().equals(user.getId())) {
                throw new RuntimeException("You can only delete your own commentary");
            }
            if (!"pending".equals(submission.getStatus())) {
                throw new RuntimeException("You can only delete pending commentary");
            }
        }

        commentaryRepository.delete(submission);
    }

    /**
     * Get matching commentary for match engine
     */
    public List<CommentarySubmission> getMatchingCommentary(
            String matchFormat,
            String phase,
            String bowlerType,
            String eventType,
            String wicketSituation,
            String batsmanState,
            String matchPressure
    ) {
        List<String> phaseCandidates = new ArrayList<>();
        if (phase != null && !phase.isBlank()) {
            phaseCandidates.add(phase);
        }
        if (!phaseCandidates.contains("all")) {
            phaseCandidates.add("all");
        }

        List<String> bowlerCandidates = new ArrayList<>();
        if (bowlerType != null && !bowlerType.isBlank()) {
            bowlerCandidates.add(bowlerType);
        }

        if (Set.of("FS", "WS").contains(bowlerType) && !bowlerCandidates.contains("SPINNER")) {
            bowlerCandidates.add("SPINNER");
        }
        if (Set.of("F", "FM", "MF", "M", "LAP").contains(bowlerType) && !bowlerCandidates.contains("PACE")) {
            bowlerCandidates.add("PACE");
        }
        if (!bowlerCandidates.contains("ALL")) {
            bowlerCandidates.add("ALL");
        }

        LinkedHashMap<UUID, CommentarySubmission> merged = new LinkedHashMap<>();
        for (String candidatePhase : phaseCandidates) {
            for (String candidateBowlerType : bowlerCandidates) {
                List<CommentarySubmission> matches = commentaryRepository.findMatchingCommentary(
                        matchFormat,
                        candidatePhase,
                        candidateBowlerType,
                        eventType,
                        wicketSituation,
                        batsmanState,
                        matchPressure
                );
                for (CommentarySubmission match : matches) {
                    merged.putIfAbsent(match.getId(), match);
                }
            }
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * Increment usage count
     */
    @Transactional
    public void incrementUsage(UUID submissionId) {
        commentaryRepository.findById(submissionId).ifPresent(c -> {
            c.setTimesUsed(c.getTimesUsed() + 1);
            commentaryRepository.save(c);
        });
    }

    // Helper: Convert to map
    private Map<String, Object> toMap(CommentarySubmission c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("commentaryText", c.getCommentaryText());
        m.put("status", c.getStatus());
        m.put("matchFormat", c.getMatchFormat());
        m.put("phase", c.getPhase());
        m.put("bowlerType", c.getBowlerType());
        m.put("eventType", c.getEventType());
        m.put("timesUsed", c.getTimesUsed());
        m.put("adminNotes", c.getAdminNotes());
        m.put("createdAt", c.getCreatedAt());
        m.put("reviewedAt", c.getReviewedAt());
        return m;
    }

    private Map<String, Object> toMapWithUser(CommentarySubmission c) {
        Map<String, Object> m = toMap(c);
        m.put("username", c.getUser().getUsername());
        m.put("teamName", c.getTeam().getTeamName());
        return m;
    }
}
