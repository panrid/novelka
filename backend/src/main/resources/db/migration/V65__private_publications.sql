-- Етап 24.1: private publications. A publication is public or private; existing ones keep
-- what they have (public), a new own work starts private (StudioNovels). A private one is read
-- by its team and by the readers its owner invited by nick; nobody else, not even by address.
ALTER TABLE edition
    ADD COLUMN visibility TEXT NOT NULL DEFAULT 'public' CHECK (visibility IN ('public', 'private'));

CREATE INDEX edition_private ON edition (id) WHERE visibility = 'private';

CREATE TABLE edition_reader
(
    edition_id BIGINT      NOT NULL REFERENCES edition (id) ON DELETE CASCADE,
    account_id BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    granted_by BIGINT REFERENCES account (id) ON DELETE SET NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (edition_id, account_id)
);

CREATE INDEX edition_reader_by_account ON edition_reader (account_id);

-- Files under /media/ are looked up by their key, so a file of a private publication is
-- answered only after the check (MediaGate), however old its address is.
CREATE TABLE image_file
(
    key      TEXT PRIMARY KEY,
    image_id BIGINT NOT NULL REFERENCES image (id) ON DELETE CASCADE
);

CREATE INDEX image_file_by_image ON image_file (image_id);

INSERT INTO image_file (key, image_id)
SELECT DISTINCT ON (v.value) v.value, i.id
FROM image i, jsonb_each_text(i.variants) v
ORDER BY v.value, i.id
ON CONFLICT DO NOTHING;
