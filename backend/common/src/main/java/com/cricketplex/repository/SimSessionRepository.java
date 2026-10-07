package com.cricketplex.repository;

import com.cricketplex.entity.SimSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SimSessionRepository extends JpaRepository<SimSession, UUID> {
    List<SimSession> findAllByOrderByCreatedAtDesc();
}
