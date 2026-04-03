package com.cricketplex.controller;

import com.cricketplex.entity.*;
import com.cricketplex.repository.*;
import com.cricketplex.security.UserPrincipal;
import com.cricketplex.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class TransferMarketController {

    private static final long MIN_TM_TAX = 5000L;

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final TransferListingRepository listingRepository;
    private final TransferBidRepository bidRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final ActivityLogService activityLogService;

    // ════════════════════════════════════════════
    //  POST /api/transfer/list — list player on TM
    // ════════════════════════════════════════════
    @PostMapping("/list")
    @Transactional
    public ResponseEntity<?> listPlayer(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {

        Team team = getTeam(principal);
        UUID playerId = UUID.fromString(body.get("playerId"));

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
        long listingFee = (long) (marketValue * 0.20);
        long tmTax = Math.max((long) (marketValue * 0.05), MIN_TM_TAX);

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
                .build();
        listingRepository.save(listing);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("listingId", listing.getId());
        resp.put("marketValue", marketValue);
        resp.put("listingFee", listingFee);
        resp.put("tmTax", tmTax);
        resp.put("message", "Player listed. Listing fee of $" + String.format("%,d", listingFee) + " deducted.");

        activityLogService.log(team, "listed", "Listed " + player.getFirstName() + " " + player.getLastName() + " on Transfer Market (value $" + String.format("%,d", marketValue) + ").");

        return ResponseEntity.ok(resp);
    }

    // ════════════════════════════════════════════
    //  GET /api/transfer/listings — all active listings
    // ════════════════════════════════════════════
    @GetMapping("/listings")
    public ResponseEntity<?> getActiveListings(@AuthenticationPrincipal UserPrincipal principal) {
        Team myTeam = getTeam(principal);
        List<TransferListing> listings = listingRepository.findByStatusOrderByListedAtDesc("ACTIVE");

        List<Map<String, Object>> result = new ArrayList<>();
        for (TransferListing l : listings) {
            Map<String, Object> m = buildListingMap(l);
            m.put("isOwn", l.getSellerTeam().getId().equals(myTeam.getId()));

            // Get highest bid
            Optional<TransferBid> topBid = bidRepository.findFirstByListingIdOrderByBidAmountDesc(l.getId());
            m.put("highestBid", topBid.map(TransferBid::getBidAmount).orElse(null));
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
    //  POST /api/transfer/bid — place a bid
    // ════════════════════════════════════════════
    @PostMapping("/bid")
    @Transactional
    public ResponseEntity<?> placeBid(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body) {

        Team team = getTeam(principal);
        UUID listingId = UUID.fromString((String) body.get("listingId"));
        long bidAmount = ((Number) body.get("bidAmount")).longValue();

        TransferListing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Listing not found"));

        if (!"ACTIVE".equals(listing.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Listing is no longer active"));
        }
        if (listing.getSellerTeam().getId().equals(team.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot bid on your own listing"));
        }
        if (bidAmount < listing.getMarketValue()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Bid must be at least the market value ($" + String.format("%,d", listing.getMarketValue()) + ")"));
        }

        // Must be higher than current highest bid
        Optional<TransferBid> topBid = bidRepository.findFirstByListingIdOrderByBidAmountDesc(listing.getId());
        if (topBid.isPresent() && bidAmount <= topBid.get().getBidAmount()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Bid must be higher than current highest ($" + String.format("%,d", topBid.get().getBidAmount()) + ")"));
        }

        // Check buyer funds
        if (team.getFunds() < bidAmount) {
            return ResponseEntity.badRequest().body(Map.of("error", "Insufficient funds"));
        }

        TransferBid bid = TransferBid.builder()
                .listing(listing)
                .bidderTeam(team)
                .bidAmount(bidAmount)
                .build();
        bidRepository.save(bid);

        return ResponseEntity.ok(Map.of(
                "message", "Bid of $" + String.format("%,d", bidAmount) + " placed",
                "bidId", bid.getId()
        ));
    }

    // ════════════════════════════════════════════
    //  POST /api/transfer/accept/{listingId} — accept highest bid
    // ════════════════════════════════════════════
    @PostMapping("/accept/{listingId}")
    @Transactional
    public ResponseEntity<?> acceptHighestBid(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID listingId) {

        Team sellerTeam = getTeam(principal);

        TransferListing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Listing not found"));

        if (!listing.getSellerTeam().getId().equals(sellerTeam.getId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not your listing"));
        }
        if (!"ACTIVE".equals(listing.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Listing is no longer active"));
        }

        Optional<TransferBid> topBidOpt = bidRepository.findFirstByListingIdOrderByBidAmountDesc(listing.getId());
        if (topBidOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No bids to accept"));
        }

        TransferBid topBid = topBidOpt.get();
        Team buyerTeam = topBid.getBidderTeam();
        long salePrice = topBid.getBidAmount();

        // Check buyer still has funds
        if (buyerTeam.getFunds() < salePrice) {
            return ResponseEntity.badRequest().body(Map.of("error", "Buyer no longer has sufficient funds"));
        }

        // Deduct from buyer
        buyerTeam.setFunds(buyerTeam.getFunds() - salePrice);

        // Seller receives sale price
        sellerTeam.setFunds(sellerTeam.getFunds() + salePrice);

        // TM tax settlement: listing fee (20% of listing price) was already paid
        // Total tax owed = 5% of sold price
        // If listingFee > totalTax: refund difference to seller
        // If listingFee < totalTax: charge difference from seller
        long listingFee = listing.getListingFee();
        long totalTax = Math.max((long) (salePrice * 0.05), MIN_TM_TAX);
        long settlement = listingFee - totalTax;

        if (settlement > 0) {
            // Refund excess to seller
            sellerTeam.setFunds(sellerTeam.getFunds() + settlement);
        } else if (settlement < 0) {
            // Charge additional to seller
            sellerTeam.setFunds(sellerTeam.getFunds() + settlement); // settlement is negative
        }

        // Transfer player
        Player player = listing.getPlayer();
        player.setTeam(buyerTeam);
        playerRepository.save(player);

        // Update listing
        listing.setStatus("SOLD");
        listing.setBuyerTeam(buyerTeam);
        listing.setSalePrice(salePrice);
        listing.setSoldAt(LocalDateTime.now());
        listingRepository.save(listing);

        teamRepository.save(sellerTeam);
        teamRepository.save(buyerTeam);

        String playerName = player.getFirstName() + " " + player.getLastName();
        logTransaction(buyerTeam, "TM_PURCHASE", "Purchased " + playerName + " from " + sellerTeam.getTeamName(), -salePrice);
        logTransaction(sellerTeam, "TM_SALE", "Sold " + playerName + " to " + buyerTeam.getTeamName(), salePrice);
        if (settlement != 0) {
            logTransaction(sellerTeam, "TM_TAX_SETTLE", "TM tax settlement for " + playerName, settlement);
        }

        activityLogService.log(buyerTeam, "bought", "Bought " + playerName + " from " + sellerTeam.getTeamName() + " for $" + String.format("%,d", salePrice) + ".");
        activityLogService.log(sellerTeam, "sold", "Sold " + playerName + " to " + buyerTeam.getTeamName() + " for $" + String.format("%,d", salePrice) + ".");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("message", "Player sold to " + buyerTeam.getTeamName() + " for $" + String.format("%,d", salePrice));
        resp.put("salePrice", salePrice);
        resp.put("totalTax", totalTax);
        resp.put("listingFee", listingFee);
        resp.put("settlement", settlement);
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

            Optional<TransferBid> topBid = bidRepository.findFirstByListingIdOrderByBidAmountDesc(l.getId());
            resp.put("highestBid", topBid.map(TransferBid::getBidAmount).orElse(null));
            resp.put("bidCount", bidRepository.findByListingIdOrderByBidAmountDesc(l.getId()).size());

            // Get bids if own player
            if (player.getTeam() != null && player.getTeam().getId().equals(myTeam.getId())) {
                List<TransferBid> bids = bidRepository.findByListingIdOrderByBidAmountDesc(l.getId());
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
        return teamRepository.findByOwner(user)
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
        pm.put("role", p.getRole());
        pm.put("age", p.getAge());
        pm.put("country", p.getCountry());
        pm.put("rating", p.getRating());
        pm.put("batRating", p.getBatRating());
        pm.put("bowlRating", p.getBowlRating());
        pm.put("keeperRating", p.getKeeperRating());
        pm.put("fldRating", p.getFldRating());
        pm.put("stamina", p.getStamina());
        pm.put("confidence", p.getConfidence());
        m.put("player", pm);

        return m;
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
