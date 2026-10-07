CREATE TABLE IF NOT EXISTS sim_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100)  NOT NULL,
    format          VARCHAR(10)   NOT NULL,
    num_matches     INT           NOT NULL,
    config_json     TEXT          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    result_json     TEXT,
    error_message   VARCHAR(1000),
    created_at      TIMESTAMP     DEFAULT now()
);

ALTER TABLE fixtures ADD COLUMN IF NOT EXISTS sim_session_id UUID REFERENCES sim_sessions(id);
ALTER TABLE teams    ADD COLUMN IF NOT EXISTS sim_session_id UUID REFERENCES sim_sessions(id);
ALTER TABLE players  ADD COLUMN IF NOT EXISTS sim_session_id UUID REFERENCES sim_sessions(id);

CREATE INDEX IF NOT EXISTS idx_fixtures_sim ON fixtures(sim_session_id) WHERE sim_session_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_teams_sim    ON teams(sim_session_id)    WHERE sim_session_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_players_sim  ON players(sim_session_id)  WHERE sim_session_id IS NOT NULL;
