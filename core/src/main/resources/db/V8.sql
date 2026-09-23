-- Accounts sign in by email or current nickname. username keeps the public nickname, relations keep using id.
ALTER TABLE accounts ADD COLUMN email text;
CREATE UNIQUE INDEX accounts_email ON accounts(email) WHERE email IS NOT NULL;
-- Append-only nickname history for administrators and change cooldown. It never authenticates anyone.
CREATE TABLE nickname_changes (
    id bigserial PRIMARY KEY,
    account_id text NOT NULL REFERENCES accounts(id),
    previous_nickname text NOT NULL,
    new_nickname text NOT NULL,
    changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX nickname_changes_account ON nickname_changes(account_id,changed_at DESC,id)
