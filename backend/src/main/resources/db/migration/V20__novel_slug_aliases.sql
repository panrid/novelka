-- A novel's address may be changed (to «mag-vody» from a first machine title); the old ones
-- keep working and send readers and search engines to the new one.
CREATE TABLE novel_slug_alias
(
    slug     TEXT PRIMARY KEY,
    novel_id BIGINT NOT NULL REFERENCES novel (id)
);
