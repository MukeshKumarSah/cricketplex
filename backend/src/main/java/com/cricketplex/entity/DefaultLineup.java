package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "default_lineups",
       uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "format"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DefaultLineup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "format", nullable = false, length = 10)
    private String format;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lineup_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> lineupData;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
