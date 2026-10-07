package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "friendly_challenges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendlyChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenger_team_id", nullable = false)
    private Team challengerTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenged_team_id", nullable = false)
    private Team challengedTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixture_id")
    private Fixture fixture;

    @Column(nullable = false, length = 10)
    private String format;

    @Column(name = "pitch_type", nullable = false, length = 30)
    @Builder.Default
    private String pitchType = "STANDARD";

    @Column(name = "match_date", nullable = false)
    private LocalDate matchDate;

    @Column(name = "match_time", length = 5)
    private String matchTime;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column
    private String message;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
