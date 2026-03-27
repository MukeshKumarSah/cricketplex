package com.cricketplex.repository;

import com.cricketplex.entity.HomeMatch;
import com.cricketplex.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HomeMatchRepository extends JpaRepository<HomeMatch, UUID> {
    List<HomeMatch> findByTeamAndMatchDateGreaterThanEqualOrderByMatchDateAsc(Team team, LocalDate date);
}
