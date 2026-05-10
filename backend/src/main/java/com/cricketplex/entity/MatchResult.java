package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "match_results")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixture_id", nullable = false)
    private Fixture fixture;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "toss_winner_id", nullable = false)
    private Team tossWinner;

    @Column(name = "toss_decision", nullable = false, length = 10)
    private String tossDecision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private Team winner;

    @Column(name = "result_type", nullable = false, length = 20)
    @Builder.Default
    private String resultType = "PENDING";

    @Column(name = "result_margin")
    private Integer resultMargin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "man_of_match_id")
    private Player manOfMatch;

    @OneToMany(mappedBy = "matchResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("inningsNumber ASC")
    @Builder.Default
    private List<Innings> inningsList = new ArrayList<>();

    @Column(name = "attendance")
    private Integer attendance;

    @Column(name = "attendance_breakdown")
    private String attendanceBreakdown;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
