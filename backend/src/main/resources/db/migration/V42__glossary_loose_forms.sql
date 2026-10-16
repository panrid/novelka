-- How a word of the glossary is found in the Ukrainian text: by default a short word (up to
-- three letters) only as it is and a capital only as a capital («То» is not «тож»); with
-- loose_forms, as before — any ending and any case («Ян» finds «Яна»).
ALTER TABLE glossary_entry
    ADD COLUMN loose_forms BOOLEAN NOT NULL DEFAULT FALSE;
