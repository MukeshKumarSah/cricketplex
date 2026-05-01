ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_verification_token VARCHAR(128),
    ADD COLUMN IF NOT EXISTS email_verification_token_expires_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_verification_token
    ON users(email_verification_token)
    WHERE email_verification_token IS NOT NULL;
