package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stadium_seats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StadiumSeats {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false, unique = true)
    private Team team;

    @Column(nullable = false)
    @Builder.Default
    private Integer premium = 500;

    @Column(nullable = false)
    @Builder.Default
    private Integer standard = 1000;

    @Column(nullable = false)
    @Builder.Default
    private Integer economy = 1500;

    @Column(nullable = false)
    @Builder.Default
    private Integer standing = 2000;

    @Column(name = "default_pitch", nullable = false, length = 20)
    @Builder.Default
    private String defaultPitch = "STANDARD";

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
