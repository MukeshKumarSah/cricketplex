-- FC day-based simulation: strategy fields on fixtures, resume state on innings

ALTER TABLE fixtures ADD COLUMN IF NOT EXISTS fc_day INTEGER DEFAULT 0;
ALTER TABLE fixtures ADD COLUMN IF NOT EXISTS fc_declare_inn1 INTEGER;
ALTER TABLE fixtures ADD COLUMN IF NOT EXISTS fc_declare_inn2_lead INTEGER;
ALTER TABLE fixtures ADD COLUMN IF NOT EXISTS fc_follow_on BOOLEAN;
ALTER TABLE fixtures ADD COLUMN IF NOT EXISTS fc_declare_inn3_lead INTEGER;

ALTER TABLE innings ADD COLUMN IF NOT EXISTS resume_state TEXT;
