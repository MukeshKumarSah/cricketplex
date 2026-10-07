package com.cricketplex.repository;

import com.cricketplex.entity.Innings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InningsRepository extends JpaRepository<Innings, UUID> {
    List<Innings> findByMatchResultIdOrderByInningsNumberAsc(UUID matchResultId);

    @Modifying
    @Query("DELETE FROM Innings i WHERE i.matchResult.id IN " +
           "(SELECT mr.id FROM MatchResult mr WHERE mr.fixture.simSessionId = :simId)")
    void deleteBySimSessionId(@Param("simId") UUID simId);
}
