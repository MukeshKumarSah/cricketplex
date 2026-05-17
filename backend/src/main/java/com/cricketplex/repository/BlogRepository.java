package com.cricketplex.repository;

import com.cricketplex.entity.Blog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.UUID;

public interface BlogRepository extends JpaRepository<Blog, UUID> {

    @Query("SELECT b FROM Blog b ORDER BY b.isPinned DESC, b.createdAt DESC")
    Page<Blog> findAllOrderByPinnedDesc(Pageable pageable);

    boolean existsByAuthorIdAndCreatedAtAfter(UUID authorId, LocalDateTime since);

    long countByCreatedAtAfter(LocalDateTime since);
}
