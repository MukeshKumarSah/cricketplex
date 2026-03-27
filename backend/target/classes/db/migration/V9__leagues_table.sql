-- Leagues table
CREATE TABLE leagues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country VARCHAR(100) NOT NULL,
    division INTEGER NOT NULL,
    league_number INTEGER NOT NULL,
    season INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_league_unique ON leagues(LOWER(country), division, league_number);

-- Auto-create Division 1 (1 league) and Division 2 (2 leagues) for all 18 countries
INSERT INTO leagues (country, division, league_number)
SELECT c.country, 1, 1
FROM (VALUES
    ('Afghanistan'), ('Australia'), ('Bangladesh'), ('England'), ('India'),
    ('Ireland'), ('Nepal'), ('Netherlands'), ('New Zealand'), ('Oman'),
    ('Pakistan'), ('Scotland'), ('South Africa'), ('Sri Lanka'),
    ('United Arab Emirates'), ('United States'), ('West Indies'), ('Zimbabwe')
) AS c(country);

INSERT INTO leagues (country, division, league_number)
SELECT c.country, 2, g.num
FROM (VALUES
    ('Afghanistan'), ('Australia'), ('Bangladesh'), ('England'), ('India'),
    ('Ireland'), ('Nepal'), ('Netherlands'), ('New Zealand'), ('Oman'),
    ('Pakistan'), ('Scotland'), ('South Africa'), ('Sri Lanka'),
    ('United Arab Emirates'), ('United States'), ('West Indies'), ('Zimbabwe')
) AS c(country)
CROSS JOIN (VALUES (1), (2)) AS g(num);
