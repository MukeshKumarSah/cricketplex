package com.cricketplex.repository;

import com.cricketplex.entity.CupTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CupTeamRepository extends JpaRepository<CupTeam, UUID> {

    List<CupTeam> findByCupIdOrderBySeedRankAsc(UUID cupId);

    List<CupTeam> findByCupIdOrderByBracketPositionAsc(UUID cupId);

    /** All teams still alive (not yet eliminated) sorted by bracket position. */
    List<CupTeam> findByCupIdAndEliminatedRoundIsNullOrderByBracketPositionAsc(UUID cupId);

    Optional<CupTeam> findByCupIdAndTeamId(UUID cupId, UUID teamId);

    boolean existsByCupIdAndTeamId(UUID cupId, UUID teamId);

    /** Find surviving bot slots (for human replacement). */
    @Query("SELECT ct FROM CupTeam ct JOIN FETCH ct.team t WHERE ct.cup.id = :cupId " +
           "AND t.isBot = true AND ct.eliminatedRound IS NULL ORDER BY ct.seedRank DESC")
    List<CupTeam> findSurvivingBotsByCupId(@Param("cupId") UUID cupId);
}
