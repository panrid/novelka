-- One-time links sent by email. Only the SHA-256 of a token is stored, so a database leak cannot reset passwords.
CREATE TABLE email_tokens (
    id bigserial PRIMARY KEY,
    account_id text NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    purpose text NOT NULL CHECK (purpose IN ('reset','verify')),
    token_hash text NOT NULL UNIQUE,
    email text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    used_at timestamptz
);
CREATE INDEX email_tokens_account ON email_tokens(account_id, purpose, created_at DESC);
-- Accounts created before email confirmation existed start unconfirmed.
ALTER TABLE accounts ADD COLUMN email_verified_at timestamptz
