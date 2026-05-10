package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "bowling_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BowlingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lineup_id", nullable = false)
    private MatchLineup lineup;

    @Column(name = "over_number", nullable = false)
    private Integer overNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bowler_id", nullable = false)
    private Player bowler;

    @Column(nullable = false, length = 5)
    @Builder.Default
    private String aggression = "N";
}
