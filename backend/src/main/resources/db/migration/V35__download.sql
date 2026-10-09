-- «Завантажити EPUB» on the novel page: the team may forbid downloading its translation.
ALTER TABLE edition
    ADD COLUMN download_allowed BOOLEAN NOT NULL DEFAULT TRUE;
