-- One vote per account and target. Novels and comments share this table and the voting rules.
CREATE TABLE votes (
    target_type text NOT NULL CHECK (target_type IN ('novel','comment')),
    target_id text NOT NULL,
    account_id text NOT NULL REFERENCES accounts(id),
    value smallint NOT NULL CHECK (value IN (-1,1)),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (target_type, target_id, account_id)
);
CREATE INDEX votes_target ON votes(target_type, target_id)
