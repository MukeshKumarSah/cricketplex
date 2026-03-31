package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "leagues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String format = "T20";

    @Column(nullable = false)
    private Integer division;

    @Column(name = "league_number", nullable = false)
    private Integer leagueNumber;

    @Column(nullable = false)
    @Builder.Default
    private Integer season = 1;

    @Column(name = "match_start_time", nullable = false, length = 5)
    @Builder.Default
    private String matchStartTime = "14:00";

    @CreationTimestamp
    private LocalDateTime createdAt;
}
