-- Transaction log for tracking all financial activities
CREATE TABLE transaction_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES teams(id),
    type VARCHAR(30) NOT NULL,
    description TEXT NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transaction_logs_team ON transaction_logs(team_id);
CREATE INDEX idx_transaction_logs_type ON transaction_logs(team_id, type);

-- Update default funds to 50,000
ALTER TABLE teams ALTER COLUMN funds SET DEFAULT 50000;
UPDATE teams SET funds = 50000 WHERE funds = 1000000;
