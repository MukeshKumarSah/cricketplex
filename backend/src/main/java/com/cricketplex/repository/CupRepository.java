package com.cricketplex.repository;

import com.cricketplex.entity.Cup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface CupRepository extends JpaRepository<Cup, UUID> {

    Optional<Cup> findBySeason(Integer season);

    boolean existsBySeason(Integer season);

    List<Cup> findByStatus(String status);

    List<Cup> findAllByOrderBySeasonDesc();
}
