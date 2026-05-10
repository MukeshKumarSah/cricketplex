-- Chat conversations (both 1-to-1 and group)
CREATE TABLE chat_conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    is_group    BOOLEAN NOT NULL DEFAULT false,
    group_name  VARCHAR(100),
    created_by  UUID REFERENCES users(id),
    created_at  TIMESTAMP DEFAULT now()
);

-- Conversation members
CREATE TABLE chat_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    is_admin        BOOLEAN NOT NULL DEFAULT false,
    last_read_at    TIMESTAMP DEFAULT now(),
    joined_at       TIMESTAMP DEFAULT now(),
    UNIQUE(conversation_id, user_id)
);

-- Messages
CREATE TABLE chat_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id),
    content         TEXT NOT NULL,
    created_at      TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_chat_messages_conv ON chat_messages(conversation_id, created_at DESC);
CREATE INDEX idx_chat_members_user ON chat_members(user_id);
