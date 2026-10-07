-- Add DAILY as an allowed schedule_type for friendly_tournaments
ALTER TABLE friendly_tournaments
    DROP CONSTRAINT IF EXISTS friendly_tournaments_schedule_type_check;

ALTER TABLE friendly_tournaments
    ADD CONSTRAINT friendly_tournaments_schedule_type_check
        CHECK (schedule_type IN ('WEEKLY', 'BIWEEKLY', 'TRIWEEKLY', 'DAILY'));
