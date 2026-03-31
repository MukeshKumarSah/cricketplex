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

    @CreationTimestamp
    private LocalDateTime createdAt;
}
