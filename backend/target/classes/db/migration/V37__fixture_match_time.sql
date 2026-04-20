-- Add match_time column to fixtures table for friendly matches
ALTER TABLE fixtures ADD COLUMN IF NOT EXISTS match_time VARCHAR(5);

-- Backfill match_time from friendly_challenges for existing accepted friendly fixtures
UPDATE fixtures f
SET match_time = fc.match_time
FROM friendly_challenges fc
WHERE fc.fixture_id = f.id
  AND f.match_type = 'FRIENDLY'
  AND fc.match_time IS NOT NULL;
