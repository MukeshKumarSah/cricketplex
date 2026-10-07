-- V5: Add last_active_at to users, add ratings to teams

ALTER TABLE users
    ADD COLUMN last_active_at TIMESTAMP;

-- Backfill last_active_at with updated_at for existing users
UPDATE users SET last_active_at = updated_at WHERE last_active_at IS NULL;

ALTER TABLE teams
    ADD COLUMN odi_rating  INTEGER NOT NULL DEFAULT 1000,
    ADD COLUMN t20_rating  INTEGER NOT NULL DEFAULT 1000,
    ADD COLUMN fc_rating   INTEGER NOT NULL DEFAULT 1000;
