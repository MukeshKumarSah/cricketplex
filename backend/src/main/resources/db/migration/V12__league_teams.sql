-- Join table: each team is assigned to one league per format
-- A team can be in FC Div 1.1, T20 Div 2.1, ODI Div 2.2 independently
CREATE TABLE league_teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_id UUID NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- A team can only appear once per league
CREATE UNIQUE INDEX idx_league_team ON league_teams(league_id, team_id);

-- A team can only be in one league per format (one T20 league, one ODI league, one FC league)
-- We enforce this at application level since we need to join with leagues table for format

-- Remove division/league_number from teams (now tracked via league_teams)
ALTER TABLE teams DROP COLUMN IF EXISTS division;
ALTER TABLE teams DROP COLUMN IF EXISTS league_number;
