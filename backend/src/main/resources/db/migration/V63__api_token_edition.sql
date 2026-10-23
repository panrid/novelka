-- Етап 22.12: a token may be limited to one translation, so it is safe to hand to a bot or an AI
-- agent: it cannot touch the person's other translations. Gone with its translation, never widened.

ALTER TABLE api_token
    ADD COLUMN edition_id BIGINT REFERENCES edition (id) ON DELETE CASCADE;

CREATE INDEX api_token_edition ON api_token (edition_id) WHERE edition_id IS NOT NULL;
