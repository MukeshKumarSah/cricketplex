package com.cricketplex.repository;

import com.cricketplex.entity.AcademyPull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AcademyPullRepository extends JpaRepository<AcademyPull, UUID> {
    List<AcademyPull> findByTeamIdOrderByPulledAtDesc(UUID teamId);
}
