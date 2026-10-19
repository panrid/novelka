-- «Зберегти як мій набір» (етап 19, п. 8): a person's own set of models, made from a quality of the
-- site with some steps changed; listed next to the site's sets as «мій».
CREATE TABLE my_model_set
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    name            TEXT        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 60),
    -- The site's set it was changed from; its measured prices stay for the steps not changed.
    based_on        BIGINT      REFERENCES autotranslate_preset (id) ON DELETE SET NULL,
    analyze_model   TEXT        NOT NULL,
    translate_model TEXT        NOT NULL,
    -- NULL: no proofreading
    proofread_model TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (account_id, name)
);

ALTER TABLE autotranslate_choice ADD COLUMN my_set_id BIGINT REFERENCES my_model_set (id) ON DELETE SET NULL;
