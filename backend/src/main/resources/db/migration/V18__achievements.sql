-- Achievements and levels, for joy only (рішення 33): no rights, no шаги. Counted from what
-- people already do on the site; the level is shown next to the nick.
CREATE TABLE achievement
(
    account_id BIGINT      NOT NULL REFERENCES account (id),
    code       TEXT        NOT NULL,
    earned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, code)
);

CREATE TABLE account_level
(
    account_id BIGINT PRIMARY KEY REFERENCES account (id),
    points     INT         NOT NULL,
    level      INT         NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
