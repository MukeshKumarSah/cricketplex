package com.cricketplex.repository;

import com.cricketplex.entity.FriendlyTournamentTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendlyTournamentTeamRepository extends JpaRepository<FriendlyTournamentTeam, UUID> {

    List<FriendlyTournamentTeam> findByTournamentId(UUID tournamentId);

    List<FriendlyTournamentTeam> findByTournamentIdAndInviteStatus(UUID tournamentId, String inviteStatus);

    Optional<FriendlyTournamentTeam> findByTournamentIdAndTeamId(UUID tournamentId, UUID teamId);

    boolean existsByTournamentIdAndTeamId(UUID tournamentId, UUID teamId);

    @Query("SELECT ftt FROM FriendlyTournamentTeam ftt WHERE ftt.team.owner.id = :userId AND ftt.inviteStatus = 'INVITED'")
    List<FriendlyTournamentTeam> findPendingInvitesForUser(@Param("userId") UUID userId);

    long countByTournamentIdAndInviteStatus(UUID tournamentId, String inviteStatus);
}
