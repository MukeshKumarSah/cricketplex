package com.cricketplex.repository;

import com.cricketplex.entity.BowlingOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BowlingOrderRepository extends JpaRepository<BowlingOrder, UUID> {
    List<BowlingOrder> findByLineupIdOrderByOverNumberAsc(UUID lineupId);
    void deleteByLineupId(UUID lineupId);
}
