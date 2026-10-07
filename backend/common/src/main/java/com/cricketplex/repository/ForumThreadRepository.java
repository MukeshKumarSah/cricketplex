package com.cricketplex.repository;

import com.cricketplex.entity.ForumCategory;
import com.cricketplex.entity.ForumThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ForumThreadRepository extends JpaRepository<ForumThread, UUID> {

    Page<ForumThread> findByCategoryOrderByIsPinnedDescLastActivityAtDesc(ForumCategory category, Pageable pageable);

    long countByCategory(ForumCategory category);

    // Panel queries
    @Query("SELECT t FROM ForumThread t ORDER BY t.commentCount DESC, t.lastActivityAt DESC")
    List<ForumThread> findMostActiveThreads(Pageable pageable);

    @Query("SELECT t FROM ForumThread t ORDER BY t.createdAt DESC")
    List<ForumThread> findRecentlyCreatedThreads(Pageable pageable);

    @Query("SELECT t FROM ForumThread t ORDER BY t.lastActivityAt DESC")
    List<ForumThread> findRecentActivityThreads(Pageable pageable);
}
