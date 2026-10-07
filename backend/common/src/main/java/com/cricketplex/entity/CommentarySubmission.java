package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "commentary_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentarySubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "commentary_text", nullable = false, length = 500)
    private String commentaryText;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "pending"; // pending, approved, rejected

    @Column(name = "match_format", nullable = false, length = 10)
    private String matchFormat; // T20, ODI, TEST

    @Column(nullable = false, length = 20)
    private String phase; // powerplay, middle, death, pressure, cruising

    @Column(name = "bowler_type", nullable = false, length = 20)
    private String bowlerType; // FAST_SEAM, FAST, MEDIUM_FAST, MEDIUM, SPINNER

    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType; // 0, 1, 4, 6, 1LB, 2NB, BOWLED, CAUGHT, etc.

    @Column(name = "wicket_situation", length = 30)
    private String wicketSituation; // early_wickets, collapse, rebuilding, set_partnership

    @Column(name = "batsman_state", length = 30)
    private String batsmanState; // new_batsman, settling, set, milestone_approaching

    @Column(name = "match_pressure", length = 20)
    private String matchPressure; // low, medium, high

    @Column(name = "extra_tags", columnDefinition = "TEXT")
    private String extraTags; // JSON array: ["catch_taken", "great_fielding", ...]

    @Column(name = "placeholders_used", columnDefinition = "TEXT")
    private String placeholdersUsed; // JSON array: ["batsman", "fielder", "runs"]

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "times_used", nullable = false)
    @Builder.Default
    private Integer timesUsed = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_edited_by")
    private User lastEditedBy;

    @Column(name = "last_edited_at")
    private LocalDateTime lastEditedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
