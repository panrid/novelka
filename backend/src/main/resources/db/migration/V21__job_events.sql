-- The journal of an autotranslation run (етап 17): what analysis added to the glossary, which
-- entries each part used, retries and their reasons, what proofreading changed.
CREATE TABLE job_event
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id         BIGINT      NOT NULL REFERENCES job (id) ON DELETE CASCADE,
    chapter_number INT         NOT NULL,
    kind           TEXT        NOT NULL,
    payload        JSONB       NOT NULL DEFAULT '{}',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX job_event_by_job ON job_event (job_id, id);
