package com.cricketplex.repository;

import com.cricketplex.entity.StadiumSeats;
import com.cricketplex.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StadiumSeatsRepository extends JpaRepository<StadiumSeats, UUID> {
    Optional<StadiumSeats> findByTeam(Team team);
}
