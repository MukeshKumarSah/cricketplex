-- Convert player skills from INT to DECIMAL(5,2) for accurate fractional progression
-- Frontend will display floor values, but backend tracks decimals for smooth progression
-- Training logs only record when integer floor value changes (pops/flops)

ALTER TABLE players 
    ALTER COLUMN bat_rating TYPE DECIMAL(5,2),
    ALTER COLUMN bowl_rating TYPE DECIMAL(5,2),
    ALTER COLUMN keeper_rating TYPE DECIMAL(5,2),
    ALTER COLUMN fld_rating TYPE DECIMAL(5,2),
    ALTER COLUMN stamina TYPE DECIMAL(5,2),
    ALTER COLUMN confidence TYPE DECIMAL(5,2);
