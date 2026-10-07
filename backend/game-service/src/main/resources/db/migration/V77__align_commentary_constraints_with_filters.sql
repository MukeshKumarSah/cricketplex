-- Align commentary_submissions constraints with current frontend/backend filter values
-- Keep legacy values for backward compatibility.

-- Normalize legacy/invalid rows first so new constraints can be added safely.
UPDATE commentary_submissions
SET match_format = 'all'
WHERE match_format IS NULL
   OR match_format NOT IN ('T20', 'ODI', 'TEST', 'FC', 'all');

UPDATE commentary_submissions
SET phase = 'all'
WHERE phase IS NULL
   OR phase NOT IN ('powerplay', 'middle', 'death', 'pressure', 'cruising', 'all');

UPDATE commentary_submissions
SET bowler_type = 'ALL'
WHERE bowler_type IS NULL
   OR bowler_type NOT IN (
       'FAST_SEAM', 'FAST', 'MEDIUM_FAST', 'MEDIUM', 'SPINNER',
       'F', 'FM', 'MF', 'M', 'FS', 'WS', 'LAP', 'PACE', 'ALL'
   );

UPDATE commentary_submissions
SET wicket_situation = NULL
WHERE wicket_situation IS NOT NULL
  AND (
      btrim(wicket_situation) = ''
      OR wicket_situation NOT IN (
          'early_wickets', 'collapse', 'rebuilding', 'set_partnership',
          'run_out_striker', 'run_out_non_striker'
      )
  );

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
