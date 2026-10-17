-- Hints only (the translation is untouched): an entry may be another name of a main one
-- («дружина Лота», later known by her name; «Міхара Рьо» and «Рьо»). Its words lead to the main
-- entry's card, and «Персонажі» show the main name alone. A main entry never points further.
-- in_story: a character who acts in the story (null — not decided yet, counted as acting);
-- monsters and people only mentioned or thought of stay in the hints, not in «Персонажі».
ALTER TABLE glossary_entry
    ADD COLUMN same_as  BIGINT REFERENCES glossary_entry (id) ON DELETE SET NULL,
    ADD COLUMN in_story BOOLEAN;
