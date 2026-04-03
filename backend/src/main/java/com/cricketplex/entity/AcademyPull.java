package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "academy_pulls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademyPull {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(nullable = false)
    private String requestedRole;   // BATSMAN, BOWLER, ALL_ROUNDER, KEEPER

    @Column(nullable = false)
    private String pulledFromCountry;

    @CreationTimestamp
    private LocalDateTime pulledAt;
}
