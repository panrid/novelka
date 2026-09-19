CREATE TABLE IF NOT EXISTS novels (
    id text PRIMARY KEY,
    data jsonb NOT NULL
);
CREATE TABLE IF NOT EXISTS chapters (
    novel_id text NOT NULL REFERENCES novels(id),
    number integer NOT NULL,
    source_hash text NOT NULL,
    data jsonb NOT NULL,
    PRIMARY KEY(novel_id, number)
);
CREATE TABLE IF NOT EXISTS chapter_versions (
    novel_id text NOT NULL,
    number integer NOT NULL,
    source_hash text NOT NULL,
    data jsonb NOT NULL,
    created_at timestamptz DEFAULT now(),
    PRIMARY KEY(novel_id, number, source_hash)
);
CREATE TABLE IF NOT EXISTS glossaries (
    novel_id text PRIMARY KEY REFERENCES novels(id),
    revision bigint NOT NULL,
    data jsonb NOT NULL
);
CREATE TABLE IF NOT EXISTS glossary_versions (
    novel_id text NOT NULL,
    revision bigint NOT NULL,
    data jsonb NOT NULL,
    created_at timestamptz DEFAULT now(),
    PRIMARY KEY(novel_id, revision)
);
CREATE TABLE IF NOT EXISTS glossary_proposals (
    id bigserial PRIMARY KEY,
    novel_id text NOT NULL,
    job_id text,
    proposal jsonb NOT NULL,
    created_at timestamptz DEFAULT now()
);
CREATE TABLE IF NOT EXISTS jobs (
    id text PRIMARY KEY,
    novel_id text NOT NULL REFERENCES novels(id),
    chapter integer NOT NULL,
    revision integer NOT NULL,
    state text NOT NULL,
    data jsonb NOT NULL,
    updated_at timestamptz DEFAULT now(),
    UNIQUE(novel_id, chapter, revision)
);
CREATE TABLE IF NOT EXISTS ai_calls (
    id text PRIMARY KEY,
    job_id text NOT NULL REFERENCES jobs(id),
    stage text NOT NULL,
    segment integer NOT NULL,
    model text NOT NULL,
    provider text,
    prompt_version text NOT NULL,
    glossary_revision bigint NOT NULL,
    context jsonb NOT NULL,
    estimated_usd numeric NOT NULL,
    actual_usd numeric,
    cost_source text,
    input_tokens bigint,
    output_tokens bigint,
    cached_tokens bigint,
    state text NOT NULL,
    request_id text,
    duration_ms bigint,
    response jsonb,
    created_at timestamptz DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ai_calls_job ON ai_calls(job_id);
CREATE TABLE IF NOT EXISTS job_metrics (
    job_id text PRIMARY KEY REFERENCES jobs(id),
    source_tokens bigint NOT NULL,
    tokenizer text NOT NULL,
    target_usd numeric NOT NULL
);
