-- Academy level on teams
ALTER TABLE teams ADD COLUMN IF NOT EXISTS academy_level INTEGER NOT NULL DEFAULT 1;

-- Player pulls
CREATE TABLE academy_pulls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES teams(id),
    player_id UUID NOT NULL REFERENCES players(id),
    requested_role VARCHAR(255) NOT NULL,
    pulled_from_country VARCHAR(255) NOT NULL,
    pulled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Focused training assignments
CREATE TABLE training_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES teams(id),
    player_id UUID NOT NULL REFERENCES players(id),
    training_type VARCHAR(255) NOT NULL
);

-- Training history log
CREATE TABLE training_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES teams(id),
    player_id UUID NOT NULL REFERENCES players(id),
    training_type VARCHAR(255) NOT NULL,
    skill VARCHAR(255) NOT NULL,
    old_value INTEGER NOT NULL,
    new_value INTEGER NOT NULL,
    change INTEGER NOT NULL,
    trained_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
