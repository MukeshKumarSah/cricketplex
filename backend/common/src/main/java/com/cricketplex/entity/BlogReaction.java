package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "blog_reactions")
@IdClass(BlogReactionId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlogReaction {

    @Id
    @Column(name = "blog_id")
    private UUID blogId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "reaction", length = 10)
    private String reaction;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
