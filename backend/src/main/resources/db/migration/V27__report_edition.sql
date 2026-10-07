-- A reader may report a translation or work itself (its text, its cover, what it is about),
-- not only a comment or a message. The reason may name the chapter.
ALTER TABLE report DROP CONSTRAINT report_target_check;
ALTER TABLE report ADD CONSTRAINT report_target_check CHECK (target IN ('comment', 'chat', 'message', 'image', 'edition'));
