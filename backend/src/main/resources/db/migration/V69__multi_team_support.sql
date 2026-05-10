-- Migration: Add Multi-Team Support
-- Date: 2026-05-10
-- Description: Allow supporters to have multiple teams with restrictions

-- Step 1: Add activeTeamId to users table
ALTER TABLE users ADD COLUMN active_team_id UUID;
ALTER TABLE users ADD CONSTRAINT fk_users_active_team FOREIGN KEY (active_team_id) REFERENCES teams(id) ON DELETE SET NULL;

-- Step 2: Remove unique constraint from teams.owner_id (if it exists as a constraint)
-- Note: This depends on how the constraint was created. May need to drop and recreate index.
-- If owner_id has a unique index:
-- DROP INDEX IF EXISTS teams_owner_id_key;

-- Step 3: Add team_order column to teams table
ALTER TABLE teams ADD COLUMN team_order INTEGER DEFAULT 1;

-- Step 4: Update existing teams to have team_order = 1 (primary team)
UPDATE teams SET team_order = 1 WHERE team_order IS NULL;

-- Step 5: Set active_team_id for existing users to their current team
UPDATE users u
SET active_team_id = t.id
FROM teams t
WHERE t.owner_id = u.id
  AND u.active_team_id IS NULL;

-- Step 6: Add index for better query performance
CREATE INDEX idx_teams_owner_id ON teams(owner_id);
CREATE INDEX idx_teams_owner_team_order ON teams(owner_id, team_order);
CREATE INDEX idx_users_active_team_id ON users(active_team_id);

-- Step 7: Add comment for documentation
COMMENT ON COLUMN users.active_team_id IS 'Currently active team for multi-team users';
COMMENT ON COLUMN teams.team_order IS '1 = primary team, 2 = secondary team';
