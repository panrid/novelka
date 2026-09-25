-- Administration: who hid what and why, and a journal of every moderator and owner action.

ALTER TABLE message
    ADD COLUMN hidden_at     TIMESTAMPTZ,
    ADD COLUMN hidden_by     BIGINT REFERENCES account (id),
    ADD COLUMN hidden_reason TEXT;

ALTER TABLE chat_message
    ADD COLUMN hidden_reason TEXT;

ALTER TABLE edition
    ADD COLUMN hidden_by BIGINT REFERENCES account (id);

CREATE TABLE audit_log
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id    BIGINT      NOT NULL REFERENCES account (id),
    action      TEXT        NOT NULL,
    target_type TEXT        NOT NULL,
    target_id   BIGINT,
    details     JSONB       NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_log_recent ON audit_log (id DESC);
