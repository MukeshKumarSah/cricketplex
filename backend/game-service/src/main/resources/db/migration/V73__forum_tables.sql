-- ─────────────────────────────────────────────────────────────────────────────
-- Forum feature: categories, threads, comments, per-user read tracking
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE forum_categories (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(255),
    display_order INT         NOT NULL DEFAULT 0,
    created_by   UUID         REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO forum_categories (name, description, display_order) VALUES
    ('General Question',    'Ask anything about CricketPlex',               1),
    ('Newbies Questions',   'New to the game? Ask here!',                   2),
    ('Transfer Market',     'Discuss player transfers and trades',           3),
    ('Bugs',                'Report bugs and issues',                       4),
    ('Dev Ideas',           'Suggest new features and improvements',        5),
    ('Off Topics',          'Chat about anything else',                     6),
    ('Friendlies',          'Arrange friendly matches and tournaments',      7),
    ('Player Ads',          'Advertise players for sale or trade',           8);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE forum_threads (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id      UUID         NOT NULL REFERENCES forum_categories(id) ON DELETE CASCADE,
    title            VARCHAR(200) NOT NULL,
    body             TEXT         NOT NULL,
    created_by       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_pinned        BOOLEAN      NOT NULL DEFAULT FALSE,
    is_locked        BOOLEAN      NOT NULL DEFAULT FALSE,
    comment_count    INT          NOT NULL DEFAULT 0,
    last_activity_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_forum_threads_category     ON forum_threads(category_id);
CREATE INDEX idx_forum_threads_last_activity ON forum_threads(last_activity_at DESC);
CREATE INDEX idx_forum_threads_created_at   ON forum_threads(created_at DESC);
CREATE INDEX idx_forum_threads_comment_count ON forum_threads(comment_count DESC);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE forum_comments (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id  UUID      NOT NULL REFERENCES forum_threads(id) ON DELETE CASCADE,
    body       TEXT      NOT NULL,
    created_by UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_forum_comments_thread ON forum_comments(thread_id);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE forum_thread_reads (
    user_id      UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    thread_id    UUID      NOT NULL REFERENCES forum_threads(id) ON DELETE CASCADE,
    last_read_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, thread_id)
);
