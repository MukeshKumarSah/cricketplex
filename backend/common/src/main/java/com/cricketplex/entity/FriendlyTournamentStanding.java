package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "friendly_tournament_standings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FriendlyTournamentStanding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private FriendlyTournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false) @Builder.Default private int played = 0;
    @Column(nullable = false) @Builder.Default private int won    = 0;
    @Column(nullable = false) @Builder.Default private int drawn  = 0;
    @Column(nullable = false) @Builder.Default private int lost   = 0;
    @Column(nullable = false) @Builder.Default private int points = 0;

    @Column(name = "runs_scored_total",   nullable = false) @Builder.Default private int runsScoredTotal   = 0;
    @Column(name = "runs_conceded_total", nullable = false) @Builder.Default private int runsConcededTotal = 0;
    @Column(name = "overs_faced_total",   nullable = false) @Builder.Default private double oversFacedTotal   = 0.0;
    @Column(name = "overs_bowled_total",  nullable = false) @Builder.Default private double oversBowledTotal  = 0.0;
    @Column(nullable = false) @Builder.Default private double nrr = 0.0;
}
