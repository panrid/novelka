-- Who tells the chapter in the first person, as analysis found it. A first-person narrator is
-- rarely named in their own narration, so the glossary alone never tells the translation their
-- gender; the translation and the proofreading get it from here.
ALTER TABLE chapter_analysis
    ADD COLUMN narrator        TEXT CHECK (narrator IS NULL OR char_length(narrator) <= 100),
    ADD COLUMN narrator_gender TEXT CHECK (narrator_gender IS NULL OR narrator_gender IN ('male', 'female', 'unknown'));
