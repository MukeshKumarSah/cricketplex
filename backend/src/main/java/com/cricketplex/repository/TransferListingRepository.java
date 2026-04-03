package com.cricketplex.repository;

import com.cricketplex.entity.TransferListing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferListingRepository extends JpaRepository<TransferListing, UUID> {

    List<TransferListing> findByStatusOrderByListedAtDesc(String status);

    List<TransferListing> findBySellerTeamIdOrderByListedAtDesc(UUID sellerTeamId);

    Optional<TransferListing> findByPlayerIdAndStatus(UUID playerId, String status);
}
