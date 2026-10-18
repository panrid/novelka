-- «Кошик»: a translation or a chapter its team deleted waits 30 days, then its text is erased.
-- A translation in the bin is hidden from readers as moderation hides one (hidden_at, with the
-- reason 'trash'); history of шаги and runs stays even after the text is erased.
ALTER TABLE edition
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by BIGINT REFERENCES account (id) ON DELETE SET NULL,
    ADD COLUMN purged_at  TIMESTAMPTZ;

-- A chapter in the bin keeps its number (a gap readers do not see) and the text it had.
ALTER TABLE chapter
    ADD COLUMN deleted_at       TIMESTAMPTZ,
    ADD COLUMN deleted_by       BIGINT REFERENCES account (id) ON DELETE SET NULL,
    ADD COLUMN deleted_revision BIGINT;

-- The code sent to email and Telegram before a translation goes to the bin.
CREATE TABLE deletion_code
(
    account_id BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    edition_id BIGINT      NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    code_hash  TEXT        NOT NULL,
    attempts   INT         NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (account_id, edition_id)
);
