-- Add pitch_type to fixtures so home teams can set pitch for their home games
ALTER TABLE fixtures ADD COLUMN pitch_type VARCHAR(30) NOT NULL DEFAULT 'STANDARD';
