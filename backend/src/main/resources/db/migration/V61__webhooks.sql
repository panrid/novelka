-- Етап 22.5: webhooks. A person names an https address and the events it wants; the site POSTs
-- each event there, signed with the endpoint's secret, and tries again after pauses for about a
-- day. Deliveries wait in a queue filled in the same transaction as the event itself.

CREATE TABLE webhook_endpoint
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    url             TEXT        NOT NULL CHECK (char_length(url) BETWEEN 9 AND 500),
    -- Kept as is: the site needs it to sign every request (HMAC). Shown to the person once.
    secret          TEXT        NOT NULL,
    events          TEXT[]      NOT NULL CHECK (cardinality(events) > 0 AND events <@ ARRAY
        ['chapter.published', 'run.finished', 'run.stopped', 'suggestion.created', 'source.chapters']),
    -- Empty: every translation the person may see (events for the team: of the person's teams).
    edition_ids     BIGINT[]    NOT NULL DEFAULT '{}',
    -- Failed attempts in a row and since when; a success clears both.
    failures        INT         NOT NULL DEFAULT 0,
    failing_since   TIMESTAMPTZ,
    disabled_at     TIMESTAMPTZ,
    disabled_reason TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX webhook_endpoint_account ON webhook_endpoint (account_id, id);
CREATE INDEX webhook_endpoint_live ON webhook_endpoint USING GIN (events) WHERE disabled_at IS NULL;

-- One event for one endpoint: its body (without the envelope), where it is in the queue and how
-- the last attempt went — the journal «Доставки» reads the same rows.
CREATE TABLE webhook_delivery
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    endpoint_id      BIGINT      NOT NULL REFERENCES webhook_endpoint (id) ON DELETE CASCADE,
    -- X-Novelka-Delivery: the same on every attempt, so the receiver can drop repeats.
    delivery_key     UUID        NOT NULL UNIQUE,
    event            TEXT        NOT NULL,
    edition_id       BIGINT REFERENCES edition (id) ON DELETE CASCADE,
    first_number     INT,
    last_number      INT,
    data             JSONB       NOT NULL,
    -- sending: taken by the sender; one left so by a crash goes back to the queue (at least once)
    state            TEXT        NOT NULL DEFAULT 'pending'
        CHECK (state IN ('pending', 'sending', 'delivered', 'failed', 'cancelled')),
    attempts         INT         NOT NULL DEFAULT 0,
    not_before       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_status      INT,
    last_error       TEXT,
    last_duration_ms INT,
    last_attempt_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ
);

CREATE INDEX webhook_delivery_ready ON webhook_delivery (not_before, id) WHERE state = 'pending';
CREATE INDEX webhook_delivery_sending ON webhook_delivery (not_before) WHERE state = 'sending';
CREATE INDEX webhook_delivery_endpoint ON webhook_delivery (endpoint_id, id DESC);
CREATE INDEX webhook_delivery_finished ON webhook_delivery (finished_at) WHERE state NOT IN ('pending', 'sending');
