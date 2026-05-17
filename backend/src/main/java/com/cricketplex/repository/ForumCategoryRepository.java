package com.cricketplex.repository;

import com.cricketplex.entity.ForumCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ForumCategoryRepository extends JpaRepository<ForumCategory, UUID> {
    List<ForumCategory> findAllByOrderByDisplayOrderAsc();
}
