-- ─────────────────────────────────────────────────────────────────────────────
-- Blog feature: blogs, reactions + "Blog" forum category
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE blogs (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(300) NOT NULL,
    description     TEXT         NOT NULL,
    body            TEXT         NOT NULL DEFAULT '',
    author_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    author_name     VARCHAR(200) NOT NULL,
    is_pinned       BOOLEAN      NOT NULL DEFAULT FALSE,
    forum_thread_id UUID         REFERENCES forum_threads(id) ON DELETE SET NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_blogs_created_at ON blogs(created_at DESC);
CREATE INDEX idx_blogs_pinned_created ON blogs(is_pinned DESC, created_at DESC);

CREATE TABLE blog_reactions (
    blog_id    UUID        NOT NULL REFERENCES blogs(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reaction   VARCHAR(10) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (blog_id, user_id, reaction)
);

CREATE INDEX idx_blog_reactions_blog ON blog_reactions(blog_id);

-- "Blog" forum category (where auto-created threads land)
INSERT INTO forum_categories (name, description, display_order)
VALUES ('Blog', 'Official blogs and articles from the CricketPlex team', 9);
