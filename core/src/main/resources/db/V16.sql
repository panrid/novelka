-- Personal reading lists: one status per account and novel.
CREATE TABLE library_entries (
    account_id text NOT NULL REFERENCES accounts(id),
    novel_id text NOT NULL REFERENCES novels(id),
    status text NOT NULL CHECK (status IN ('reading','planned','completed','on_hold','dropped')),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, novel_id)
);
CREATE INDEX library_entries_status ON library_entries(account_id, status, updated_at DESC)
