package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "home_matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private String opponentName;

    @Column(nullable = false)
    private LocalDate matchDate;

    @Column(nullable = false)
    private String competition;

    @Column(nullable = false)
    @Builder.Default
    private String format = "T20";

    @Column(nullable = false)
    @Builder.Default
    private String pitchType = "STANDARD";

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
