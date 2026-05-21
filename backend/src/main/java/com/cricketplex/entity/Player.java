package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "players")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(nullable = false)
    private String role;           // BATSMAN, BOWLER, ALL_ROUNDER, KEEPER

    @Column(nullable = false)
    private int age;

    @Column(name = "age_days", nullable = false)
    @Builder.Default
    private int ageDays = 0;

    @Column(name = "bat_hand", nullable = false)
    private String batHand;        // RH, LH

    @Column(name = "bowl_hand")
    private String bowlHand;       // RH, LH (null for pure batsmen/keepers)

    @Column(name = "bowl_type")
    private String bowlType;       // FS, WS, F, M, FM, MF (null for pure batsmen/keepers)

    @Column(name = "bat_rating", nullable = false, columnDefinition = "NUMERIC(5,2)")
    private double batRating;

    @Column(name = "bowl_rating", nullable = false, columnDefinition = "NUMERIC(5,2)")
    private double bowlRating;

    @Column(name = "keeper_rating", nullable = false, columnDefinition = "NUMERIC(5,2)")
    private double keeperRating;

    @Column(name = "fld_rating", nullable = false, columnDefinition = "NUMERIC(5,2)")
    private double fldRating;

    @Column(nullable = false)
    private int experience;

    @Column(nullable = false, columnDefinition = "NUMERIC(5,2)")
    private double stamina;

    @Column(nullable = false)
    @Builder.Default
    private int fitness = 100;

    @Column(name = "bat_aggression", nullable = false)
    private String batAggression;  // D, N, A

    @Column(name = "bowl_aggression", nullable = false)
    private String bowlAggression; // D, N, A

    private String nationality;

    @Column(nullable = false)
    @Builder.Default
    private int wage = 500;

    @Column(nullable = false)
    @Builder.Default
    private int rating = 20;

    @Column(nullable = false, columnDefinition = "NUMERIC(5,2)")
    @Builder.Default
    private double confidence = 50.0;

    @Column(name = "sim_session_id")
    private UUID simSessionId;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
