-- Common list order and per-user filtering paths. ILIKE contains-search deliberately remains unindexed
-- until catalogue/history volume justifies a trigram extension in production.
CREATE INDEX corrections_author_created ON corrections(author_id,created_at DESC,id);
CREATE INDEX web_tasks_created ON web_tasks(created_at DESC,id);
CREATE INDEX jobs_novel_updated ON jobs(novel_id,updated_at DESC,id);
CREATE INDEX ai_calls_created ON ai_calls(created_at DESC,id);
CREATE INDEX glossary_proposals_novel_id ON glossary_proposals(novel_id,id);
CREATE INDEX audit_events_created ON audit_events(created_at DESC,id);
CREATE INDEX accounts_created ON accounts(created_at DESC,id);
