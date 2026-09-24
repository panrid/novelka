package panrid.space.novelka.server.repository;

import panrid.space.novelka.core.persistence.JdbcSession;
import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.server.correction.CorrectionRequest;
import panrid.space.novelka.server.list.ListPage;
import panrid.space.novelka.server.list.ListQuery;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class CorrectionRepository {
    private final JdbcSession jdbc;

    public CorrectionRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    /** New corrections are private drafts until the author submits them as a batch. */
    public String create(String author, CorrectionRequest request, String blockId) throws Exception {
        String id = UUID.randomUUID().toString();
        jdbc.exec("INSERT INTO corrections(id,author_id,novel_id,chapter,base_job_id,block_index,block_id,original,replacement,reason,state)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,'draft')", id, author, request.novelId(), request.chapter(), request.baseJobId(),
                request.blockIndex(), blockId, request.original(), request.replacement(), request.reason());
        return id;
    }

    public String createReplace(String author, String novel, int chapter, String baseJob, String find, String replacement,
            String reason, String scope) throws Exception {
        String id = UUID.randomUUID().toString();
        jdbc.exec("INSERT INTO corrections(id,author_id,novel_id,chapter,base_job_id,block_index,block_id,original,replacement,reason,state,kind,scope)"
                + " VALUES(?,?,?,?,?,0,'',?,?,?,'draft','replace',?)", id, author, novel, chapter, baseJob, find, replacement, reason, scope);
        return id;
    }

    /** The author's unreviewed correction of one paragraph, so a second edit updates it instead of conflicting. */
    public Map<String, Object> openBlock(String author, String novel, int chapter, String blockId) throws Exception {
        var rows = jdbc.rows("SELECT * FROM corrections WHERE author_id=? AND novel_id=? AND chapter=? AND block_id=?"
                + " AND kind='block' AND state IN ('draft','pending') FOR UPDATE", author, novel, chapter, blockId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void update(String id, String replacement, String reason) throws Exception {
        jdbc.exec("UPDATE corrections SET replacement=?,reason=?,updated_at=now() WHERE id=? AND state IN ('draft','pending')", replacement, reason, id);
    }

    public void delete(String id) throws Exception {
        jdbc.exec("DELETE FROM corrections WHERE id=? AND state IN ('draft','pending')", id);
    }

    /** Moves the author's drafts of a novel (optionally one chapter) into review as one batch. */
    public int submit(String author, String novel, Integer chapter, String batch) throws Exception {
        return jdbc.rows("UPDATE corrections SET state='pending',batch_id=?,created_at=now() WHERE author_id=? AND novel_id=?"
                + " AND (?::int IS NULL OR chapter=?) AND state='draft' RETURNING id", batch, author, novel, chapter, chapter).size();
    }

    public List<Map<String, Object>> batch(String batch, boolean lock) throws Exception {
        return jdbc.rows("SELECT * FROM corrections WHERE batch_id=? ORDER BY created_at,id" + (lock ? " FOR UPDATE" : ""), batch);
    }

    public int draftCount(String author, String novel) throws Exception {
        return ((Number) jdbc.rows("SELECT count(*) AS total FROM corrections WHERE author_id=? AND novel_id=? AND state='draft'",
                author, novel).getFirst().get("total")).intValue();
    }

    public Map<String, Object> get(String id, boolean lock) throws Exception {
        var rows = jdbc.rows("SELECT * FROM corrections WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public ListPage<Map<String, Object>> list(String owner, ListQuery query, String state, String novel,
            int chapter, String authorId, String dateFrom, String dateTo) throws Exception {
        if (!state.isEmpty() && !List.of("draft", "pending", "approved", "rejected").contains(state))
            throw new IllegalArgumentException("Невідомий стан правки.");
        if (chapter < 0) throw new IllegalArgumentException("Некоректний номер глави.");
        String fromDate = date(dateFrom);
        String toDate = date(dateTo);
        if (!fromDate.isEmpty() && !toDate.isEmpty() && fromDate.compareTo(toDate) > 0)
            throw new IllegalArgumentException("Початкова дата має бути не пізнішою за кінцеву.");
        fromDate = fromDate.isEmpty() ? "" : fromDate + "T00:00:00Z";
        toDate = toDate.isEmpty() ? "" : LocalDate.parse(toDate).plusDays(1) + "T00:00:00Z";
        String base = """
                WITH correction_list AS (
                    SELECT c.id,c.author_id,a.username author,c.novel_id,
                        COALESCE(NULLIF(n.data->>'titleUk',''),n.data->>'title') novel_title,
                        c.chapter,COALESCE(jsonb_path_query_first(b.data,
                            '$.segments[*].revised[*] ? (@.kind == "heading").text') #>> '{}',ch.data->>'title') chapter_title,
                        c.state,c.created_at,c.reviewed_at,c.original,c.replacement,c.kind,c.scope,c.batch_id
                    FROM corrections c JOIN accounts a ON a.id=c.author_id
                    JOIN novels n ON n.id=c.novel_id JOIN jobs b ON b.id=c.base_job_id
                    LEFT JOIN chapters ch ON ch.novel_id=c.novel_id AND ch.number=c.chapter
                )
                """;
        String where = """
                 WHERE (?::text IS NULL OR author_id=?) AND (?::text IS NOT NULL OR state<>'draft') AND (?='' OR state=?) AND (?='' OR novel_id=?)
                   AND (?=0 OR chapter=?) AND (?='' OR author_id=?)
                   AND (?='' OR created_at>=?::timestamptz) AND (?='' OR created_at<?::timestamptz)
                   AND (?='' OR novel_title ILIKE ? ESCAPE '\\' OR chapter_title ILIKE ? ESCAPE '\\'
                       OR chapter::text LIKE ? ESCAPE '\\' OR author ILIKE ? ESCAPE '\\'
                       OR original ILIKE ? ESCAPE '\\' OR replacement ILIKE ? ESCAPE '\\')
                """;
        Object[] filters = {owner, owner, owner, state, state, novel, novel, chapter, chapter, authorId, authorId,
                fromDate, fromDate, toDate, toDate, query.q(), query.pattern(), query.pattern(), query.pattern(),
                query.pattern(), query.pattern(), query.pattern()};
        long total = ((Number) jdbc.rows(base + "SELECT count(*) total FROM correction_list" + where, filters).getFirst().get("total")).longValue();
        String order = query.order(Map.of("created", "created_at", "state", "state", "chapter", "chapter",
                "novel", "novel_title", "author", "author"), "created", "id");
        var items = jdbc.rows(base + "SELECT id,author_id,author,novel_id,novel_title,chapter,chapter_title,state,created_at,reviewed_at,kind,scope,batch_id"
                + " FROM correction_list" + where + order + " LIMIT ? OFFSET ?", append(filters, query.size(), query.offset()));
        return ListPage.of(items, query, total);
    }

    public Map<String, Object> detail(String id) throws Exception {
        var rows = jdbc.rows("""
                SELECT c.id,c.author_id,a.username author,c.novel_id,c.chapter,c.base_job_id,b.revision base_revision,
                    p.revision published_revision,c.block_index,c.original,c.replacement,c.reason,c.state,c.review_note,c.kind,c.scope,c.batch_id,
                    (SELECT count(*) FROM corrections m WHERE m.batch_id=c.batch_id) AS batch_size,
                    c.created_at,c.reviewed_at
                FROM corrections c JOIN accounts a ON a.id=c.author_id JOIN jobs b ON b.id=c.base_job_id
                LEFT JOIN jobs p ON p.id=c.published_job_id WHERE c.id=?
                """, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Map<String, Object>> authors(String owner, String q) throws Exception {
        var query = new ListQuery(1, 20, q, "", "asc");
        return jdbc.rows("""
                SELECT DISTINCT a.id,a.username FROM corrections c JOIN accounts a ON a.id=c.author_id
                WHERE (?::text IS NULL OR c.author_id=?) AND (?='' OR a.username ILIKE ? ESCAPE '\\')
                ORDER BY a.username,a.id LIMIT 20
                """, owner, owner, query.q(), query.pattern());
    }

    private String date(String value) {
        if (value == null || value.isEmpty()) return "";
        try { return LocalDate.parse(value).toString(); }
        catch (DateTimeParseException error) { throw new IllegalArgumentException("Дата має формат РРРР-ММ-ДД."); }
    }

    private Object[] append(Object[] values, Object... extras) {
        var result = java.util.Arrays.copyOf(values, values.length + extras.length);
        System.arraycopy(extras, 0, result, values.length, extras.length);
        return result;
    }

    public List<Map<String, Object>> pending(String author, Work work) throws Exception {
        var blocks = work.segments().stream().flatMap(segment -> segment.revised().stream()).toList();
        return jdbc.rows("SELECT c.id,c.state,c.block_index,c.block_id,c.original,c.replacement FROM corrections c"
                + " JOIN jobs b ON b.id=c.base_job_id WHERE c.author_id=? AND c.novel_id=? AND c.chapter=?"
                + " AND c.state IN ('draft','pending') AND c.kind='block' AND b.data->>'sourceHash'=? ORDER BY c.created_at",
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
