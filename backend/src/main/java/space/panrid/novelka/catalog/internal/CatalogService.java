package space.panrid.novelka.catalog.internal;

import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.NOVEL_TAG;
import static space.panrid.novelka.jooq.Tables.TAG;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.catalog.Catalog;
import space.panrid.novelka.catalog.EditionChanges;
import space.panrid.novelka.catalog.EditionData;
import space.panrid.novelka.catalog.EditionRef;
import space.panrid.novelka.catalog.ImportedNovel;
import space.panrid.novelka.catalog.NewNovel;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Slugs;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class CatalogService implements Catalog {

    static final int MAX_TAGS = 12;
    static final int TAG_MAX_LENGTH = 40;
    private static final Set<String> KINDS = Set.of("human", "machine", "mixed", "original");
    private static final Set<String> STATUSES = Set.of("ongoing", "completed", "paused", "abandoned");
    private static final TypeReference<List<Block>> BLOCKS = new TypeReference<>() { };

    private final DSLContext db;
    private final JsonMapper json;

    CatalogService(DSLContext db, JsonMapper json) {
        this.db = db;
        this.json = json;
    }

    @Override
    @Transactional
    public EditionRef createNovel(NewNovel novel) {
        String title = title(novel.title() == null ? "" : novel.title());
        if (novel.kind() == null || !KINDS.contains(novel.kind())) {
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
    public EditionRef importNovel(ImportedNovel novel) {
        Long novelId = novelBySource(novel.sourceKey()).orElse(null);
        String slug;
        if (novelId == null) {
            String title = title(novel.title());
            slug = freeSlug(Slugs.slug(title, 60));
            novelId = db.insertInto(NOVEL)
                    .set(NOVEL.SOURCE, "syosetu")
                    .set(NOVEL.SOURCE_KEY, novel.sourceKey())
                    .set(NOVEL.SOURCE_URL, novel.sourceUrl())
                    .set(NOVEL.TITLE_ORIGINAL, novel.titleOriginal())
                    .set(NOVEL.AUTHOR_ORIGINAL, novel.authorOriginal())
                    .set(NOVEL.TITLE, title)
                    .set(NOVEL.AUTHOR, novel.author() == null ? "" : novel.author().strip())
                    .set(NOVEL.DESCRIPTION, JSONB.valueOf(json.writeValueAsString(novel.description())))
                    .set(NOVEL.SOURCE_CHAPTER_COUNT, novel.sourceChapterCount())
                    .set(NOVEL.SLUG, slug)
                    .returning(NOVEL.ID)
                    .fetchOne(NOVEL.ID);
        } else {
            db.update(NOVEL).set(NOVEL.SOURCE_CHAPTER_COUNT, novel.sourceChapterCount()).where(NOVEL.ID.eq(novelId)).execute();
            slug = db.select(NOVEL.SLUG).from(NOVEL).where(NOVEL.ID.eq(novelId)).fetchOne(NOVEL.SLUG);
        }
        Long editionId = db.select(EDITION.ID).from(EDITION)
                .where(EDITION.NOVEL_ID.eq(novelId), EDITION.TEAM_ID.eq(novel.teamId())).fetchOne(EDITION.ID);
        if (editionId == null) {
            editionId = db.insertInto(EDITION)
                    .set(EDITION.NOVEL_ID, novelId)
                    .set(EDITION.TEAM_ID, novel.teamId())
                    .set(EDITION.KIND, "machine")
                    .set(EDITION.ADULT, novel.adult())
                    .returning(EDITION.ID)
                    .fetchOne(EDITION.ID);
        }
        return new EditionRef(novelId, editionId, slug);
    }

    @Override
    public Optional<Long> novelBySource(String sourceKey) {
        return db.select(NOVEL.ID).from(NOVEL).where(NOVEL.SOURCE_KEY.eq(sourceKey)).fetchOptional(NOVEL.ID);
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

    @Override
    public Optional<EditionData> edition(long editionId) {
        Record row = db.select(EDITION.ID, NOVEL.ID, NOVEL.SLUG, EDITION.TEAM_ID, DSL.coalesce(EDITION.TITLE, NOVEL.TITLE),
                        NOVEL.AUTHOR, DSL.coalesce(EDITION.DESCRIPTION, NOVEL.DESCRIPTION), EDITION.KIND, EDITION.STATUS,
                        EDITION.ADULT, EDITION.COVER_IMAGE_ID, EDITION.CHAPTER_COUNT, NOVEL.SOURCE)
                .from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                .where(EDITION.ID.eq(editionId))
                .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        long novelId = row.get(NOVEL.ID);
        List<String> tags = db.select(TAG.NAME).from(NOVEL_TAG).join(TAG).on(TAG.ID.eq(NOVEL_TAG.TAG_ID))
                .where(NOVEL_TAG.NOVEL_ID.eq(novelId)).orderBy(TAG.NAME).fetch(TAG.NAME);
        return Optional.of(new EditionData(row.get(EDITION.ID), novelId, row.get(NOVEL.SLUG), row.get(EDITION.TEAM_ID),
                row.get(4, String.class), row.get(NOVEL.AUTHOR),
                json.readValue(row.get(6, JSONB.class).data(), BLOCKS), tags, row.get(EDITION.KIND),
                row.get(EDITION.STATUS), row.get(EDITION.ADULT), row.get(EDITION.COVER_IMAGE_ID),
                row.get(EDITION.CHAPTER_COUNT), ownNovel(novelId, row.get(NOVEL.SOURCE))));
    }

    @Override
    @Transactional
    public void updateEdition(long editionId, EditionChanges changes) {
        EditionData current = edition(editionId).orElseThrow(() -> UserFacingException.notFound("Такої новели немає."));
        if (changes.status() != null && !STATUSES.contains(changes.status())) {
            throw UserFacingException.badRequest("Невідомий стан перекладу.");
        }
        String title = changes.title() == null ? null : title(changes.title());
        String description = changes.description() == null ? null : json.writeValueAsString(changes.description());
        if (current.ownNovel()) {
            Map<Field<?>, Object> novel = new HashMap<>();
            if (title != null) {
                novel.put(NOVEL.TITLE, title);
            }
            if (changes.author() != null) {
                novel.put(NOVEL.AUTHOR, changes.author().strip());
            }
            if (description != null) {
                novel.put(NOVEL.DESCRIPTION, JSONB.valueOf(description));
            }
            if (!novel.isEmpty()) {
                db.update(NOVEL).set(novel).where(NOVEL.ID.eq(current.novelId())).execute();
            }
            if (changes.tags() != null) {
                db.deleteFrom(NOVEL_TAG).where(NOVEL_TAG.NOVEL_ID.eq(current.novelId())).execute();
                setTags(current.novelId(), changes.tags());
            }
        }
        Map<Field<?>, Object> edition = new HashMap<>();
        if (!current.ownNovel() && title != null) {
            edition.put(EDITION.TITLE, title);
        }
        if (!current.ownNovel() && description != null) {
            edition.put(EDITION.DESCRIPTION, JSONB.valueOf(description));
        }
        if (changes.status() != null) {
            edition.put(EDITION.STATUS, changes.status());
        }
        if (changes.adult() != null) {
            edition.put(EDITION.ADULT, changes.adult());
        }
        if (!edition.isEmpty()) {
            db.update(EDITION).set(edition).where(EDITION.ID.eq(editionId)).execute();
        }
    }

    @Override
    @Transactional
    public void setCover(long editionId, Long imageId) {
        db.update(EDITION).set(EDITION.COVER_IMAGE_ID, imageId).where(EDITION.ID.eq(editionId)).execute();
    }

    /** Entered on the site (not imported) and with only this one edition. */
    private boolean ownNovel(long novelId, String source) {
        return !source.equals("syosetu") && db.fetchCount(EDITION, EDITION.NOVEL_ID.eq(novelId)) == 1;
    }

    private static String title(String raw) {
        String title = raw.strip();
        if (title.isEmpty() || title.length() > 200) {
            throw UserFacingException.badRequest("Назва новели — від 1 до 200 символів.");
        }
        return title;
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

    @Override
    @Transactional
    public void hideEdition(long editionId, long adminId, String reason) {
        int changed = db.update(EDITION).set(EDITION.HIDDEN_AT, DSL.currentOffsetDateTime()).set(EDITION.HIDDEN_BY, adminId)
                .set(EDITION.HIDDEN_REASON, reason).where(EDITION.ID.eq(editionId), EDITION.HIDDEN_AT.isNull()).execute();
        if (changed == 0 && !db.fetchExists(EDITION, EDITION.ID.eq(editionId))) {
            throw UserFacingException.notFound("Такої новели немає.");
        }
    }

    @Override
    @Transactional
    public void restoreEdition(long editionId) {
        db.update(EDITION).setNull(EDITION.HIDDEN_AT).setNull(EDITION.HIDDEN_BY).setNull(EDITION.HIDDEN_REASON)
                .where(EDITION.ID.eq(editionId)).execute();
    }
}
