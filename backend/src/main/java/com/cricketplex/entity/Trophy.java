package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trophies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trophy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /** e.g. "T20", "ODI", "FC" */
    @Column(nullable = false, length = 10)
    private String format;

    /** e.g. "India" */
    @Column(nullable = false, length = 60)
    private String country;

    /** Division the league was in when won */
    @Column(nullable = false)
    private Integer division;

    /** League number within the division */
    @Column(name = "league_number", nullable = false)
    private Integer leagueNumber;

    /** Season number when the trophy was won */
    @Column(nullable = false)
    private Integer season;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
