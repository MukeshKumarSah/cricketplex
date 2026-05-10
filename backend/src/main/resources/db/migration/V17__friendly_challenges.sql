-- =============================================
-- V17: Friendly Challenge System
-- =============================================

-- Make fixtures table support friendly matches (no league)
ALTER TABLE fixtures ALTER COLUMN league_id DROP NOT NULL;
ALTER TABLE fixtures ALTER COLUMN round SET DEFAULT 0;
ALTER TABLE fixtures ALTER COLUMN match_number SET DEFAULT 0;
ALTER TABLE fixtures ADD COLUMN match_type VARCHAR(20) NOT NULL DEFAULT 'LEAGUE';
ALTER TABLE fixtures ADD COLUMN format VARCHAR(10);

-- Friendly challenges table
CREATE TABLE friendly_challenges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenger_team_id UUID NOT NULL REFERENCES teams(id),
    challenged_team_id UUID NOT NULL REFERENCES teams(id),
    fixture_id      UUID REFERENCES fixtures(id),
    format          VARCHAR(10) NOT NULL,
    pitch_type      VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    match_date      DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    message         TEXT,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_friendly_challenger ON friendly_challenges(challenger_team_id);
CREATE INDEX idx_friendly_challenged ON friendly_challenges(challenged_team_id);
CREATE INDEX idx_friendly_status ON friendly_challenges(status);
