package com.cricketplex.controller;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class TransferMarketController {

    private static final long MIN_TM_TAX = 5000L;
    private static final long MIN_STARTING_BID = 1000L;
    private static final long MIN_BID_INCREMENT = 1000L;
    private static final double BID_INCREMENT_PCT = 0.05;
    private static final long AUCTION_HOURS = 48L;
    private static final long ANTI_SNIPE_SECONDS = 120L; // 2 minutes

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final TransferListingRepository listingRepository;
    private final TransferBidRepository bidRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final ActivityLogService activityLogService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TrainingAssignmentRepository trainingAssignmentRepository;

    // ════════════════════════════════════════════
    //  POST /api/transfer/list — list player on TM (48h auction)
    // ════════════════════════════════════════════
    @PostMapping("/list")
    @Transactional
    public ResponseEntity<?> listPlayer(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body) {

        Team team = getTeam(principal);
        UUID playerId = UUID.fromString((String) body.get("playerId"));

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));

        if (player.getTeam() == null || !player.getTeam().getId().equals(team.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not your player"));
        }

        // Check if already listed
        Optional<TransferListing> existing = listingRepository.findByPlayerIdAndStatus(playerId, "ACTIVE");
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Player is already listed on the transfer market"));
        }

        long marketValue = calculateMarketValue(player);

        // Starting bid: seller can set their own, minimum $1,000
        long startingBid = Math.max(MIN_STARTING_BID, marketValue);
        if (body.get("startingPrice") != null) {
            long requested = ((Number) body.get("startingPrice")).longValue();
            if (requested >= MIN_STARTING_BID) {
                startingBid = requested;
            }
        }

        // Listing fee & tax based on starting bid (what seller actually lists at)
        long listingFee = (long) (startingBid * 0.20);
        long tmTax = Math.max((long) (startingBid * 0.05), MIN_TM_TAX);

        // Deduct listing fee
        if (team.getFunds() < listingFee) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Insufficient funds. Listing fee: $" + String.format("%,d", listingFee)));
        }
        team.setFunds(team.getFunds() - listingFee);
        teamRepository.save(team);
        logTransaction(team, "TM_LISTING_FEE", "Listing fee for " + player.getFirstName() + " " + player.getLastName(), -listingFee);

        TransferListing listing = TransferListing.builder()
                .sellerTeam(team)
                .player(player)
                .marketValue(marketValue)
                .listingFee(listingFee)
                .tmTax(tmTax)
                .status("ACTIVE")
                .currentBid(startingBid)
                .auctionEndsAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(AUCTION_HOURS))
                .build();
        listingRepository.save(listing);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("listingId", listing.getId());
        resp.put("marketValue", marketValue);
        resp.put("startingBid", startingBid);
        resp.put("listingFee", listingFee);
        resp.put("tmTax", tmTax);
        resp.put("auctionEndsAt", listing.getAuctionEndsAt().toString());
        resp.put("message", "Player listed for 48-hour auction. Listing fee of $" + String.format("%,d", listingFee) + " deducted.");

        activityLogService.log(team, "listed", "Listed " + player.getFirstName() + " " + player.getLastName() + " on Transfer Market (value $" + String.format("%,d", marketValue) + ", 48h auction).");

        return ResponseEntity.ok(resp);
    }

    // ════════════════════════════════════════════
    //  GET /api/transfer/listings — all active listings
    // ════════════════════════════════════════════
    @GetMapping("/listings")
    public ResponseEntity<?> getActiveListings(@AuthenticationPrincipal UserPrincipal principal) {
        Team myTeam = getTeam(principal);
        List<TransferListing> listings = listingRepository.findByStatusOrderByListedAtAsc("ACTIVE");

        List<Map<String, Object>> result = new ArrayList<>();
        for (TransferListing l : listings) {
            Map<String, Object> m = buildListingMap(l);
            m.put("isOwn", l.getSellerTeam().getId().equals(myTeam.getId()));
            m.put("currentBid", l.getCurrentBid());
            m.put("currentBidderTeam", l.getCurrentBidderTeam() != null ? l.getCurrentBidderTeam().getTeamName() : null);
            m.put("auctionEndsAt", l.getAuctionEndsAt() != null ? l.getAuctionEndsAt().toString() : null);

            // Bid status relative to current user
            boolean isLeading = l.getCurrentBidderTeam() != null && l.getCurrentBidderTeam().getId().equals(myTeam.getId());
            m.put("isLeadingBidder", isLeading);
            m.put("hasBid", isLeading || bidRepository.existsByListingIdAndBidderTeamId(l.getId(), myTeam.getId()));

            // Compute minimum next bid: max(currentBid * 1.05, currentBid + 1000)
            long current = l.getCurrentBid() != null ? l.getCurrentBid() : MIN_STARTING_BID;
            long minNextBid = Math.max((long) Math.ceil(current * (1.0 + BID_INCREMENT_PCT)), current + MIN_BID_INCREMENT);
            m.put("minNextBid", minNextBid);

            // Bid count
            m.put("bidCount", bidRepository.findByListingIdOrderByBidAmountDesc(l.getId()).size());

            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    // ════════════════════════════════════════════
    //  GET /api/transfer/my-listings — my team's listings
    // ════════════════════════════════════════════
    @GetMapping("/my-listings")
    public ResponseEntity<?> getMyListings(@AuthenticationPrincipal UserPrincipal principal) {
        Team team = getTeam(principal);
        List<TransferListing> listings = listingRepository.findBySellerTeamIdOrderByListedAtDesc(team.getId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (TransferListing l : listings) {
            Map<String, Object> m = buildListingMap(l);
            m.put("currentBid", l.getCurrentBid());
            m.put("currentBidderTeam", l.getCurrentBidderTeam() != null ? l.getCurrentBidderTeam().getTeamName() : null);
            m.put("auctionEndsAt", l.getAuctionEndsAt() != null ? l.getAuctionEndsAt().toString() : null);

            // Can cancel: ACTIVE, no bids, within 5 minutes of listing
            if ("ACTIVE".equals(l.getStatus())) {
                boolean noBids = bidRepository.findByListingIdOrderByBidAmountDesc(l.getId()).isEmpty();
                long mins = java.time.Duration.between(l.getListedAt(), LocalDateTime.now(ZoneOffset.UTC)).toMinutes();
                m.put("canCancel", noBids && mins < 5);
            } else {
                m.put("canCancel", false);
            }

            // Bids for this listing
            List<TransferBid> bids = bidRepository.findByListingIdOrderByBidAmountDesc(l.getId());
            List<Map<String, Object>> bidList = new ArrayList<>();
            for (TransferBid b : bids) {
                Map<String, Object> bm = new LinkedHashMap<>();
                bm.put("bidId", b.getId());
                bm.put("bidderTeam", b.getBidderTeam().getTeamName());
                bm.put("bidderTeamId", b.getBidderTeam().getId());
                bm.put("bidAmount", b.getBidAmount());
                bm.put("bidAt", b.getBidAt() != null ? b.getBidAt().toString() : null);
                bidList.add(bm);
            }
            m.put("bids", bidList);
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    // ════════════════════════════════════════════
    //  POST /api/transfer/bid — place a bid (5% or +1000 min increment, anti-snipe)
    // ════════════════════════════════════════════
    @PostMapping("/bid")
    @Transactional
    public ResponseEntity<?> placeBid(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body) {

        Team team = getTeam(principal);
        UUID listingId = UUID.fromString((String) body.get("listingId"));
        long bidAmount = ((Number) body.get("bidAmount")).longValue();

        // Pessimistic lock — serialises concurrent bids on the same listing
        TransferListing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Listing not found"));

        if (!"ACTIVE".equals(listing.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Listing is no longer active"));
        }
        if (listing.getSellerTeam().getId().equals(team.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot bid on your own listing"));
        }

        // Check if bidding team and selling team have the same owner (multi-team restriction)
        if (listing.getSellerTeam().getOwner() != null && team.getOwner() != null 
                && listing.getSellerTeam().getOwner().getId().equals(team.getOwner().getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot bid on players from your other team"));
        }

        // Check auction hasn't expired
        if (listing.getAuctionEndsAt() != null && LocalDateTime.now(ZoneOffset.UTC).isAfter(listing.getAuctionEndsAt())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Auction has ended"));
        }

        // Minimum bid: max(currentBid * 1.05, currentBid + 1000)
        long currentBid = listing.getCurrentBid() != null ? listing.getCurrentBid() : MIN_STARTING_BID;
        boolean isFirstBid = listing.getCurrentBidderTeam() == null;
        long minBid;
        if (isFirstBid) {
            minBid = currentBid; // First bid just needs to meet the starting bid
        } else {
            minBid = Math.max((long) Math.ceil(currentBid * (1.0 + BID_INCREMENT_PCT)), currentBid + MIN_BID_INCREMENT);
        }

        if (bidAmount < minBid) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Bid must be at least $" + String.format("%,d", minBid)
                    + " (5% above current bid or +$1,000, whichever is higher)"));
        }

        // Check buyer funds
        if (team.getFunds() < bidAmount) {
            return ResponseEntity.badRequest().body(Map.of("error", "Insufficient funds"));
        }

        // Save bid record
        TransferBid bid = TransferBid.builder()
                .listing(listing)
                .bidderTeam(team)
                .bidAmount(bidAmount)
                .build();
        bidRepository.save(bid);

        // Update listing with current highest
        listing.setCurrentBid(bidAmount);
        listing.setCurrentBidderTeam(team);

        // Anti-snipe: if less than 2 minutes remain, reset deadline TO 2 minutes from now
        if (listing.getAuctionEndsAt() != null) {
            long secondsRemaining = java.time.Duration.between(LocalDateTime.now(ZoneOffset.UTC), listing.getAuctionEndsAt()).getSeconds();
            if (secondsRemaining < ANTI_SNIPE_SECONDS) {
                listing.setAuctionEndsAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(ANTI_SNIPE_SECONDS));
            }
        }
        listingRepository.save(listing);

        // Broadcast to all connected clients
        long newMinBid = Math.max(
                (long) Math.ceil(bidAmount * (1.0 + BID_INCREMENT_PCT)),
                bidAmount + MIN_BID_INCREMENT);
        Map<String, Object> wsUpdate = new LinkedHashMap<>();
        wsUpdate.put("type", "BID_UPDATE");
        wsUpdate.put("listingId", listing.getId());
        wsUpdate.put("currentBid", bidAmount);
        wsUpdate.put("currentBidderTeam", team.getTeamName());
        wsUpdate.put("currentBidderTeamId", team.getId());
        wsUpdate.put("auctionEndsAt", listing.getAuctionEndsAt() != null ? listing.getAuctionEndsAt().toString() : null);
        wsUpdate.put("minNextBid", newMinBid);
        wsUpdate.put("bidCount", bidRepository.findByListingIdOrderByBidAmountDesc(listing.getId()).size());
        messagingTemplate.convertAndSend("/topic/transfer", wsUpdate);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", "Bid of $" + String.format("%,d", bidAmount) + " placed");
        resp.put("bidId", bid.getId());
        resp.put("myTeamId", team.getId());
        resp.put("auctionEndsAt", listing.getAuctionEndsAt() != null ? listing.getAuctionEndsAt().toString() : null);
        return ResponseEntity.ok(resp);
    }

    // ════════════════════════════════════════════
    //  POST /api/transfer/cancel/{listingId} — cancel listing (lose fee)
    // ════════════════════════════════════════════
    @PostMapping("/cancel/{listingId}")
    @Transactional
    public ResponseEntity<?> cancelListing(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID listingId) {

        Team team = getTeam(principal);

        TransferListing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Listing not found"));

        if (!listing.getSellerTeam().getId().equals(team.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not your listing"));
        }
        if (!"ACTIVE".equals(listing.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Listing is no longer active"));
        }

        // Only allow cancel within 5 minutes of listing AND if no bids placed
        boolean hasBids = !bidRepository.findByListingIdOrderByBidAmountDesc(listing.getId()).isEmpty();
        if (hasBids) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot cancel — bids have been placed on this listing"));
        }
        long minutesSinceListed = java.time.Duration.between(listing.getListedAt(), LocalDateTime.now(ZoneOffset.UTC)).toMinutes();
        if (minutesSinceListed >= 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot cancel — listing can only be cancelled within 5 minutes"));
        }

        listing.setStatus("CANCELLED");
        listingRepository.save(listing);

        return ResponseEntity.ok(Map.of("message", "Listing cancelled. Listing fee of $"
                + String.format("%,d", listing.getListingFee()) + " is forfeited."));
    }

    // ════════════════════════════════════════════
    //  POST /api/player/{id}/fire — remove player from team
    // ════════════════════════════════════════════
    @PostMapping("/fire/{playerId}")
    @Transactional
    public ResponseEntity<?> firePlayer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID playerId) {

        Team team = getTeam(principal);
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));

        if (player.getTeam() == null || !player.getTeam().getId().equals(team.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not your player"));
        }

        // Cancel any active listing
        listingRepository.findByPlayerIdAndStatus(playerId, "ACTIVE")
                .ifPresent(l -> { l.setStatus("CANCELLED"); listingRepository.save(l); });

        // Release any focused/general training assignment for this player from the team
        trainingAssignmentRepository.deleteByTeamIdAndPlayerId(team.getId(), player.getId());

        player.setTeam(null);
        playerRepository.save(player);

        activityLogService.log(team, "released", "Released " + player.getFirstName() + " " + player.getLastName() + " from the squad.");

        return ResponseEntity.ok(Map.of("message", player.getFirstName() + " " + player.getLastName() + " has been released."));
    }

    // ════════════════════════════════════════════
    //  POST /api/transfer/retire/{playerId} — retire player
    // ════════════════════════════════════════════
    @PostMapping("/retire/{playerId}")
    @Transactional
    public ResponseEntity<?> retirePlayer(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID playerId) {

        Team team = getTeam(principal);
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));

        if (player.getTeam() == null || !player.getTeam().getId().equals(team.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not your player"));
        }

        // Cancel any active listing
        listingRepository.findByPlayerIdAndStatus(playerId, "ACTIVE")
                .ifPresent(l -> { l.setStatus("CANCELLED"); listingRepository.save(l); });

        // Release any focused/general training assignment for this player from the team
        trainingAssignmentRepository.deleteByTeamIdAndPlayerId(team.getId(), player.getId());

        player.setTeam(null);
        playerRepository.save(player);

        activityLogService.log(team, "retired", player.getFirstName() + " " + player.getLastName() + " has retired.");

        return ResponseEntity.ok(Map.of("message", player.getFirstName() + " " + player.getLastName() + " has retired."));
    }

    // ════════════════════════════════════════════
    //  GET /api/transfer/player-status/{playerId}
    //  Check if player is listed / can be bid on
    // ════════════════════════════════════════════
    @GetMapping("/player-status/{playerId}")
    public ResponseEntity<?> getPlayerTransferStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID playerId) {

        Team myTeam = getTeam(principal);

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found"));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("isOwnPlayer", player.getTeam() != null && player.getTeam().getId().equals(myTeam.getId()));
        resp.put("marketValue", calculateMarketValue(player));
        resp.put("myFunds", myTeam.getFunds());

        Optional<TransferListing> activeListing = listingRepository.findByPlayerIdAndStatus(playerId, "ACTIVE");
        if (activeListing.isPresent()) {
            TransferListing l = activeListing.get();
            resp.put("isListed", true);
            resp.put("listingId", l.getId());
            resp.put("listingFee", l.getListingFee());
            resp.put("tmTax", l.getTmTax());
            resp.put("currentBid", l.getCurrentBid());
            resp.put("currentBidderTeam", l.getCurrentBidderTeam() != null ? l.getCurrentBidderTeam().getTeamName() : null);
            resp.put("auctionEndsAt", l.getAuctionEndsAt() != null ? l.getAuctionEndsAt().toString() : null);

            long current = l.getCurrentBid() != null ? l.getCurrentBid() : MIN_STARTING_BID;
            boolean isFirstBid = l.getCurrentBidderTeam() == null;
            long minNextBid = isFirstBid ? current : Math.max((long) Math.ceil(current * (1.0 + BID_INCREMENT_PCT)), current + MIN_BID_INCREMENT);
            resp.put("minNextBid", minNextBid);

            Optional<TransferBid> topBid = bidRepository.findFirstByListingIdOrderByBidAmountDesc(l.getId());
            resp.put("highestBid", topBid.map(TransferBid::getBidAmount).orElse(null));
            resp.put("bidCount", bidRepository.findByListingIdOrderByBidAmountDesc(l.getId()).size());

            // Get bids if own player
            if (player.getTeam() != null && player.getTeam().getId().equals(myTeam.getId())) {
                List<TransferBid> bids = bidRepository.findByListingIdOrderByBidAmountDesc(l.getId());
                boolean noBids = bids.isEmpty();
                long mins = java.time.Duration.between(l.getListedAt(), LocalDateTime.now(ZoneOffset.UTC)).toMinutes();
                resp.put("canCancel", noBids && mins < 5);
                List<Map<String, Object>> bidList = new ArrayList<>();
                for (TransferBid b : bids) {
                    Map<String, Object> bm = new LinkedHashMap<>();
                    bm.put("bidId", b.getId());
                    bm.put("bidderTeam", b.getBidderTeam().getTeamName());
                    bm.put("bidAmount", b.getBidAmount());
                    bm.put("bidAt", b.getBidAt() != null ? b.getBidAt().toString() : null);
                    bidList.add(bm);
                }
                resp.put("bids", bidList);
            }
        } else {
            resp.put("isListed", false);
        }

        return ResponseEntity.ok(resp);
    }

    // ════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════

    private Team getTeam(UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // Use active team if set, otherwise fall back to finding first team
        if (user.getActiveTeamId() != null) {
            return teamRepository.findById(user.getActiveTeamId())
                    .orElseThrow(() -> new IllegalArgumentException("Active team not found"));
        }
        
        return teamRepository.findByOwnerOrderByTeamOrderAsc(user)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No team found"));
    }

    private long calculateMarketValue(Player player) {
        int rating = player.getRating();
        int age = player.getAge();
        double ageFactor;
        if (age <= 19) ageFactor = 1.5;
        else if (age <= 24) ageFactor = 1.3;
        else if (age <= 29) ageFactor = 1.0;
        else if (age <= 33) ageFactor = 0.7;
        else ageFactor = 0.4;
        return Math.max((long) (rating * 1500 * ageFactor), 5000);
    }

    private Map<String, Object> buildListingMap(TransferListing l) {
        Player p = l.getPlayer();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("listingId", l.getId());
        m.put("status", l.getStatus());
        m.put("marketValue", l.getMarketValue());
        m.put("listingFee", l.getListingFee());
        m.put("tmTax", l.getTmTax());
        m.put("listedAt", l.getListedAt() != null ? l.getListedAt().toString() : null);
        m.put("salePrice", l.getSalePrice());
        m.put("soldAt", l.getSoldAt() != null ? l.getSoldAt().toString() : null);
        m.put("sellerTeam", l.getSellerTeam().getTeamName());
        m.put("sellerTeamId", l.getSellerTeam().getId());

        if (l.getBuyerTeam() != null) {
            m.put("buyerTeam", l.getBuyerTeam().getTeamName());
            m.put("buyerTeamId", l.getBuyerTeam().getId());
        }

        Map<String, Object> pm = new LinkedHashMap<>();
        pm.put("id", p.getId());
        pm.put("name", p.getFirstName() + " " + p.getLastName());
        pm.put("firstName", p.getFirstName());
        pm.put("lastName", p.getLastName());
        pm.put("role", p.getRole());
        pm.put("age", p.getAge());
        pm.put("ageDays", p.getAgeDays());
        pm.put("country", p.getCountry());
        pm.put("rating", p.getRating());
        pm.put("batRating", p.getBatRating());
        pm.put("bowlRating", p.getBowlRating());
        pm.put("keeperRating", p.getKeeperRating());
        pm.put("fldRating", p.getFldRating());
        pm.put("stamina", p.getStamina());
        pm.put("fitness", p.getFitness());
        pm.put("experience", p.getExperience());
        pm.put("confidence", p.getConfidence());
        pm.put("batHand", p.getBatHand());
        pm.put("bowlType", p.getBowlType());
        m.put("player", pm);

        return m;
    }

    // ════════════════════════════════════════════
    //  GET /api/transfer/recent-sales — last 20 completed sales
    // ════════════════════════════════════════════
    @GetMapping("/recent-sales")
    public ResponseEntity<?> getRecentSales() {
        List<TransferListing> sales = listingRepository.findTop20ByStatusOrderBySoldAtDesc("SOLD");
        List<Map<String, Object>> result = new ArrayList<>();
        for (TransferListing l : sales) {
            Player p = l.getPlayer();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("playerId", p.getId());
            m.put("playerName", p.getFirstName() + " " + p.getLastName());
            m.put("sellerTeamId", l.getSellerTeam().getId());
            m.put("soldFrom", l.getSellerTeam().getTeamName());
            m.put("buyerTeamId", l.getBuyerTeam() != null ? l.getBuyerTeam().getId() : null);
            m.put("soldTo", l.getBuyerTeam() != null ? l.getBuyerTeam().getTeamName() : null);
            m.put("initialPrice", l.getListingFee() * 5);
            m.put("finalPrice", l.getSalePrice());
            m.put("soldAt", l.getSoldAt() != null ? l.getSoldAt().toString() : null);
            result.add(m);
        }
        return ResponseEntity.ok(result);
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
