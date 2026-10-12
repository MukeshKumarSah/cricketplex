-- Store a reference to the approved commentary template instead of duplicating filled text.

ALTER TABLE ball_events
    ADD COLUMN commentary_submission_id UUID REFERENCES commentary_submissions(id) ON DELETE SET NULL,
    ADD COLUMN non_striker_id UUID REFERENCES players(id);

CREATE INDEX idx_ball_events_commentary_submission
    ON ball_events(commentary_submission_id)
    WHERE commentary_submission_id IS NOT NULL;
