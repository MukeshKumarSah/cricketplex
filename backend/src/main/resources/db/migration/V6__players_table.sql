-- V6: Name pool tables + generated players table

-- Pool of first names per country (admin-managed)
CREATE TABLE player_first_names (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    country     VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX idx_pfn_name_country ON player_first_names(LOWER(name), LOWER(country));

-- Pool of last names per country (admin-managed)
CREATE TABLE player_last_names (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    country     VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX idx_pln_name_country ON player_last_names(LOWER(name), LOWER(country));

-- Generated players assigned to teams
CREATE TABLE players (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    country         VARCHAR(100) NOT NULL,
    team_id         UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    role            VARCHAR(15) NOT NULL,
    age             INT NOT NULL,
    bat_hand        VARCHAR(2) NOT NULL,
    bowl_hand       VARCHAR(2),
    bowl_type       VARCHAR(2),
    bat_rating      INT NOT NULL DEFAULT 0,
    bowl_rating     INT NOT NULL DEFAULT 0,
    keeper_rating   INT NOT NULL DEFAULT 0,
    fld_rating      INT NOT NULL DEFAULT 0,
    experience      INT NOT NULL DEFAULT 10,
    stamina         INT NOT NULL DEFAULT 15,
    fitness         INT NOT NULL DEFAULT 100,
    bat_aggression  VARCHAR(1) NOT NULL DEFAULT 'N',
    bowl_aggression VARCHAR(1) NOT NULL DEFAULT 'N',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_players_team_id ON players(team_id);
CREATE UNIQUE INDEX idx_players_unique_name ON players(LOWER(first_name), LOWER(last_name));
