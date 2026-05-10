package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ball_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BallEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "innings_id", nullable = false)
    private Innings innings;

    @Column(name = "over_number", nullable = false)
    private Integer overNumber;

    @Column(name = "ball_number", nullable = false)
    private Integer ballNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batsman_id", nullable = false)
    private Player batsman;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bowler_id", nullable = false)
    private Player bowler;

    @Column(nullable = false)
    @Builder.Default
    private Integer runs = 0;

    @Column(name = "is_wicket", nullable = false)
    @Builder.Default
    private Boolean isWicket = false;

    @Column(name = "is_boundary", nullable = false)
    @Builder.Default
    private Boolean isBoundary = false;

    @Column(name = "is_six", nullable = false)
    @Builder.Default
    private Boolean isSix = false;

    @Column(name = "is_wide", nullable = false)
    @Builder.Default
    private Boolean isWide = false;

    @Column(name = "is_no_ball", nullable = false)
    @Builder.Default
    private Boolean isNoBall = false;

    @Column(name = "is_bye", nullable = false)
    @Builder.Default
    private Boolean isBye = false;

    @Column(name = "is_leg_bye", nullable = false)
    @Builder.Default
    private Boolean isLegBye = false;

    @Column(name = "dismissal_type", length = 30)
    private String dismissalType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fielder_id")
    private Player fielder;

    @Column(columnDefinition = "TEXT")
    private String commentary;
}
