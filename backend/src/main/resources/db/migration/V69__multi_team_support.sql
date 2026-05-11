-- Migration: Add Multi-Team Support
-- Date: 2026-05-10
-- Description: Allow supporters to have multiple teams with restrictions

-- Step 1: Drop the unique constraint on teams.owner_id if it exists
DO $$
BEGIN
    -- Drop unique constraint
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'teams_owner_id_key') THEN
        ALTER TABLE teams DROP CONSTRAINT teams_owner_id_key;
    END IF;
    
    -- Also check for unique index
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'teams_owner_id_key') THEN
        DROP INDEX teams_owner_id_key;
    END IF;
END $$;

-- Step 2: Add activeTeamId to users table (if not exists)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name='users' AND column_name='active_team_id') THEN
        ALTER TABLE users ADD COLUMN active_team_id UUID;
    END IF;
END $$;

-- Step 3: Add foreign key constraint (if not exists)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints 
                   WHERE constraint_name='fk_users_active_team' AND table_name='users') THEN
        ALTER TABLE users ADD CONSTRAINT fk_users_active_team 
            FOREIGN KEY (active_team_id) REFERENCES teams(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Step 4: Add team_order column to teams table (if not exists)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name='teams' AND column_name='team_order') THEN
        ALTER TABLE teams ADD COLUMN team_order INTEGER DEFAULT 1;
    END IF;
END $$;

-- Step 5: Update existing teams to have team_order = 1 (primary team)
UPDATE teams SET team_order = 1 WHERE team_order IS NULL;

-- Step 6: Set active_team_id for existing users to their current team
UPDATE users u
SET active_team_id = t.id
FROM teams t
WHERE t.owner_id = u.id
  AND u.active_team_id IS NULL;

-- Step 7: Add indexes for better query performance (if not exists)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_teams_owner_id') THEN
        CREATE INDEX idx_teams_owner_id ON teams(owner_id);
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_teams_owner_team_order') THEN
        CREATE INDEX idx_teams_owner_team_order ON teams(owner_id, team_order);
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_users_active_team_id') THEN
        CREATE INDEX idx_users_active_team_id ON users(active_team_id);
    END IF;
END $$;

