package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cup_teams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CupTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cup_id", nullable = false)
    private Cup cup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /** 1 = best seed (top human team by rating). */
    @Column(name = "seed_rank", nullable = false)
    private Integer seedRank;

    /**
     * Position in the bracket (1 .. bracketSize).
     * Assigned at draw time using the standard seeding algorithm.
     * Never changes – used to determine match pairings each round.
     */
    @Column(name = "bracket_position", nullable = false)
    private Integer bracketPosition;

    /**
     * Round in which this team was knocked out (null = still alive / champion).
     * Set after losing a match.
     */
    @Column(name = "eliminated_round")
    private Integer eliminatedRound;

    /** Prize money (coins) earned from this cup run. */
    @Column(name = "prize_won", nullable = false)
    @Builder.Default
    private Long prizeWon = 0L;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
