-- A report on a translation may name the chapter. One open report per person, thing and
-- chapter; once a moderator decided, the same person may report it again.
ALTER TABLE report ADD COLUMN chapter_number INT;
ALTER TABLE report DROP CONSTRAINT report_reporter_id_target_target_id_key;
CREATE UNIQUE INDEX report_once_open ON report (reporter_id, target, target_id, COALESCE(chapter_number, 0))
    WHERE state = 'open';
