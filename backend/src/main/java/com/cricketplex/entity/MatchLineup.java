package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "match_lineups", uniqueConstraints = @UniqueConstraint(columnNames = {"fixture_id", "team_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchLineup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixture_id", nullable = false)
    private Fixture fixture;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "captain_id")
    private Player captain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keeper_id")
    private Player keeper;

    @Column(name = "toss_choice", length = 10)
    private String tossChoice;

    @Column(name = "bat_or_bowl", length = 10)
    private String batOrBowl;

    @Column(name = "bowling_plan", nullable = false, length = 20)
    @Builder.Default
    private String bowlingPlan = "BALANCED";

    @OneToMany(mappedBy = "lineup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LineupPlayer> players = new ArrayList<>();

    @OneToMany(mappedBy = "lineup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BowlingOrder> bowlingOrders = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
