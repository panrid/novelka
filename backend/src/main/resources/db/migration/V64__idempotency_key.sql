-- Етап 22.10: a request sent again with the same Idempotency-Key gets the answer of the first one
-- and changes nothing twice. Keys are a person's own (whatever token sent them) and kept 7 days;
-- the answer is kept as the text it was sent as, byte for byte.

CREATE TABLE idempotency_key
(
    account_id  BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    key         TEXT        NOT NULL,
    fingerprint TEXT        NOT NULL,
    status      INT         NOT NULL,
    body        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, key)
);

CREATE INDEX idempotency_key_age ON idempotency_key (created_at);
