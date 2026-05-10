-- Add format column to leagues (T20, ODI, FC)
ALTER TABLE leagues ADD COLUMN format VARCHAR(10) NOT NULL DEFAULT 'T20';

-- Drop old unique index FIRST (it doesn't include format)
DROP INDEX idx_league_unique;

-- Create new unique index including format
CREATE UNIQUE INDEX idx_league_unique ON leagues(LOWER(country), format, division, league_number);

-- Set existing leagues to T20
UPDATE leagues SET format = 'T20';

-- Create ODI copies of all existing leagues
INSERT INTO leagues (country, division, league_number, season, format)
SELECT country, division, league_number, season, 'ODI' FROM leagues WHERE format = 'T20';

-- Create FC copies of all existing leagues
INSERT INTO leagues (country, division, league_number, season, format)
SELECT country, division, league_number, season, 'FC' FROM leagues WHERE format = 'T20';
