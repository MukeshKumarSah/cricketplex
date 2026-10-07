-- Make default lineups per-format (T20 / ODI / FC) instead of one per team
ALTER TABLE default_lineups DROP CONSTRAINT IF EXISTS default_lineups_team_id_key;
ALTER TABLE default_lineups ADD COLUMN format VARCHAR(10) NOT NULL DEFAULT 'T20';
ALTER TABLE default_lineups ADD CONSTRAINT uq_default_lineup_team_format UNIQUE (team_id, format);
