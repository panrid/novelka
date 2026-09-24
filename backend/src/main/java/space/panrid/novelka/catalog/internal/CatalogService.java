package space.panrid.novelka.catalog.internal;

import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.NOVEL_TAG;
import static space.panrid.novelka.jooq.Tables.TAG;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.catalog.Catalog;
import space.panrid.novelka.catalog.EditionRef;
import space.panrid.novelka.catalog.NewNovel;
import space.panrid.novelka.platform.text.Slugs;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.databind.json.JsonMapper;

@Service
class CatalogService implements Catalog {

    static final int MAX_TAGS = 12;
    static final int TAG_MAX_LENGTH = 40;
    private static final Set<String> KINDS = Set.of("human", "machine", "mixed", "original");

    private final DSLContext db;
    private final JsonMapper json;

    CatalogService(DSLContext db, JsonMapper json) {
        this.db = db;
        this.json = json;
    }

    @Override
    @Transactional
    public EditionRef createNovel(NewNovel novel) {
        String title = novel.title() == null ? "" : novel.title().strip();
        if (title.isEmpty() || title.length() > 200) {
            throw UserFacingException.badRequest("Назва новели — від 1 до 200 символів.");
        }
        if (!KINDS.contains(novel.kind())) {
            throw UserFacingException.badRequest("Невідомий вид перекладу.");
        }
        String slug = freeSlug(Slugs.slug(title, 60));
        long novelId = db.insertInto(NOVEL)
                .set(NOVEL.SOURCE, novel.kind().equals("original") ? "original" : "manual")
                .set(NOVEL.TITLE, title)
                .set(NOVEL.AUTHOR, novel.author() == null ? "" : novel.author().strip())
                .set(NOVEL.AUTHOR_ACCOUNT_ID, novel.authorAccountId())
                .set(NOVEL.DESCRIPTION, JSONB.valueOf(json.writeValueAsString(novel.description())))
                .set(NOVEL.SLUG, slug)
                .returning(NOVEL.ID)
                .fetchOne(NOVEL.ID);
        setTags(novelId, novel.tags());
        long editionId = db.insertInto(EDITION)
                .set(EDITION.NOVEL_ID, novelId)
                .set(EDITION.TEAM_ID, novel.teamId())
                .set(EDITION.KIND, novel.kind())
                .set(EDITION.ADULT, novel.adult())
                .returning(EDITION.ID)
                .fetchOne(EDITION.ID);
        return new EditionRef(novelId, editionId, slug);
    }

    @Override
    @Transactional
    public void recordPublication(long editionId, int publishedChapters, OffsetDateTime at) {
        db.update(EDITION)
                .set(EDITION.CHAPTER_COUNT, publishedChapters)
                .set(EDITION.LAST_PUBLISHED_AT, at)
                .where(EDITION.ID.eq(editionId))
                .execute();
    }

    private void setTags(long novelId, List<String> names) {
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : names == null ? List.<String>of() : names) {
            String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > TAG_MAX_LENGTH) {
                throw UserFacingException.badRequest("Тег — до %d символів.".formatted(TAG_MAX_LENGTH));
            }
            unique.add(name);
        }
        if (unique.size() > MAX_TAGS) {
            throw UserFacingException.badRequest("До %d тегів на новелу.".formatted(MAX_TAGS));
        }
        for (String name : unique) {
            String key = tagKey(name);
            db.insertInto(TAG).set(TAG.NAME, name).set(TAG.SLUG, key).onConflict(TAG.SLUG).doNothing().execute();
            long tagId = db.select(TAG.ID).from(TAG).where(TAG.SLUG.eq(key)).fetchOne(TAG.ID);
            db.insertInto(NOVEL_TAG).set(NOVEL_TAG.NOVEL_ID, novelId).set(NOVEL_TAG.TAG_ID, tagId)
                    .onConflictDoNothing().execute();
        }
    }

    /** «Фентезі», « фентезі » and «ФЕНТЕЗІ» are one tag. */
    static String tagKey(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC).strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String freeSlug(String base) {
        String candidate = base;
        for (int n = 2; db.fetchExists(NOVEL, NOVEL.SLUG.eq(candidate)); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }
}
