-- The site owner lets chosen people autotranslate (етап 19): they run it with their own шаги.
ALTER TABLE account ADD COLUMN autotranslate BOOLEAN NOT NULL DEFAULT FALSE;
