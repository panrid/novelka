-- Corrections are reviewed per novel: its translator (owner), the accounts they pick, or everyone when review is open.
-- Novels without an owner (CLI imports) are managed by administrators only.
ALTER TABLE novels ADD COLUMN owner_id text REFERENCES accounts(id);
ALTER TABLE novels ADD COLUMN open_review boolean NOT NULL DEFAULT false;
UPDATE novels SET owner_id=(SELECT id FROM accounts WHERE role='OWNER');
CREATE INDEX novels_owner ON novels(owner_id);
CREATE TABLE novel_editors (
    novel_id text NOT NULL REFERENCES novels(id),
    account_id text NOT NULL REFERENCES accounts(id),
    granted_by text REFERENCES accounts(id),
    granted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (novel_id, account_id)
);
CREATE INDEX novel_editors_account ON novel_editors(account_id);
-- Former global editors keep their rights on every existing novel and become regular users.
INSERT INTO novel_editors(novel_id,account_id) SELECT n.id,a.id FROM novels n CROSS JOIN accounts a WHERE a.role='EDITOR';
ALTER TABLE accounts DROP CONSTRAINT accounts_role_check;
UPDATE accounts SET role='READER' WHERE role='EDITOR';
ALTER TABLE accounts ADD CONSTRAINT accounts_role_check CHECK (role IN ('READER','MODERATOR','ADMIN','OWNER'))
