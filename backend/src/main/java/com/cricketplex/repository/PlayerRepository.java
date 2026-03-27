package com.cricketplex.repository;

import com.cricketplex.entity.Player;
import com.cricketplex.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    List<Player> findByTeam(Team team);

    boolean existsByFirstNameIgnoreCaseAndLastNameIgnoreCase(String firstName, String lastName);

    @Query("SELECT LOWER(p.firstName) || '|' || LOWER(p.lastName) FROM Player p")
    Set<String> findAllNameCombos();

    @Query("SELECT p FROM Player p WHERE LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Player> searchByName(@Param("q") String query);
}
