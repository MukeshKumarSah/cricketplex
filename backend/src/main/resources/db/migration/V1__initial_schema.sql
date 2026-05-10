-- =============================================
-- V1: Initial schema — users & teams
-- =============================================

-- Users table
CREATE TABLE users (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255)    NOT NULL,
    username        VARCHAR(30)     NOT NULL UNIQUE,
    email           VARCHAR(255)    NOT NULL UNIQUE,
    password        VARCHAR(255)    NOT NULL,
    role            VARCHAR(20)     NOT NULL DEFAULT 'USER',
    is_supporter    BOOLEAN         NOT NULL DEFAULT FALSE,
    is_sub_admin    BOOLEAN         NOT NULL DEFAULT FALSE,
    accepted_terms  BOOLEAN         NOT NULL DEFAULT FALSE,
    profile_pic_url VARCHAR(512),
    team_setup_done BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email    ON users (email);

-- Teams table
CREATE TABLE teams (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    team_name   VARCHAR(255)    NOT NULL,
    country     VARCHAR(100)    NOT NULL,
    owner_id    UUID            NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE INDEX idx_teams_owner_id  ON teams (owner_id);
CREATE INDEX idx_teams_team_name ON teams (team_name);
