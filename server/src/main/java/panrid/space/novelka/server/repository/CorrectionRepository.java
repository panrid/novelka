package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.server.correction.CorrectionRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CorrectionRepository {
    private final JdbcSession jdbc;

    public CorrectionRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public String create(String author, CorrectionRequest request, String blockId) throws Exception {
        String id = UUID.randomUUID().toString();
        jdbc.exec("INSERT INTO corrections(id,author_id,novel_id,chapter,base_job_id,block_index,block_id,original,replacement,reason)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?)", id, author, request.novelId(), request.chapter(), request.baseJobId(),
                request.blockIndex(), blockId, request.original(), request.replacement(), request.reason());
        return id;
    }

    public Map<String, Object> get(String id, boolean lock) throws Exception {
        var rows = jdbc.rows("SELECT * FROM corrections WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Map<String, Object>> list(String author, int offset) throws Exception {
        return author == null
                ? jdbc.rows("SELECT c.*,a.username author FROM corrections c JOIN accounts a ON a.id=c.author_id"
                        + " ORDER BY (c.state='pending') DESC,c.created_at DESC LIMIT 50 OFFSET ?", offset)
                : jdbc.rows("SELECT c.*,a.username author FROM corrections c JOIN accounts a ON a.id=c.author_id"
                        + " WHERE c.author_id=? ORDER BY c.created_at DESC LIMIT 50 OFFSET ?", author, offset);
    }

    public List<Map<String, Object>> pending(String author, Work work) throws Exception {
        var blocks = work.segments().stream().flatMap(segment -> segment.revised().stream()).toList();
        return jdbc.rows("SELECT c.block_index,c.block_id,c.original,c.replacement FROM corrections c"
                + " JOIN jobs b ON b.id=c.base_job_id WHERE c.author_id=? AND c.novel_id=? AND c.chapter=?"
                + " AND c.state='pending' AND b.data->>'sourceHash'=? ORDER BY c.created_at",
                author, work.novelId(), work.chapter(), work.sourceHash()).stream().filter(row -> {
                    int index = ((Number) row.get("block_index")).intValue();
                    return index >= 0 && index < blocks.size() && blocks.get(index).id().equals(row.get("block_id"))
                            && blocks.get(index).text().equals(row.get("original"));
                }).toList();
    }

    public void review(String id, String reviewer, String state, String note, String publishedJob) throws Exception {
        jdbc.exec("UPDATE corrections SET reviewer_id=?,state=?,review_note=?,published_job_id=?,reviewed_at=now() WHERE id=?",
                reviewer, state, note, publishedJob, id);
    }

    public void lineage(String child, String parent) throws Exception {
        jdbc.exec("INSERT INTO work_origins VALUES(?,?)", child, parent);
    }
}
