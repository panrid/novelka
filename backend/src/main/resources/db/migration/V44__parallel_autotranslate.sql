-- Several chapters translate at once (one per novel): the worker serves whoever waited longest,
-- first by person, then by run, so a short run never waits behind someone else's long one.
ALTER TABLE job ADD COLUMN last_step_at TIMESTAMPTZ;
CREATE INDEX job_step_running ON job_step (job_id) WHERE state = 'running';
