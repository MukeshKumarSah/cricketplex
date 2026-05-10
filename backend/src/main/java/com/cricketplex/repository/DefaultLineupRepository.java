package com.cricketplex.repository;

import com.cricketplex.entity.DefaultLineup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DefaultLineupRepository extends JpaRepository<DefaultLineup, UUID> {
    Optional<DefaultLineup> findByTeamIdAndFormat(UUID teamId, String format);
}
