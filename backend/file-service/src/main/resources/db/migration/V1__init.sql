CREATE TABLE files (
    id          UUID         PRIMARY KEY,
    owner_id    UUID         NULL,
    room_id     UUID         NULL,
    kind        VARCHAR(10)  NOT NULL,
    mime_type   VARCHAR(100) NOT NULL,
    size_bytes  BIGINT       NOT NULL,
    object_key  VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_files_room ON files (room_id);
CREATE INDEX idx_files_owner ON files (owner_id);
