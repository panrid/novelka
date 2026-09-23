CREATE TABLE glossary_proposal_dismissals (
    novel_id text NOT NULL REFERENCES novels(id) ON DELETE CASCADE,
    fingerprint text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(novel_id,fingerprint)
);
