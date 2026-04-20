package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sim_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SimSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 10)
    private String format;

    @Column(name = "num_matches", nullable = false)
    private Integer numMatches;

    /** Full JSON config: team names, player stats, bowling plans, pitch settings */
    @Column(name = "config_json", columnDefinition = "TEXT", nullable = false)
    private String configJson;

    /** PENDING | RUNNING | DONE | ERROR */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** Aggregated result JSON — populated once status = DONE */
    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
