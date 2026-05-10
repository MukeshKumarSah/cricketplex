package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "bowling_scorecards")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BowlingScorecard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "innings_id", nullable = false)
    private Innings innings;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(nullable = false)
    @Builder.Default
    private Double overs = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private Integer maidens = 0;

    @Column(name = "runs_conceded", nullable = false)
    @Builder.Default
    private Integer runsConceded = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer wickets = 0;

    @Column(nullable = false)
    @Builder.Default
    private Double economy = 0.0;

    @Column(name = "dot_balls", nullable = false)
    @Builder.Default
    private Integer dotBalls = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer wides = 0;

    @Column(name = "no_balls", nullable = false)
    @Builder.Default
    private Integer noBalls = 0;
}
