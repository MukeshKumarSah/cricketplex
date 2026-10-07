package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String teamName;

    @Column(nullable = false)
    private String country;

    private String groundName;

    private String teamProfilePicUrl;

    @Column(nullable = false)
    @Builder.Default
    private Integer odiRating = 1000;

    @Column(nullable = false, name = "t20_rating")
    @Builder.Default
    private Integer t20Rating = 1000;

    @Column(nullable = false)
    @Builder.Default
    private Integer fcRating = 1000;

    @Column(nullable = false)
    @Builder.Default
    private Integer fans = 1000;

    @Column(nullable = false)
    @Builder.Default
    private Integer morale = 50;

    @Column(name = "sim_session_id")
    private UUID simSessionId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isBot = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer academyLevel = 1;

    @Column(nullable = false)
    @Builder.Default
    private Long funds = 50000L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "team_order")
    @Builder.Default
    private Integer teamOrder = 1; // 1 = primary team, 2 = secondary team

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
