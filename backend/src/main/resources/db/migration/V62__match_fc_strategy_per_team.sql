CREATE TABLE IF NOT EXISTS match_fc_strategies (
    id UUID PRIMARY KEY,
    fixture_id UUID NOT NULL REFERENCES fixtures(id) ON DELETE CASCADE,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    declare_inn1 INTEGER,
    declare_inn2_lead INTEGER,
    follow_on BOOLEAN,
    declare_inn3_lead INTEGER,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_match_fc_strategy_fixture_team UNIQUE (fixture_id, team_id)
);

CREATE INDEX IF NOT EXISTS idx_match_fc_strategy_fixture ON match_fc_strategies(fixture_id);
CREATE INDEX IF NOT EXISTS idx_match_fc_strategy_team ON match_fc_strategies(team_id);

INSERT INTO match_fc_strategies (
    id, fixture_id, team_id, declare_inn1, declare_inn2_lead, follow_on, declare_inn3_lead
)
SELECT gen_random_uuid(), f.id, f.home_team_id, f.fc_declare_inn1, f.fc_declare_inn2_lead, f.fc_follow_on, f.fc_declare_inn3_lead
FROM fixtures f
WHERE f.fc_declare_inn1 IS NOT NULL
   OR f.fc_declare_inn2_lead IS NOT NULL
   OR f.fc_follow_on IS NOT NULL
   OR f.fc_declare_inn3_lead IS NOT NULL
ON CONFLICT ON CONSTRAINT uq_match_fc_strategy_fixture_team DO NOTHING;

INSERT INTO match_fc_strategies (
    id, fixture_id, team_id, declare_inn1, declare_inn2_lead, follow_on, declare_inn3_lead
)
SELECT gen_random_uuid(), f.id, f.away_team_id, f.fc_declare_inn1, f.fc_declare_inn2_lead, f.fc_follow_on, f.fc_declare_inn3_lead
FROM fixtures f
WHERE f.fc_declare_inn1 IS NOT NULL
   OR f.fc_declare_inn2_lead IS NOT NULL
   OR f.fc_follow_on IS NOT NULL
   OR f.fc_declare_inn3_lead IS NOT NULL
ON CONFLICT ON CONSTRAINT uq_match_fc_strategy_fixture_team DO NOTHING;
