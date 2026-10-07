package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "training_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(nullable = false)
    private String trainingType;  // BAT, BOWL, AR, FLD, WK, STAMINA, MENTAL, GENERAL

    @Column(nullable = false)
    private String skill;         // batRating, bowlRating, keeperRating, fldRating, stamina, confidence

    @Column(nullable = false)
    private int oldValue;

    @Column(nullable = false)
    private int newValue;

    @Column(nullable = false)
    private int change;           // positive = pop, negative = flop

    @CreationTimestamp
    private LocalDateTime trainedAt;
}
