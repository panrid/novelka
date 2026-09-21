CREATE TABLE accounts (
    id text PRIMARY KEY,
    username text NOT NULL UNIQUE,
    password_hash text NOT NULL,
    role text NOT NULL CHECK (role IN ('READER','EDITOR','ADMIN','OWNER')),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX accounts_single_owner ON accounts(role) WHERE role='OWNER';
CREATE TABLE audit_events (
    id bigserial PRIMARY KEY,
    actor_id text REFERENCES accounts(id),
    action text NOT NULL,
    target text NOT NULL,
    details jsonb NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE corrections (
    id text PRIMARY KEY,
    author_id text NOT NULL REFERENCES accounts(id),
    novel_id text NOT NULL REFERENCES novels(id),
    chapter integer NOT NULL,
    base_job_id text NOT NULL REFERENCES jobs(id),
    block_index integer NOT NULL CHECK (block_index >= 0),
    block_id text NOT NULL,
    original text NOT NULL,
    replacement text NOT NULL,
    reason text NOT NULL,
    state text NOT NULL DEFAULT 'pending' CHECK (state IN ('pending','approved','rejected')),
    reviewer_id text REFERENCES accounts(id),
    review_note text,
    published_job_id text REFERENCES jobs(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    reviewed_at timestamptz
);
CREATE UNIQUE INDEX corrections_pending_block ON corrections(author_id,base_job_id,block_index) WHERE state='pending';
CREATE INDEX corrections_queue ON corrections(state,created_at);
CREATE TABLE work_origins (
    child_job_id text PRIMARY KEY REFERENCES jobs(id),
    parent_job_id text NOT NULL REFERENCES jobs(id),
    CHECK (child_job_id <> parent_job_id)
);
CREATE TABLE site_settings (
    id integer PRIMARY KEY CHECK (id=1),
    data jsonb NOT NULL
);
CREATE TABLE web_tasks (
    id text PRIMARY KEY,
    actor_id text NOT NULL REFERENCES accounts(id),
    request_key text NOT NULL,
    operation text NOT NULL,
    novel_id text,
    request jsonb NOT NULL,
    settings jsonb NOT NULL,
    state text NOT NULL DEFAULT 'queued',
    message text NOT NULL DEFAULT '',
    current_job_id text,
    cancel_requested boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(actor_id,request_key)
);
CREATE INDEX web_tasks_queue ON web_tasks(state,created_at);
CREATE TABLE web_task_jobs (
    task_id text NOT NULL REFERENCES web_tasks(id),
    job_id text NOT NULL REFERENCES jobs(id),
    initial_usd numeric NOT NULL,
    final_usd numeric,
    PRIMARY KEY(task_id,job_id)
);
