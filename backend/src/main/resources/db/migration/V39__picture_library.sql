-- «Мої картинки»: the file a picture was uploaded from (its name can say where it goes:
-- «27-5.png» — chapter 27, after the fifth paragraph) and pictures put away from the library.
-- Putting a picture away keeps it wherever it is already used.
ALTER TABLE image
    ADD COLUMN file_name  TEXT,
    ADD COLUMN removed_at TIMESTAMPTZ;
