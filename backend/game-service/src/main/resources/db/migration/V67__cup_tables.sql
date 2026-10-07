-- Cup knockout tournament tables

CREATE TABLE cups (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season      INTEGER     NOT NULL UNIQUE,
    format      VARCHAR(10) NOT NULL,           -- 'T20' or 'ODI'
    status      VARCHAR(20) NOT NULL DEFAULT 'UPCOMING', -- UPCOMING, ONGOING, COMPLETED
    bracket_size  INTEGER   NOT NULL,           -- 256 | 512 | 1024 | 2048 | 4096
    total_rounds  INTEGER   NOT NULL,
    current_round INTEGER   NOT NULL DEFAULT 0, -- 0 = not started
    start_date  DATE,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE cup_teams (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cup_id            UUID    NOT NULL REFERENCES cups(id) ON DELETE CASCADE,
    team_id           UUID    NOT NULL REFERENCES teams(id),
    seed_rank         INTEGER NOT NULL,         -- 1 = top seed
    bracket_position  INTEGER NOT NULL,         -- 1 .. bracket_size (never changes)
    eliminated_round  INTEGER,                  -- NULL = still in / champion; set when knocked out
    prize_won         BIGINT  NOT NULL DEFAULT 0,
    created_at        TIMESTAMP DEFAULT NOW(),
    UNIQUE (cup_id, team_id),
    UNIQUE (cup_id, bracket_position)
);

-- Link cup fixtures to their cup (NULL for league / friendly fixtures)
ALTER TABLE fixtures ADD COLUMN cup_id UUID REFERENCES cups(id);

CREATE INDEX idx_cup_teams_cup  ON cup_teams (cup_id);
CREATE INDEX idx_fixtures_cup   ON fixtures  (cup_id);
