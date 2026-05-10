package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "lineup_players")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LineupPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lineup_id", nullable = false)
    private MatchLineup lineup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "batting_position", nullable = false)
    private Integer battingPosition;

    @Column(name = "bat_aggression", nullable = false, length = 5)
    @Builder.Default
    private String batAggression = "N";
}
