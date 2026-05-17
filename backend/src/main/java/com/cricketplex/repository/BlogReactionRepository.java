package com.cricketplex.repository;

import com.cricketplex.entity.BlogReaction;
import com.cricketplex.entity.BlogReactionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BlogReactionRepository extends JpaRepository<BlogReaction, BlogReactionId> {

    List<BlogReaction> findByBlogId(UUID blogId);

    boolean existsByBlogIdAndUserIdAndReaction(UUID blogId, UUID userId, String reaction);

    void deleteByBlogIdAndUserIdAndReaction(UUID blogId, UUID userId, String reaction);
}
