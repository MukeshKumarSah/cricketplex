package com.cricketplex.repository;

import com.cricketplex.entity.TransferListing;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferListingRepository extends JpaRepository<TransferListing, UUID> {

    List<TransferListing> findByStatusOrderByListedAtAsc(String status);

    List<TransferListing> findBySellerTeamIdOrderByListedAtDesc(UUID sellerTeamId);

    Optional<TransferListing> findByPlayerIdAndStatus(UUID playerId, String status);

    @Query("SELECT l FROM TransferListing l WHERE l.status = 'ACTIVE' AND l.auctionEndsAt <= :now")
    List<TransferListing> findExpiredAuctions(LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM TransferListing l WHERE l.id = :id")
    Optional<TransferListing> findByIdForUpdate(UUID id);

    List<TransferListing> findTop20ByStatusOrderBySoldAtDesc(String status);

    List<TransferListing> findTop50ByPlayerIdAndStatusOrderBySoldAtDesc(UUID playerId, String status);
}
