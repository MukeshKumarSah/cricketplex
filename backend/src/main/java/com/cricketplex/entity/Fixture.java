package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fixtures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fixture {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id")
    private League league;

    @Column
    @Builder.Default
    private Integer round = 0;

    @Column(name = "match_number")
    @Builder.Default
    private Integer matchNumber = 0;

    @Column(name = "match_type", nullable = false, length = 20)
    @Builder.Default
    private String matchType = "LEAGUE";

    @Column(length = 10)
    private String format;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @Column(name = "match_date")
    private LocalDate matchDate;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "SCHEDULED";

    @Column(name = "pitch_type", nullable = false, length = 30)
    @Builder.Default
    private String pitchType = "STANDARD";

    // ─── FC day-based simulation fields ───

    /** FC match day: 0 = not started, 1 = day 1 complete, 2 = match done */
    @Column(name = "fc_day")
    @Builder.Default
    private Integer fcDay = 0;

    /** 1st innings: declare when total score reaches this value (null = no declaration) */
    @Column(name = "fc_declare_inn1")
    private Integer fcDeclareInn1;

    /** 2nd innings: declare when leading by this many runs (null = no declaration) */
    @Column(name = "fc_declare_inn2_lead")
    private Integer fcDeclareInn2Lead;

    /** Follow-on choice: true = enforce, false = don't, null = not yet applicable */
    @Column(name = "fc_follow_on")
    private Boolean fcFollowOn;

    /** 3rd innings: declare when leading by this many runs (null = no declaration) */
    @Column(name = "fc_declare_inn3_lead")
    private Integer fcDeclareInn3Lead;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
