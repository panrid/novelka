-- Editorial queue by date, and drill-down to one novel/chapter without scanning all corrections.
CREATE INDEX corrections_created ON corrections(created_at DESC,id);
CREATE INDEX corrections_novel_chapter_created ON corrections(novel_id,chapter,created_at DESC,id);
