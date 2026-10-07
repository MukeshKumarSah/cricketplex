ALTER TABLE users
    ADD COLUMN IF NOT EXISTS supporter_plan VARCHAR(32),
    ADD COLUMN IF NOT EXISTS supporter_since TIMESTAMP,
    ADD COLUMN IF NOT EXISTS supporter_until TIMESTAMP,
    ADD COLUMN IF NOT EXISTS supporter_provider VARCHAR(32),
    ADD COLUMN IF NOT EXISTS supporter_order_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS supporter_payment_id VARCHAR(128);
