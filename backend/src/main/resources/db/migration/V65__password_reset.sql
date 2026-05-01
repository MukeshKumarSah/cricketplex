ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_reset_token VARCHAR(128),
    ADD COLUMN IF NOT EXISTS password_reset_token_expires_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_password_reset_token
    ON users(password_reset_token)
    WHERE password_reset_token IS NOT NULL;
