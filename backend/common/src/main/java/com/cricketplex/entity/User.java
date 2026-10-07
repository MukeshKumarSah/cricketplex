package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.USER;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isSupporter = false;

    @Column(length = 32)
    private String supporterPlan;

    private LocalDateTime supporterSince;

    private LocalDateTime supporterUntil;

    @Column(length = 32)
    private String supporterProvider;

    @Column(length = 128)
    private String supporterOrderId;

    @Column(length = 128)
    private String supporterPaymentId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isSubAdmin = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean acceptedTerms = false;

    private String profilePicUrl;

    @Column(nullable = false)
    @Builder.Default
    private String theme = "dark";

    @Column(nullable = false)
    @Builder.Default
    private Boolean teamSetupDone = false;

    @Column(name = "active_team_id")
    private UUID activeTeamId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(length = 128, unique = true)
    private String emailVerificationToken;

    private LocalDateTime emailVerificationTokenExpiresAt;

    @Column(length = 128, unique = true)
    private String passwordResetToken;

    private LocalDateTime passwordResetTokenExpiresAt;

    private LocalDateTime lastActiveAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
