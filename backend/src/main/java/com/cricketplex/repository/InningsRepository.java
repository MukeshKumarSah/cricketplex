package com.cricketplex.repository;

import com.cricketplex.entity.Innings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InningsRepository extends JpaRepository<Innings, UUID> {
    List<Innings> findByMatchResultIdOrderByInningsNumberAsc(UUID matchResultId);
}
