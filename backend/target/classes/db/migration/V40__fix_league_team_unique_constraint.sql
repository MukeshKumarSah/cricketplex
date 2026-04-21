-- The original unique index covered (league_id, team_id) with no season,
-- preventing the same team from ever appearing in the same league across seasons.
-- Replace with a season-scoped unique index so promotion/relegation can
-- re-enroll a team in the same league for a future season.
DROP INDEX IF EXISTS idx_league_team;
CREATE UNIQUE INDEX idx_league_team ON league_teams(league_id, team_id, season);
