package com.cricketplex.repository;

import com.cricketplex.entity.FriendlyTournamentStanding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendlyTournamentStandingRepository extends JpaRepository<FriendlyTournamentStanding, UUID> {

    @Query("""
        SELECT s FROM FriendlyTournamentStanding s
        WHERE s.tournament.id = :tournamentId
        ORDER BY s.points DESC, s.nrr DESC, s.won DESC
        """)
    List<FriendlyTournamentStanding> findByTournamentIdOrdered(@Param("tournamentId") UUID tournamentId);

    Optional<FriendlyTournamentStanding> findByTournamentIdAndTeamId(UUID tournamentId, UUID teamId);
}
