package com.cricketplex.repository;

import com.cricketplex.entity.CommentarySubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentarySubmissionRepository extends JpaRepository<CommentarySubmission, UUID> {

    // Find by user
    List<CommentarySubmission> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Find by status
    List<CommentarySubmission> findByStatusOrderByCreatedAtDesc(String status);

    // Find by user and status
    List<CommentarySubmission> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);

    // Count by user and status
    long countByUserIdAndStatus(UUID userId, String status);

    // Find approved commentaries matching criteria (for match engine)
    @Query("SELECT c FROM CommentarySubmission c WHERE c.status = 'approved' " +
           "AND c.matchFormat = :matchFormat " +
           "AND c.phase = :phase " +
           "AND c.bowlerType = :bowlerType " +
           "AND c.eventType = :eventType " +
           "AND (:wicketSituation IS NULL OR c.wicketSituation IS NULL OR c.wicketSituation = :wicketSituation) " +
           "AND (:batsmanState IS NULL OR c.batsmanState IS NULL OR c.batsmanState = :batsmanState) " +
           "AND (:matchPressure IS NULL OR c.matchPressure IS NULL OR c.matchPressure = :matchPressure)")
    List<CommentarySubmission> findMatchingCommentary(
            @Param("matchFormat") String matchFormat,
            @Param("phase") String phase,
            @Param("bowlerType") String bowlerType,
            @Param("eventType") String eventType,
            @Param("wicketSituation") String wicketSituation,
            @Param("batsmanState") String batsmanState,
            @Param("matchPressure") String matchPressure
    );

    // Find similar commentary using trigram similarity (for duplicate detection)
    @Query(value = "SELECT * FROM commentary_submissions " +
                   "WHERE event_type = :eventType " +
                   "AND status IN ('pending', 'approved') " +
                   "AND similarity(commentary_text, :text) > 0.8 " +
                   "ORDER BY similarity(commentary_text, :text) DESC " +
                   "LIMIT 5",
           nativeQuery = true)
    List<CommentarySubmission> findSimilarCommentary(
            @Param("text") String text,
            @Param("eventType") String eventType
    );

    // Check exact duplicate
    boolean existsByCommentaryTextAndEventType(String commentaryText, String eventType);

    // Count approved commentaries by event type (for coverage gaps report)
    @Query("SELECT c.eventType, COUNT(c) FROM CommentarySubmission c " +
           "WHERE c.status = 'approved' " +
           "GROUP BY c.eventType")
    List<Object[]> countApprovedByEventType();

    // Top contributors
    @Query("SELECT c.user.id, c.user.username, COUNT(c) FROM CommentarySubmission c " +
           "WHERE c.status = 'approved' " +
           "GROUP BY c.user.id, c.user.username " +
           "ORDER BY COUNT(c) DESC")
    List<Object[]> findTopContributors();
}
