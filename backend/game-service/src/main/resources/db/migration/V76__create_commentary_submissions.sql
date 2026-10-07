-- Commentary Submissions System
-- User-generated match commentary with admin approval workflow

-- Enable pg_trgm extension for similarity search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE commentary_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    
    -- Commentary content
    commentary_text VARCHAR(500) NOT NULL,
    
    -- Approval workflow
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    
    -- Core filters (required)
    match_format VARCHAR(10) NOT NULL CHECK (match_format IN ('T20', 'ODI', 'TEST')),
    phase VARCHAR(20) NOT NULL CHECK (phase IN ('powerplay', 'middle', 'death', 'pressure', 'cruising')),
    bowler_type VARCHAR(20) NOT NULL CHECK (bowler_type IN ('FAST_SEAM', 'FAST', 'MEDIUM_FAST', 'MEDIUM', 'SPINNER')),
    event_type VARCHAR(20) NOT NULL, -- '0', '1', '4', '6', '1LB', '2NB', 'BOWLED', 'CAUGHT', etc.
    
    -- Optional context filters
    wicket_situation VARCHAR(30) CHECK (wicket_situation IN ('early_wickets', 'collapse', 'rebuilding', 'set_partnership', NULL)),
    batsman_state VARCHAR(30) CHECK (batsman_state IN ('new_batsman', 'settling', 'set', 'milestone_approaching', NULL)),
    match_pressure VARCHAR(20) CHECK (match_pressure IN ('low', 'medium', 'high', NULL)),
    
    -- Extra context tags (JSON array)
    extra_tags TEXT, -- JSON: ["catch_taken", "great_fielding", ...]
    
    -- Placeholder tracking (JSON array)
    placeholders_used TEXT, -- JSON: ["batsman", "fielder", "runs"]
    
    -- Admin review
    admin_notes TEXT,
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMP,
    
    -- Usage statistics
    times_used INTEGER NOT NULL DEFAULT 0,
    
    -- Edit tracking
    last_edited_by UUID REFERENCES users(id) ON DELETE SET NULL,
    last_edited_at TIMESTAMP,
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for efficient querying
CREATE INDEX idx_commentary_status ON commentary_submissions(status);
CREATE INDEX idx_commentary_user ON commentary_submissions(user_id);
CREATE INDEX idx_commentary_team ON commentary_submissions(team_id);
CREATE INDEX idx_commentary_approved_lookup ON commentary_submissions(status, match_format, phase, bowler_type, event_type) WHERE status = 'approved';
CREATE INDEX idx_commentary_text_similarity ON commentary_submissions USING gin(to_tsvector('english', commentary_text));

-- Full-text search for similarity detection
CREATE INDEX idx_commentary_text_search ON commentary_submissions USING gin(commentary_text gin_trgm_ops);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_commentary_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER commentary_updated_at_trigger
    BEFORE UPDATE ON commentary_submissions
    FOR EACH ROW
    EXECUTE FUNCTION update_commentary_timestamp();

-- Enable trigram extension for similarity search (if not already enabled)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
