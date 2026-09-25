-- Шаги without payments (рішення 29): the site owner grants them, people spend them on
-- autotranslation and pictures. A run holds шаги first and is charged what it really cost.

CREATE TABLE shah_balance
(
    account_id BIGINT PRIMARY KEY REFERENCES account (id),
    available  INT         NOT NULL DEFAULT 0 CHECK (available >= 0),
    reserved   INT         NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Шаги held for one run until it ends: then the cost is charged and the rest returned.
CREATE TABLE shah_hold
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES account (id),
    amount     INT         NOT NULL CHECK (amount > 0),
    charged    INT,
    what       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    settled_at TIMESTAMPTZ
);

CREATE INDEX shah_hold_open ON shah_hold (account_id) WHERE settled_at IS NULL;

-- Every change of a balance, for the person's history and for checking the sums.
CREATE TABLE shah_entry
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES account (id),
    kind       TEXT        NOT NULL CHECK (kind IN ('grant', 'hold', 'charge', 'release')),
    amount     INT         NOT NULL CHECK (amount >= 0),
    hold_id    BIGINT REFERENCES shah_hold (id),
    what       TEXT,     -- the owner's note for a grant, or what the шаги went on
    actor_id   BIGINT REFERENCES account (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX shah_entry_by_account ON shah_entry (account_id, id DESC);

-- A run paid from a person's шаги; job.hold_tx_id points at its shah_hold.
ALTER TABLE job DROP CONSTRAINT job_funding_check;
ALTER TABLE job ADD CONSTRAINT job_funding_check CHECK (funding IN ('site', 'team', 'account'));
