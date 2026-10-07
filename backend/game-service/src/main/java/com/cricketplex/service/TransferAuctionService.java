package com.cricketplex.service;

import com.cricketplex.client.TransferEventClient;
import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatically finalizes expired transfer market auctions every 30 seconds.
 * If the highest bidder still has funds → sale completes.
 * If no bids → listing expires (CANCELLED, fee forfeited).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferAuctionService {

    private static final long MIN_TM_TAX = 5000L;

    private final TransferListingRepository listingRepository;
    private final TransferBidRepository bidRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final ActivityLogService activityLogService;
    private final TransferEventClient transferEventClient;
    private final TrainingAssignmentRepository trainingAssignmentRepository;

    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    @Transactional
    public void finalizeExpiredAuctions() {
        List<TransferListing> expired = listingRepository.findExpiredAuctions(LocalDateTime.now(ZoneOffset.UTC));
        for (TransferListing listing : expired) {
            try {
                finalizeAuction(listing);
            } catch (Exception e) {
                log.error("Failed to finalize auction {}: {}", listing.getId(), e.getMessage());
            }
        }
    }

    private void finalizeAuction(TransferListing listing) {
        Team sellerTeam = listing.getSellerTeam();

        // No bids → expire
        if (listing.getCurrentBidderTeam() == null) {
            listing.setStatus("EXPIRED");
            listingRepository.save(listing);
            activityLogService.log(sellerTeam, "tm_expired",
                    listing.getPlayer().getFirstName() + " " + listing.getPlayer().getLastName()
                    + " auction expired with no bids. Listing fee forfeited.");
            broadcastAuctionComplete(listing, "EXPIRED", null, null);
            log.info("Auction {} expired (no bids)", listing.getId());
            return;
        }

        // Has a winning bid
        Team buyerTeam = listing.getCurrentBidderTeam();
        long salePrice = listing.getCurrentBid();

        // Check buyer still has funds
        if (buyerTeam.getFunds() < salePrice) {
            // Buyer can no longer afford — expire the listing
            listing.setStatus("EXPIRED");
            listingRepository.save(listing);
            activityLogService.log(sellerTeam, "tm_expired",
                    listing.getPlayer().getFirstName() + " " + listing.getPlayer().getLastName()
                    + " auction failed — winning bidder (" + buyerTeam.getTeamName() + ") has insufficient funds.");
            broadcastAuctionComplete(listing, "EXPIRED", null, null);
            log.warn("Auction {} failed — buyer {} has insufficient funds", listing.getId(), buyerTeam.getTeamName());
            return;
        }

        // Execute the sale
        buyerTeam.setFunds(buyerTeam.getFunds() - salePrice);
        sellerTeam.setFunds(sellerTeam.getFunds() + salePrice);

        // TM tax — 5% of sale price, deducted from seller's proceeds (listing fee already paid separately)
        long totalTax = Math.max((long) (salePrice * 0.05), MIN_TM_TAX);
        sellerTeam.setFunds(sellerTeam.getFunds() - totalTax);

        // Transfer player
        Player player = listing.getPlayer();
        // Release any focused/general training assignment for this player from the seller team
        trainingAssignmentRepository.deleteByTeamIdAndPlayerId(sellerTeam.getId(), player.getId());
        player.setTeam(buyerTeam);
        playerRepository.save(player);

        // Update listing
        listing.setStatus("SOLD");
        listing.setBuyerTeam(buyerTeam);
        listing.setSalePrice(salePrice);
        listing.setSoldAt(LocalDateTime.now(ZoneOffset.UTC));
        listingRepository.save(listing);

        teamRepository.save(sellerTeam);
        teamRepository.save(buyerTeam);

        String playerName = player.getFirstName() + " " + player.getLastName();
        logTransaction(buyerTeam, "TM_PURCHASE",
                "Purchased " + playerName + " from " + sellerTeam.getTeamName(), -salePrice);
        logTransaction(sellerTeam, "TM_SALE",
                "Sold " + playerName + " to " + buyerTeam.getTeamName(), salePrice);
        logTransaction(sellerTeam, "TM_TAX",
                "TM tax (5%) on sale of " + playerName, -totalTax);

        activityLogService.log(buyerTeam, "bought",
                "Won auction for " + playerName + " from " + sellerTeam.getTeamName()
                + " for $" + String.format("%,d", salePrice) + ".");
        activityLogService.log(sellerTeam, "sold",
                "Auction completed: " + playerName + " sold to " + buyerTeam.getTeamName()
                + " for $" + String.format("%,d", salePrice) + ".");

        broadcastAuctionComplete(listing, "SOLD", buyerTeam.getTeamName(), salePrice);

        log.info("Auction {} finalized: {} sold to {} for ${}", listing.getId(),
                playerName, buyerTeam.getTeamName(), salePrice);
    }

    private void broadcastAuctionComplete(TransferListing listing, String status, String buyerName, Long salePrice) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "AUCTION_COMPLETE");
        msg.put("listingId", listing.getId());
        msg.put("status", status);
        if (buyerName != null) {
            msg.put("buyerTeam", buyerName);
            msg.put("salePrice", salePrice);
        }
        transferEventClient.broadcast(msg);
    }

    private void logTransaction(Team team, String type, String description, long amount) {
        transactionLogRepository.save(TransactionLog.builder()
                .team(team)
                .type(type)
                .description(description)
                .amount(amount)
                .balanceAfter(team.getFunds())
                .build());
    }
}
