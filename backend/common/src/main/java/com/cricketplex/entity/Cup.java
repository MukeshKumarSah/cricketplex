package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Global season number (1-based). One cup per season. */
    @Column(nullable = false, unique = true)
    private Integer season;

    /** "T20" or "ODI" – determined by season parity (odd=ODI, even=T20). */
    @Column(nullable = false, length = 10)
    private String format;

    /** UPCOMING | ONGOING | COMPLETED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "UPCOMING";

    /** 256 | 512 | 1024 | 2048 | 4096 */
    @Column(name = "bracket_size", nullable = false)
    private Integer bracketSize;

    /** log2(bracketSize) */
    @Column(name = "total_rounds", nullable = false)
    private Integer totalRounds;

    /** 0 = not started; 1..totalRounds = currently in that round. */
    @Column(name = "current_round", nullable = false)
    @Builder.Default
    private Integer currentRound = 0;

    /** Date of Round 1 (Day 3 for ≤2048 bracket, Day 2 for 4096). */
    @Column(name = "start_date")
    private LocalDate startDate;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
