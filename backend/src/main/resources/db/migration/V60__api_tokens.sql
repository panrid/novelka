-- Етап 22.1: access tokens for developers and agents. A token is shown once; the database keeps
-- only its SHA-256, so a leaked copy of the database lets nobody in. A token signs in as its owner
-- (never more than the owner may do) and its scopes narrow that further.

CREATE TABLE api_token
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id   BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    name         TEXT        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 60),
    hint         TEXT        NOT NULL,        -- «nvk_Ab3dE…»: enough to recognise it, useless to sign in
    secret_hash  TEXT        NOT NULL UNIQUE, -- hex SHA-256 of the whole token
    scopes       TEXT[]      NOT NULL CHECK (cardinality(scopes) > 0
        AND scopes <@ ARRAY ['read', 'library', 'suggest', 'studio', 'translate', 'admin']),
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    last_used_ip TEXT,
    revoked_at   TIMESTAMPTZ,
    revoked_by   BIGINT REFERENCES account (id) ON DELETE SET NULL
);

CREATE INDEX api_token_account ON api_token (account_id, id DESC);

-- Every change made with a token: who, with which token, what and how it ended.
CREATE TABLE api_token_write
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    token_id   BIGINT      NOT NULL REFERENCES api_token (id) ON DELETE CASCADE,
    account_id BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    method     TEXT        NOT NULL,
    path       TEXT        NOT NULL,
    status     INT         NOT NULL,
    ip         TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX api_token_write_token ON api_token_write (token_id, id DESC);
