-- Manual fix for teams_owner_id_key unique constraint
-- Run this directly on your database if migration V69 failed

-- Drop the unique constraint on teams.owner_id
DO $$
BEGIN
    -- Drop unique constraint
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'teams_owner_id_key') THEN
        ALTER TABLE teams DROP CONSTRAINT teams_owner_id_key;
        RAISE NOTICE 'Dropped constraint teams_owner_id_key';
    ELSE
        RAISE NOTICE 'Constraint teams_owner_id_key does not exist';
    END IF;
    
    -- Also check for unique index
    IF EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'teams_owner_id_key') THEN
        DROP INDEX teams_owner_id_key;
        RAISE NOTICE 'Dropped index teams_owner_id_key';
    ELSE
        RAISE NOTICE 'Index teams_owner_id_key does not exist';
    END IF;
END $$;

-- After running this, delete the failed migration record from Flyway:
-- DELETE FROM flyway_schema_history WHERE version = '69' AND success = false;

-- Then restart your application to let Flyway re-run the migration
