package com.cricketplex.repository;

import com.cricketplex.entity.BowlingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BowlingOrderRepository extends JpaRepository<BowlingOrder, UUID> {
    List<BowlingOrder> findByLineupIdOrderByOverNumberAsc(UUID lineupId);
    void deleteByLineupId(UUID lineupId);

    @Modifying
    @Query("DELETE FROM BowlingOrder bo WHERE bo.lineup.id IN " +
           "(SELECT ml.id FROM MatchLineup ml WHERE ml.fixture.simSessionId = :simId)")
    void deleteBySimSessionId(@Param("simId") UUID simId);
}
