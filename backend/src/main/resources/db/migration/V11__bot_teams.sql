-- Bot team support: add league assignment + bot flag to teams
ALTER TABLE teams ADD COLUMN is_bot BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE teams ADD COLUMN division INTEGER;
ALTER TABLE teams ADD COLUMN league_number INTEGER;

-- Make owner_id nullable for bot teams
ALTER TABLE teams ALTER COLUMN owner_id DROP NOT NULL;
