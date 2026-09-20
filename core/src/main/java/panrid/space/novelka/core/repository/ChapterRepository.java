package panrid.space.novelka.core.repository;

import panrid.space.novelka.core.model.Chapter;
import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.support.Json;

import java.util.List;
import java.util.Map;

import static panrid.space.novelka.core.support.Hashes.hash;

public final class ChapterRepository {
    private final JdbcSession jdbc;

    public ChapterRepository(JdbcSession jdbc) {
        this.jdbc = jdbc;
    }

    public void save(String novel, Chapter ch) throws Exception {
        String h = hash(ch.blocks());
        jdbc.exec(
                "INSERT INTO chapter_versions(novel_id,number,source_hash,data) VALUES(?,?,?,?::jsonb) ON"
                        + " CONFLICT DO NOTHING",
                novel,
                ch.number(),
                h,
                Json.write(ch));
        jdbc.exec(
                "INSERT INTO chapters VALUES(?,?,?,?::jsonb) ON CONFLICT(novel_id,number) DO UPDATE SET"
                        + " source_hash=excluded.source_hash,data=excluded.data",
                novel,
                ch.number(),
                h,
                Json.write(ch));
    }

    public Chapter chapter(String novel, int number) throws Exception {
        return jdbc.one(
                "SELECT data FROM chapters WHERE novel_id=? AND number=?", Chapter.class, novel, number);
    }

    public boolean exists(String novel, int number) throws Exception {
        return !jdbc.rows("SELECT number FROM chapters WHERE novel_id=? AND number=?", novel, number).isEmpty();
    }

    public List<Map<String, Object>> list(String novel) throws Exception {
        return jdbc.rows("SELECT number,data->>'title' title,source_hash FROM chapters WHERE novel_id=? ORDER BY number", novel);
    }
}
