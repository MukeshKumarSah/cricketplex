-- Match scorecards and ball-by-ball data

CREATE TABLE match_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fixture_id UUID NOT NULL REFERENCES fixtures(id),
    toss_winner_id UUID NOT NULL REFERENCES teams(id),
    toss_decision VARCHAR(10) NOT NULL, -- BAT / BOWL
    winner_id UUID REFERENCES teams(id),       -- null = draw/tie
    result_type VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, RUNS, WICKETS, TIE, DRAW, NO_RESULT
    result_margin INTEGER,                     -- e.g. 42 (runs) or 5 (wickets)
    man_of_match_id UUID REFERENCES players(id),
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE innings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_result_id UUID NOT NULL REFERENCES match_results(id) ON DELETE CASCADE,
    innings_number INTEGER NOT NULL,          -- 1 or 2 (or 3,4 for FC)
    batting_team_id UUID NOT NULL REFERENCES teams(id),
    bowling_team_id UUID NOT NULL REFERENCES teams(id),
    total_runs INTEGER NOT NULL DEFAULT 0,
    total_wickets INTEGER NOT NULL DEFAULT 0,
    total_overs DOUBLE PRECISION NOT NULL DEFAULT 0,
    extras INTEGER NOT NULL DEFAULT 0,
    all_out BOOLEAN NOT NULL DEFAULT false,
    declared BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE batting_scorecards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    innings_id UUID NOT NULL REFERENCES innings(id) ON DELETE CASCADE,
    player_id UUID NOT NULL REFERENCES players(id),
    batting_position INTEGER NOT NULL,
    runs_scored INTEGER NOT NULL DEFAULT 0,
    balls_faced INTEGER NOT NULL DEFAULT 0,
    fours INTEGER NOT NULL DEFAULT 0,
    sixes INTEGER NOT NULL DEFAULT 0,
    dismissal_type VARCHAR(30),   -- BOWLED, CAUGHT, LBW, RUN_OUT, STUMPED, HIT_WICKET, NOT_OUT, RETIRED
    bowler_id UUID REFERENCES players(id),
    fielder_id UUID REFERENCES players(id),
    strike_rate DOUBLE PRECISION NOT NULL DEFAULT 0
);

CREATE TABLE bowling_scorecards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    innings_id UUID NOT NULL REFERENCES innings(id) ON DELETE CASCADE,
    player_id UUID NOT NULL REFERENCES players(id),
    overs DOUBLE PRECISION NOT NULL DEFAULT 0,
    maidens INTEGER NOT NULL DEFAULT 0,
    runs_conceded INTEGER NOT NULL DEFAULT 0,
    wickets INTEGER NOT NULL DEFAULT 0,
    economy DOUBLE PRECISION NOT NULL DEFAULT 0,
    dot_balls INTEGER NOT NULL DEFAULT 0,
    wides INTEGER NOT NULL DEFAULT 0,
    no_balls INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE ball_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    innings_id UUID NOT NULL REFERENCES innings(id) ON DELETE CASCADE,
    over_number INTEGER NOT NULL,
    ball_number INTEGER NOT NULL,   -- 1-6 within the over (legal balls)
    batsman_id UUID NOT NULL REFERENCES players(id),
    bowler_id UUID NOT NULL REFERENCES players(id),
    runs INTEGER NOT NULL DEFAULT 0,
    is_wicket BOOLEAN NOT NULL DEFAULT false,
    is_boundary BOOLEAN NOT NULL DEFAULT false,
    is_six BOOLEAN NOT NULL DEFAULT false,
    is_wide BOOLEAN NOT NULL DEFAULT false,
    is_no_ball BOOLEAN NOT NULL DEFAULT false,
    is_bye BOOLEAN NOT NULL DEFAULT false,
    is_leg_bye BOOLEAN NOT NULL DEFAULT false,
    dismissal_type VARCHAR(30),
    fielder_id UUID REFERENCES players(id),
    commentary TEXT
);

CREATE INDEX idx_match_results_fixture ON match_results(fixture_id);
CREATE INDEX idx_innings_match_result ON innings(match_result_id);
CREATE INDEX idx_batting_sc_innings ON batting_scorecards(innings_id);
CREATE INDEX idx_bowling_sc_innings ON bowling_scorecards(innings_id);
CREATE INDEX idx_ball_events_innings ON ball_events(innings_id);
