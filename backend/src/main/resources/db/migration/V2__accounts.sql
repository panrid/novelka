-- Accounts, one-time email links and web sessions.

CREATE TABLE account
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nick               TEXT        NOT NULL,
    nick_key           TEXT        NOT NULL UNIQUE, -- lower(nick): «Mika» and «mika» are one nick
    email              TEXT        NOT NULL,
    email_key          TEXT        NOT NULL UNIQUE, -- lower(email)
    email_verified_at  TIMESTAMPTZ,
    password_hash      TEXT        NOT NULL,
    site_role          TEXT        NOT NULL DEFAULT 'reader'
        CHECK (site_role IN ('reader', 'moderator', 'admin', 'owner')),
    bio                TEXT        NOT NULL DEFAULT '' CHECK (char_length(bio) <= 500),
    avatar_image_id    BIGINT,                      -- FK arrives with the media tables
    dm_policy          TEXT        NOT NULL DEFAULT 'everyone' CHECK (dm_policy IN ('everyone', 'nobody')),
    show_reading       BOOLEAN     NOT NULL DEFAULT TRUE,
    adult_confirmed_at TIMESTAMPTZ,
    show_shah          BOOLEAN     NOT NULL DEFAULT TRUE,
    nick_changed_at    TIMESTAMPTZ,
    last_seen_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The site has exactly one owner.
CREATE UNIQUE INDEX account_single_owner ON account ((TRUE)) WHERE site_role = 'owner';

CREATE TABLE nick_change
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES account (id),
    old_nick   TEXT        NOT NULL,
    new_nick   TEXT        NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX nick_change_by_account ON nick_change (account_id, changed_at);

-- Links from emails. Only the SHA-256 of the token is stored.
CREATE TABLE email_token
(
    token_hash TEXT PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES account (id),
    purpose    TEXT        NOT NULL CHECK (purpose IN ('verify', 'reset')),
    email      TEXT        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX email_token_by_account ON email_token (account_id, purpose, created_at);

-- Spring Session JDBC 4.1 (schema-postgresql.sql): sessions survive restarts.
CREATE TABLE spring_session
(
    primary_id            CHAR(36) NOT NULL PRIMARY KEY,
    session_id            CHAR(36) NOT NULL,
    creation_time         BIGINT   NOT NULL,
    last_access_time      BIGINT   NOT NULL,
    max_inactive_interval INT      NOT NULL,
    expiry_time           BIGINT   NOT NULL,
    principal_name        VARCHAR(100)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes
(
    session_primary_id CHAR(36)     NOT NULL REFERENCES spring_session (primary_id) ON DELETE CASCADE,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA        NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name)
);
