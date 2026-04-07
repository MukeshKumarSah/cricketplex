package com.cricketplex.repository;

import com.cricketplex.entity.Player;
import com.cricketplex.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    List<Player> findByTeam(Team team);

    List<Player> findByTeamId(UUID teamId);

    boolean existsByFirstNameIgnoreCaseAndLastNameIgnoreCase(String firstName, String lastName);

    @Query("SELECT LOWER(p.firstName) || '|' || LOWER(p.lastName) FROM Player p")
    Set<String> findAllNameCombos();

    @Query("SELECT p FROM Player p WHERE LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Player> searchByName(@Param("q") String query);

    @Modifying
    @Query("UPDATE Player p SET p.ageDays = p.ageDays + 1")
    int incrementAgeDays();

    @Modifying
    @Query("UPDATE Player p SET p.age = p.age + 1, p.ageDays = 0 WHERE p.ageDays >= :daysPerSeason")
    int rollOverAge(@Param("daysPerSeason") int daysPerSeason);

    @Modifying
    @Query(value = "UPDATE players SET fitness = LEAST(100, fitness + 1 + (stamina * 5 / 100)) WHERE fitness < 100", nativeQuery = true)
    int recoverFitness();
}
