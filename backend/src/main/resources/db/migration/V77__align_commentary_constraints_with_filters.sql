-- Align commentary_submissions constraints with current frontend/backend filter values
-- Keep legacy values for backward compatibility.

ALTER TABLE commentary_submissions
    DROP CONSTRAINT IF EXISTS commentary_submissions_match_format_check,
    DROP CONSTRAINT IF EXISTS commentary_submissions_phase_check,
    DROP CONSTRAINT IF EXISTS commentary_submissions_bowler_type_check,
    DROP CONSTRAINT IF EXISTS commentary_submissions_wicket_situation_check;

ALTER TABLE commentary_submissions
    ADD CONSTRAINT commentary_submissions_match_format_check
        CHECK (match_format IN ('T20', 'ODI', 'TEST', 'FC', 'all')),
    ADD CONSTRAINT commentary_submissions_phase_check
        CHECK (phase IN ('powerplay', 'middle', 'death', 'pressure', 'cruising', 'all')),
    ADD CONSTRAINT commentary_submissions_bowler_type_check
        CHECK (bowler_type IN (
            'FAST_SEAM', 'FAST', 'MEDIUM_FAST', 'MEDIUM', 'SPINNER',
            'F', 'FM', 'MF', 'M', 'FS', 'WS', 'LAP', 'PACE', 'ALL'
        )),
    ADD CONSTRAINT commentary_submissions_wicket_situation_check
        CHECK (
            wicket_situation IS NULL OR
            wicket_situation IN (
                'early_wickets', 'collapse', 'rebuilding', 'set_partnership',
                'run_out_striker', 'run_out_non_striker'
            )
        );
