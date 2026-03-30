CREATE TABLE bot_users (
    telegram_user_id BIGINT PRIMARY KEY,
    chat_id          BIGINT NOT NULL,
    language_code    VARCHAR(5) NOT NULL DEFAULT 'en',
    role             VARCHAR(20) NOT NULL DEFAULT 'viewer',
    subscribed       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_active_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bot_users_chat_id ON bot_users(chat_id);
CREATE INDEX idx_bot_users_subscribed ON bot_users(subscribed) WHERE subscribed = TRUE;
