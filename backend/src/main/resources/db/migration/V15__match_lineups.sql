-- Match lineup storage
CREATE TABLE match_lineups (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fixture_id     UUID NOT NULL REFERENCES fixtures(id) ON DELETE CASCADE,
    team_id        UUID NOT NULL REFERENCES teams(id),
    captain_id     UUID REFERENCES players(id),
    keeper_id      UUID REFERENCES players(id),
    toss_choice    VARCHAR(10),
    bat_or_bowl    VARCHAR(10),
    bowling_plan   VARCHAR(20) NOT NULL DEFAULT 'BALANCED',
    created_at     TIMESTAMP DEFAULT NOW(),
    updated_at     TIMESTAMP DEFAULT NOW(),
    UNIQUE(fixture_id, team_id)
);

-- Playing 11 with batting order and aggression
CREATE TABLE lineup_players (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lineup_id        UUID NOT NULL REFERENCES match_lineups(id) ON DELETE CASCADE,
    player_id        UUID NOT NULL REFERENCES players(id),
    batting_position INT NOT NULL,
    bat_aggression   VARCHAR(5) NOT NULL DEFAULT 'N'
);

CREATE INDEX idx_lineup_players_lineup ON lineup_players(lineup_id);

-- Over-by-over bowling assignment
CREATE TABLE bowling_orders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lineup_id   UUID NOT NULL REFERENCES match_lineups(id) ON DELETE CASCADE,
    over_number INT NOT NULL,
    bowler_id   UUID NOT NULL REFERENCES players(id),
    aggression  VARCHAR(5) NOT NULL DEFAULT 'N'
);

CREATE INDEX idx_bowling_orders_lineup ON bowling_orders(lineup_id);
