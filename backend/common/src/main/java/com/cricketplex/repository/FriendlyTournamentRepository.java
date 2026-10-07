package com.cricketplex.repository;

import com.cricketplex.entity.FriendlyTournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendlyTournamentRepository extends JpaRepository<FriendlyTournament, UUID> {

    List<FriendlyTournament> findByIsPublicTrueAndStatusNotOrderByCreatedAtDesc(String status);

    @Query("SELECT t FROM FriendlyTournament t WHERE t.createdBy.id = :userId ORDER BY t.createdAt DESC")
    List<FriendlyTournament> findByCreatedByIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    Optional<FriendlyTournament> findByJoinCode(String joinCode);

    @Query("""
        SELECT DISTINCT t FROM FriendlyTournament t
        JOIN FriendlyTournamentTeam ftt ON ftt.tournament = t
        WHERE ftt.team.owner.id = :userId AND ftt.inviteStatus = 'ACCEPTED'
        ORDER BY t.createdAt DESC
        """)
    List<FriendlyTournament> findJoinedByUserId(@Param("userId") UUID userId);

    @Query("""
        SELECT DISTINCT t FROM FriendlyTournament t
        JOIN FriendlyTournamentTeam ftt ON ftt.tournament = t
        WHERE ftt.team.owner.id = :userId AND ftt.inviteStatus = 'INVITED'
        ORDER BY t.createdAt DESC
        """)
    List<FriendlyTournament> findPendingInvitesByUserId(@Param("userId") UUID userId);
}
