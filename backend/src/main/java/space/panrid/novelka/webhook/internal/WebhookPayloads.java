package space.panrid.novelka.webhook.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.REVISION;
import static space.panrid.novelka.jooq.Tables.TEAM;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.SiteProperties;

/**
 * What a webhook's {@code data} holds (етап 22.5), in the manner of {@code /api/v1}: changed only
 * by adding fields; a novel is its {@code slug}, a translation its team's {@code team} handle; never
 * the original's text, nor a draft. A run of autotranslation is told by its number ({@code run}),
 * the one the Studio's journal of runs uses.
 */
@Component
class WebhookPayloads {

    /** At most this many chapters are listed in one event; {@code first} and {@code last} still tell the whole range. */
    static final int MAX_CHAPTERS = 100;

    /** @param url the translation's page on the site */
    record Translation(String novel, String team, String title, String url) {
    }

    /**
     * @param label the number readers see when it is not the position («12.5», «Екстра»); null shows {@code number}
     * @param url   the chapter in the reader
     */
    record Chapter(int number, String label, String title, String url) {
    }

    /** {@code chapter.published}: chapters readers can open for the first time. */
    record ChaptersPublished(Translation translation, int first, int last, List<Chapter> chapters) {
    }

    /**
     * {@code run.finished} and {@code run.stopped}.
     *
     * @param state done, failed (an error stopped it; it may be continued) or cancelled
     * @param error what stopped it, for a failed run
     */
    record Run(Translation translation, long run, String state, int first, int last, String error) {
    }

    /** {@code suggestion.created}: a reader sent {@code count} suggestions to the team. */
    record Suggestions(Translation translation, String author, int count) {
    }

    /** {@code source.chapters}: the original's site has chapters {@code first}..{@code last} to translate. */
    record SourceChapters(Translation translation, int first, int last) {
    }

    /** {@code ping}: «Надіслати пробну подію». */
    record Ping(String message) {
    }

    private final DSLContext db;
    private final SiteProperties site;

    WebhookPayloads(DSLContext db, SiteProperties site) {
        this.db = db;
        this.site = site;
    }

    Translation translation(long editionId) {
        Record row = db.select(NOVEL.SLUG, TEAM.HANDLE, DSL.coalesce(EDITION.TITLE, NOVEL.TITLE))
                .from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .where(EDITION.ID.eq(editionId)).fetchSingle();
        String slug = row.get(NOVEL.SLUG);
        String team = row.get(TEAM.HANDLE);
        return new Translation(slug, team, row.get(2, String.class), site.link("/n/" + slug + "?t=" + team));
    }

    ChaptersPublished chaptersPublished(long editionId, int first, int last) {
        Translation translation = translation(editionId);
        List<Chapter> chapters = db.select(CHAPTER.NUMBER, CHAPTER.LABEL, REVISION.TITLE).from(CHAPTER)
                .join(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.NUMBER.between(first, last), CHAPTER.DELETED_AT.isNull())
                .orderBy(CHAPTER.NUMBER)
                .limit(MAX_CHAPTERS)
                .fetch(r -> new Chapter(r.get(CHAPTER.NUMBER), r.get(CHAPTER.LABEL), r.get(REVISION.TITLE),
                        site.link("/n/" + translation.novel() + "/" + r.get(CHAPTER.NUMBER) + "?t=" + translation.team())));
        return new ChaptersPublished(translation, first, last, chapters);
    }

    Run run(long editionId, long jobId, String state, int first, int last, String error) {
        return new Run(translation(editionId), jobId, state, first, last, error);
    }

    Suggestions suggestions(long editionId, long authorId, int count) {
        String author = db.select(ACCOUNT.NICK).from(ACCOUNT).where(ACCOUNT.ID.eq(authorId)).fetchOne(ACCOUNT.NICK);
        return new Suggestions(translation(editionId), author, count);
    }

    SourceChapters sourceChapters(long editionId, int first, int last) {
        return new SourceChapters(translation(editionId), first, last);
    }
}
