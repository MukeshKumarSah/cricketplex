-- Auction system: 48-hour open bidding with anti-snipe
ALTER TABLE transfer_listings ADD COLUMN auction_ends_at TIMESTAMP;
ALTER TABLE transfer_listings ADD COLUMN current_bid BIGINT;
ALTER TABLE transfer_listings ADD COLUMN current_bidder_team_id UUID REFERENCES teams(id);

-- Backfill existing ACTIVE listings with 48h from now (unlikely but safe)
UPDATE transfer_listings SET auction_ends_at = CURRENT_TIMESTAMP + INTERVAL '48 hours' WHERE status = 'ACTIVE' AND auction_ends_at IS NULL;
