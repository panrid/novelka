-- How the person wants the site and the reader to look (етап 18): {"site": {...}, "reader": {...}}.
-- Only known keys and values get in (Appearance checks them); an empty object means the defaults.
ALTER TABLE account
    ADD COLUMN appearance JSONB NOT NULL DEFAULT '{}';
