package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "innings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Innings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_result_id", nullable = false)
    private MatchResult matchResult;

    @Column(name = "innings_number", nullable = false)
    private Integer inningsNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batting_team_id", nullable = false)
    private Team battingTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bowling_team_id", nullable = false)
    private Team bowlingTeam;

    @Column(name = "total_runs", nullable = false)
    @Builder.Default
    private Integer totalRuns = 0;

    @Column(name = "total_wickets", nullable = false)
    @Builder.Default
    private Integer totalWickets = 0;

    @Column(name = "total_overs", nullable = false)
    @Builder.Default
    private Double totalOvers = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private Integer extras = 0;

    @Column(name = "all_out", nullable = false)
    @Builder.Default
    private Boolean allOut = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean declared = false;

    /** JSON state for resuming a mid-innings day break (null = innings not interrupted) */
    @Column(name = "resume_state", columnDefinition = "TEXT")
    private String resumeState;

    @OneToMany(mappedBy = "innings", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BattingScorecard> battingCards = new ArrayList<>();

    @OneToMany(mappedBy = "innings", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BowlingScorecard> bowlingCards = new ArrayList<>();

    @OneToMany(mappedBy = "innings", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BallEvent> ballEvents = new ArrayList<>();
}
