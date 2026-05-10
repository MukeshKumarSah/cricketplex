-- Default lineup storage (JSONB to avoid extra child tables)
CREATE TABLE default_lineups (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id     UUID NOT NULL UNIQUE REFERENCES teams(id),
    lineup_data JSONB NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
