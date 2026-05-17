package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "friendly_tournament_teams")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FriendlyTournamentTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private FriendlyTournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /** INVITED | ACCEPTED | DECLINED */
    @Column(name = "invite_status", nullable = false, length = 20)
    @Builder.Default
    private String inviteStatus = "INVITED";

    @Column(name = "invited_at", nullable = false)
    @Builder.Default
    private LocalDateTime invitedAt = LocalDateTime.now();

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
}
