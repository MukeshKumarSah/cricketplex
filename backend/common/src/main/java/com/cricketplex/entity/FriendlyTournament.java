package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "friendly_tournaments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FriendlyTournament {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    /** LEAGUE or KNOCKOUT */
    @Column(nullable = false, length = 20)
    private String type;

    /** T20 or ODI */
    @Column(nullable = false, length = 10)
    private String format;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /** REGISTRATION → ACTIVE → COMPLETED | CANCELLED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "REGISTRATION";

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = true;

    /** Short alphanumeric code teams can use to find & join */
    @Column(name = "join_code", length = 12, unique = true)
    private String joinCode;

    @Column(name = "registration_deadline")
    private LocalDateTime registrationDeadline;

    @Column(name = "start_date")
    private LocalDate startDate;

    /** WEEKLY | BIWEEKLY | TRIWEEKLY */
    @Column(name = "schedule_type", nullable = false, length = 20)
    @Builder.Default
    private String scheduleType = "WEEKLY";

    /** Comma-separated day names, e.g. "SATURDAY" or "MONDAY,THURSDAY" */
    @Column(name = "schedule_days", length = 100)
    private String scheduleDays;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
