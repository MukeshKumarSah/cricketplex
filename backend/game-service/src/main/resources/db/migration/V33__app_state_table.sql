CREATE TABLE app_state (
    key   VARCHAR(64) PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);

INSERT INTO app_state (key, value) VALUES ('last_aging_date', CURRENT_DATE::TEXT);
