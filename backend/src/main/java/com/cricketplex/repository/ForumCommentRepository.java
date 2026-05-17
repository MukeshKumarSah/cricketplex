package com.cricketplex.repository;

import com.cricketplex.entity.ForumComment;
import com.cricketplex.entity.ForumThread;
import com.cricketplex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ForumCommentRepository extends JpaRepository<ForumComment, UUID> {

    List<ForumComment> findByThreadOrderByCreatedAtAsc(ForumThread thread);

    long countByThreadIdAndCreatedAtAfter(UUID threadId, LocalDateTime after);

    boolean existsByThreadIdAndCreatedBy(UUID threadId, User user);
}
