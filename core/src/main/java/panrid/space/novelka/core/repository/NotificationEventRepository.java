package panrid.space.novelka.core.repository;

import panrid.space.novelka.core.persistence.JdbcSession;

/** Durable events written in the same transaction as the change they describe. */
public final class NotificationEventRepository {
    private final JdbcSession jdbc;

    public NotificationEventRepository(JdbcSession jdbc) { this.jdbc = jdbc; }

    public void chapterPublished(String novel, int chapter) throws Exception {
        add("chapter:" + novel + ":" + chapter, "chapter_published", "READER", novel, chapter, null, null);
    }

    public void glossaryAdded(String novel, long revision, int count) throws Exception {
        if (count > 0) add("glossary:" + novel + ":" + revision, "glossary_added", "ADMIN", novel, null, null, count);
    }

    public void taskFinished(String task, String novel, String state) throws Exception {
        if (java.util.Set.of("complete", "failed", "interrupted").contains(state))
            add("task:" + task + ":" + state, "task_" + state, "ADMIN", novel, null, task, null);
    }

    private void add(String key, String kind, String audience, String novel, Integer chapter, String task, Integer count) throws Exception {
        jdbc.exec("INSERT INTO notifications(event_key,kind,audience,novel_id,chapter,task_id,entry_count)"
                + " VALUES(?,?,?,?,?,?,?) ON CONFLICT(event_key) DO NOTHING", key, kind, audience, novel, chapter, task, count);
    }
}
