-- Personal translation balances. Only top-ups are stored: the balance is top-ups minus the budget of active charged
-- tasks and the real spending of finished ones, so a crash never needs a refund step.
CREATE TABLE balance_topups (
    id bigserial PRIMARY KEY,
    account_id text NOT NULL REFERENCES accounts(id),
    amount_usd numeric(12,4) NOT NULL CHECK (amount_usd <> 0),
    actor_id text NOT NULL REFERENCES accounts(id),
    note text NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX balance_topups_account ON balance_topups(account_id, created_at DESC);
-- Tasks queued before balances existed were paid from the site budget and are not charged to anyone.
ALTER TABLE web_tasks ADD COLUMN charged boolean NOT NULL DEFAULT false;
CREATE INDEX web_tasks_charged ON web_tasks(actor_id) WHERE charged;
-- Personal notifications: a task result goes to its author (administrators still see every task result).
ALTER TABLE notifications ADD COLUMN account_id text REFERENCES accounts(id) ON DELETE CASCADE;
UPDATE notifications n SET account_id=t.actor_id FROM web_tasks t WHERE t.id=n.task_id;
CREATE INDEX notifications_account ON notifications(account_id, id DESC) WHERE account_id IS NOT NULL
