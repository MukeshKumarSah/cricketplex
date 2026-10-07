CREATE TABLE trophies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES teams(id),
    format VARCHAR(10) NOT NULL,
    country VARCHAR(60) NOT NULL,
    division INTEGER NOT NULL,
    league_number INTEGER NOT NULL,
    season INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_trophies_team ON trophies(team_id);
