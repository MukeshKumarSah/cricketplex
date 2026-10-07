package com.cricketplex.repository;

import com.cricketplex.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TransactionLogRepository extends JpaRepository<TransactionLog, UUID> {

    List<TransactionLog> findByTeamIdOrderByCreatedAtDesc(UUID teamId);

    @Query("SELECT t FROM TransactionLog t WHERE t.team.id = :teamId AND t.type = :type ORDER BY t.createdAt DESC")
    List<TransactionLog> findByTeamIdAndType(UUID teamId, String type);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionLog t WHERE t.team.id = :teamId AND t.amount > 0")
    Long getTotalIncome(UUID teamId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionLog t WHERE t.team.id = :teamId AND t.amount < 0")
    Long getTotalExpenses(UUID teamId);
}
