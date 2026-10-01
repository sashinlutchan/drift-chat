CREATE TABLE rooms (
    id             UUID PRIMARY KEY,
    name           VARCHAR(50) NOT NULL,
    slug           VARCHAR(80) NOT NULL UNIQUE,
    created_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    empty_since    TIMESTAMPTZ NULL
);

CREATE TABLE room_members (
    room_id   UUID        NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at   TIMESTAMPTZ NULL,
    PRIMARY KEY (room_id, user_id)
);

CREATE INDEX idx_room_members_user ON room_members (user_id);

CREATE TABLE room_pins (
    user_id   UUID        NOT NULL,
    room_id   UUID        NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    pinned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, room_id)
);
