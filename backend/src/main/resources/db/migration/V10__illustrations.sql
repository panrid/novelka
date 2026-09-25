-- A drawn illustration remembers what it was drawn from and which model call paid for it.
ALTER TABLE image
    ADD COLUMN prompt     TEXT,
    ADD COLUMN fragment   TEXT,
    ADD COLUMN ai_call_id BIGINT REFERENCES ai_call (id);
