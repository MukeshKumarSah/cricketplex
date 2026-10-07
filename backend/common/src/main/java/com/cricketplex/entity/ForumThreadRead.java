package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "forum_thread_reads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForumThreadRead {

    @EmbeddedId
    private ForumThreadReadId id;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime lastReadAt = LocalDateTime.now();
}
