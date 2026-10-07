package com.cricketplex.repository;

import com.cricketplex.entity.Trophy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrophyRepository extends JpaRepository<Trophy, UUID> {

    List<Trophy> findByTeamIdOrderBySeasonDesc(UUID teamId);

    long countByTeamId(UUID teamId);
}
