-- users_db initial schema (spec section 18)
CREATE TABLE users (
    id             UUID PRIMARY KEY,
    username       VARCHAR(20)  NOT NULL UNIQUE,
    name           VARCHAR(50)  NOT NULL,
    email          VARCHAR(255) NOT NULL,
    avatar_file_id UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ  NOT NULL
);
