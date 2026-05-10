package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "batting_scorecards")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BattingScorecard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "innings_id", nullable = false)
    private Innings innings;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "batting_position", nullable = false)
    private Integer battingPosition;

    @Column(name = "runs_scored", nullable = false)
    @Builder.Default
    private Integer runsScored = 0;

    @Column(name = "balls_faced", nullable = false)
    @Builder.Default
    private Integer ballsFaced = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer fours = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer sixes = 0;

    @Column(name = "dismissal_type", length = 30)
    private String dismissalType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bowler_id")
    private Player bowler;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fielder_id")
    private Player fielder;

    @Column(name = "strike_rate", nullable = false)
    @Builder.Default
    private Double strikeRate = 0.0;
}
