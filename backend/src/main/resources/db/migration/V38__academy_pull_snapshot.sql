ALTER TABLE academy_pulls
    ADD COLUMN IF NOT EXISTS snapshot_role        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS snapshot_age         INT,
    ADD COLUMN IF NOT EXISTS snapshot_age_days    INT,
    ADD COLUMN IF NOT EXISTS snapshot_bat_rating  INT,
    ADD COLUMN IF NOT EXISTS snapshot_bowl_rating INT,
    ADD COLUMN IF NOT EXISTS snapshot_keeper_rating INT,
    ADD COLUMN IF NOT EXISTS snapshot_fld_rating  INT,
    ADD COLUMN IF NOT EXISTS snapshot_stamina     INT,
    ADD COLUMN IF NOT EXISTS snapshot_confidence  INT,
    ADD COLUMN IF NOT EXISTS snapshot_experience  INT;
