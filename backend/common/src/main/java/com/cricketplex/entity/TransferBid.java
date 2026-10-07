package com.cricketplex.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transfer_bids")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferBid {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private TransferListing listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_team_id", nullable = false)
    private Team bidderTeam;

    @Column(nullable = false)
    private Long bidAmount;

    @CreationTimestamp
    private LocalDateTime bidAt;
}
