package com.cricketplex.repository;

import com.cricketplex.entity.TransferBid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferBidRepository extends JpaRepository<TransferBid, UUID> {

    List<TransferBid> findByListingIdOrderByBidAmountDesc(UUID listingId);

    Optional<TransferBid> findFirstByListingIdOrderByBidAmountDesc(UUID listingId);

    List<TransferBid> findByBidderTeamIdOrderByBidAtDesc(UUID bidderTeamId);

    boolean existsByListingIdAndBidderTeamId(UUID listingId, UUID bidderTeamId);
}
