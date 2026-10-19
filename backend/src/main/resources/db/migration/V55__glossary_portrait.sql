-- A portrait for «Хто це» (етап 20, крок 4): the team picks it in the glossary from «Мої картинки».
-- Readers see the letter of the name until there is one; a hidden picture is not shown.
ALTER TABLE glossary_entry
    ADD COLUMN portrait_image_id BIGINT REFERENCES image (id) ON DELETE SET NULL;
