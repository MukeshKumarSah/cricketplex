-- ═══════════════════════════════════════════════════════════════
--  Friendly Tournaments — League (4-8 teams) & Knockout (8+)
--  Created by supporters / admins. Stats count, no XP/fitness.
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE friendly_tournaments (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR(100) NOT NULL,
    type                  VARCHAR(20)  NOT NULL CHECK (type IN ('LEAGUE', 'KNOCKOUT')),
    format                VARCHAR(10)  NOT NULL CHECK (format IN ('T20', 'ODI')),
    created_by            UUID         NOT NULL REFERENCES users(id),
    status                VARCHAR(20)  NOT NULL DEFAULT 'REGISTRATION'
                              CHECK (status IN ('REGISTRATION', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    is_public             BOOLEAN      NOT NULL DEFAULT true,
    join_code             VARCHAR(12)  UNIQUE,
    registration_deadline TIMESTAMP,
    start_date            DATE,
    -- How matches are spread across the week
    schedule_type         VARCHAR(20)  NOT NULL DEFAULT 'WEEKLY'
                              CHECK (schedule_type IN ('WEEKLY', 'BIWEEKLY', 'TRIWEEKLY')),
    -- Comma-separated day names e.g. "SATURDAY" / "MONDAY,THURSDAY" / "MON,WED,SAT"
    schedule_days         VARCHAR(100),
    created_at            TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_ft_created_by   ON friendly_tournaments(created_by);
CREATE INDEX idx_ft_status       ON friendly_tournaments(status);
CREATE INDEX idx_ft_join_code    ON friendly_tournaments(join_code);

-- ── Invited / accepted teams ──────────────────────────────────
CREATE TABLE friendly_tournament_teams (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id   UUID        NOT NULL REFERENCES friendly_tournaments(id) ON DELETE CASCADE,
    team_id         UUID        NOT NULL REFERENCES teams(id),
    invite_status   VARCHAR(20) NOT NULL DEFAULT 'INVITED'
                        CHECK (invite_status IN ('INVITED', 'ACCEPTED', 'DECLINED')),
    invited_at      TIMESTAMP   NOT NULL DEFAULT now(),
    responded_at    TIMESTAMP,
    UNIQUE (tournament_id, team_id)
);

CREATE INDEX idx_ftt_tournament ON friendly_tournament_teams(tournament_id);
CREATE INDEX idx_ftt_team       ON friendly_tournament_teams(team_id);

-- ── League standings (recalculated after each match) ──────────
CREATE TABLE friendly_tournament_standings (
    id                  UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    tournament_id       UUID             NOT NULL REFERENCES friendly_tournaments(id) ON DELETE CASCADE,
    team_id             UUID             NOT NULL REFERENCES teams(id),
    played              INT              NOT NULL DEFAULT 0,
    won                 INT              NOT NULL DEFAULT 0,
    drawn               INT              NOT NULL DEFAULT 0,
    lost                INT              NOT NULL DEFAULT 0,
    points              INT              NOT NULL DEFAULT 0,
    runs_scored_total   INT              NOT NULL DEFAULT 0,
    runs_conceded_total INT              NOT NULL DEFAULT 0,
    overs_faced_total   DOUBLE PRECISION NOT NULL DEFAULT 0,
    overs_bowled_total  DOUBLE PRECISION NOT NULL DEFAULT 0,
    nrr                 DOUBLE PRECISION NOT NULL DEFAULT 0,
    UNIQUE (tournament_id, team_id)
);

CREATE INDEX idx_fts_tournament ON friendly_tournament_standings(tournament_id);

-- ── Hook tournament fixtures into existing fixtures table ─────
ALTER TABLE fixtures
    ADD COLUMN IF NOT EXISTS friendly_tournament_id UUID REFERENCES friendly_tournaments(id),
    -- 1 = first leg, 2 = second leg (league double round-robin)
    ADD COLUMN IF NOT EXISTS tournament_leg INT,
    -- "Preliminary", "Quarter Final", "Semi Final", "Final" (knockout)
    ADD COLUMN IF NOT EXISTS tournament_round_name VARCHAR(30),
    -- Slot number within the round (used for bracket advancement)
    ADD COLUMN IF NOT EXISTS tournament_slot INT;

CREATE INDEX idx_fixtures_friendly_tournament ON fixtures(friendly_tournament_id)
    WHERE friendly_tournament_id IS NOT NULL;
