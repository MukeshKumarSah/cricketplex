-- Team funds
ALTER TABLE teams ADD COLUMN IF NOT EXISTS funds BIGINT NOT NULL DEFAULT 1000000;

-- Allow players to have no team (fired/retired)
ALTER TABLE players ALTER COLUMN team_id DROP NOT NULL;

-- Transfer listings
CREATE TABLE transfer_listings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_team_id UUID NOT NULL REFERENCES teams(id),
    player_id UUID NOT NULL REFERENCES players(id),
    market_value BIGINT NOT NULL,
    listing_fee BIGINT NOT NULL,
    tm_tax BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    buyer_team_id UUID REFERENCES teams(id),
    sale_price BIGINT,
    listed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sold_at TIMESTAMP
);

-- Transfer bids
CREATE TABLE transfer_bids (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL REFERENCES transfer_listings(id),
    bidder_team_id UUID NOT NULL REFERENCES teams(id),
    bid_amount BIGINT NOT NULL,
    bid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
