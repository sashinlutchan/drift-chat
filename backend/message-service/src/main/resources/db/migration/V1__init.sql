CREATE TABLE messages (
    id             UUID PRIMARY KEY,
    room_id        UUID         NOT NULL,
    sender_id      UUID         NULL,
    sender_deleted BOOLEAN      NOT NULL DEFAULT FALSE,
    type           VARCHAR(10)  NOT NULL,
    ciphertext     BYTEA        NOT NULL,
    nonce          BYTEA        NOT NULL,
    key_version    INT          NOT NULL,
    file_id        UUID         NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_room_created ON messages (room_id, created_at DESC);
