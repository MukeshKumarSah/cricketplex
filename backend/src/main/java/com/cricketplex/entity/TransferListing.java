package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transfer_listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_team_id", nullable = false)
    private Team sellerTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(nullable = false)
    private Long marketValue;

    @Column(nullable = false)
    private Long listingFee;

    @Column(nullable = false)
    private Long tmTax;

    @Column(nullable = false)
    @Builder.Default
    private String status = "ACTIVE";   // ACTIVE, SOLD, CANCELLED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_team_id")
    private Team buyerTeam;

    private Long salePrice;

    @CreationTimestamp
    private LocalDateTime listedAt;

    private LocalDateTime soldAt;

    @Column(name = "auction_ends_at")
    private LocalDateTime auctionEndsAt;

    @Column(name = "current_bid")
    private Long currentBid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_bidder_team_id")
    private Team currentBidderTeam;
}
