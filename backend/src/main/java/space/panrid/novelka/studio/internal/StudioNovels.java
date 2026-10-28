package space.panrid.novelka.studio.internal;

import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.IMAGE;
import static space.panrid.novelka.jooq.Tables.NOVEL;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.access.EditionAccess;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.catalog.Catalog;
import space.panrid.novelka.catalog.EditionChanges;
import space.panrid.novelka.catalog.EditionRef;
import space.panrid.novelka.catalog.NewNovel;
import space.panrid.novelka.media.ImageKind;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.studio.NovelChanged;
import space.panrid.novelka.team.Teams;

/**
 * Creating a work and changing its data, one way for the Studio's pages and its API (етап 22.9): the
 * same rights, checks and errors wherever the request comes from. Every change is announced as
 * {@link NovelChanged}.
 */
@Component
class StudioNovels {

    /** What a person may create: an own translation or an own work. */
    static final Set<String> KINDS = Set.of("human", "original");

    /** Languages an original may be in (edition_original). */
    static final Set<String> ORIGINAL_LANGUAGES = Set.of("ja", "en", "zh", "ko");

    /**
     * A new work. {@code team}: its handle, none — the person's own team. {@code rightsConfirmed}:
     * «Маю право перекладати цей текст» (етап 19). {@code titleOriginal} and {@code language}: the
     * name in the original and the language it is in; translations only.
     */
    record Creation(String kind, String title, String slug, String author, List<Block> description, List<String> tags,
            Boolean adult, String team, Boolean rightsConfirmed, String titleOriginal, String language) {
    }

    private final AccessPolicy access;
    private final Teams teams;
    private final Catalog catalog;
    private final Images images;
    private final DSLContext db;
    private final ApplicationEventPublisher events;

    StudioNovels(AccessPolicy access, Teams teams, Catalog catalog, Images images, DSLContext db, ApplicationEventPublisher events) {
        this.access = access;
        this.teams = teams;
        this.catalog = catalog;
        this.images = images;
        this.db = db;
        this.events = events;
    }

    /** Owners and translators of the team create; with no team named — in the person's own one. */
    @Transactional
    EditionRef create(Creation body) {
        Viewer viewer = access.requireSignedIn();
        if (body.kind() == null || !KINDS.contains(body.kind())) {
            throw UserFacingException.badRequest("Оберіть: свій переклад (human) чи свій твір (original).");
        }
        long teamId = body.team() == null || body.team().isBlank()
                ? teams.personalTeam(viewer.accountId())
                : teams.findByHandle(body.team().strip()).orElseThrow(() -> UserFacingException.notFound("Такої команди немає.")).id();
        access.requireTeamTranslator(teamId);
        boolean original = body.kind().equals("original");
        String titleOriginal = original || body.titleOriginal() == null ? "" : body.titleOriginal().strip();
        if (titleOriginal.length() > 300) {
            throw UserFacingException.badRequest("Назва в оригіналі задовга: до 300 знаків.");
        }
        String language = original || body.language() == null ? "" : body.language().strip();
        if (!language.isEmpty() && !ORIGINAL_LANGUAGES.contains(language)) {
            throw UserFacingException.badRequest("Мова оригіналу — ja, en, zh чи ko.");
        }
        String author = original && (body.author() == null || body.author().isBlank()) ? viewer.nick() : body.author();
        EditionRef ref = catalog.createNovel(new NewNovel(body.title(), author,
                body.description() == null ? List.of() : description(body.description()),
                body.tags() == null ? List.of() : body.tags(), body.kind(), Boolean.TRUE.equals(body.adult()), teamId,
                original ? viewer.accountId() : null, null, body.slug()));
        if (!original && Boolean.TRUE.equals(body.rightsConfirmed())) {
            db.update(EDITION).set(EDITION.RIGHTS_CONFIRMED_AT, DSL.currentOffsetDateTime()).where(EDITION.ID.eq(ref.editionId())).execute();
        }
        if (!titleOriginal.isEmpty()) {
            db.update(NOVEL).set(NOVEL.TITLE_ORIGINAL, titleOriginal).where(NOVEL.ID.eq(ref.novelId())).execute();
        }
        // The glossary keeps the original's forms per language: the first pasted chapter needs no guess.
        if (!language.isEmpty()) {
            db.update(NOVEL).set(NOVEL.SOURCE_LANGUAGE, language).where(NOVEL.ID.eq(ref.novelId())).execute();
        }
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("kind", body.kind());
        created.put("title", body.title() == null ? null : body.title().strip());
        created.put("slug", ref.novelSlug());
        created.put("team", teams.find(teamId).map(team -> team.handle()).orElse(null));
        created.put("adult", Boolean.TRUE.equals(body.adult()));
        events.publishEvent(new NovelChanged(ref.editionId(), viewer.accountId(), NovelChanged.Action.CREATED, created));
        return ref;
    }

    /**
     * The owner changes the work's data and state; {@code slug}, when sent, is checked first, so a
     * refused address leaves everything as it was.
     */
    @Transactional
    EditionAccess update(long editionId, EditionChanges changes, String slug) {
        EditionAccess who = access.requireEditionOwner(editionId);
        if (slug != null) {
            changeSlug(who, slug);
        }
        catalog.updateEdition(editionId, changes);
        Map<String, Object> changed = new LinkedHashMap<>();
        put(changed, "title", changes.title());
        put(changed, "author", changes.author());
        put(changed, "description", changes.description());
        put(changed, "tags", changes.tags());
        put(changed, "status", changes.status());
        put(changed, "pausedUntil", changes.pausedUntil());
        put(changed, "adult", changes.adult());
        put(changed, "facts", changes.facts());
        put(changed, "downloadAllowed", changes.downloadAllowed());
        put(changed, "visibility", changes.visibility());
        if (!changed.isEmpty()) {
            events.publishEvent(new NovelChanged(editionId, who.viewer().accountId(), NovelChanged.Action.UPDATED, changed));
        }
        return who;
    }

    /**
     * The novel's address (етап 17). It is the novel's, not one team's: the owner changes it while
     * theirs is the only translation, the site's staff always; the old address keeps working.
     */
    @Transactional
    String changeSlug(long editionId, String slug) {
        return changeSlug(access.requireEditionOwner(editionId), slug);
    }

    private String changeSlug(EditionAccess who, String slug) {
        long novelId = db.select(EDITION.NOVEL_ID).from(EDITION).where(EDITION.ID.eq(who.editionId())).fetchSingle(EDITION.NOVEL_ID);
        boolean staff = who.viewer().role() == SiteRole.OWNER || who.viewer().role() == SiteRole.ADMIN;
        if (!staff && db.fetchCount(EDITION, EDITION.NOVEL_ID.eq(novelId)) > 1) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "У новели кілька перекладів, тож адресу змінює адміністрація сайту.");
        }
        String before = db.select(NOVEL.SLUG).from(NOVEL).where(NOVEL.ID.eq(novelId)).fetchSingle(NOVEL.SLUG);
        String now = catalog.changeSlug(who.editionId(), slug);
        if (!now.equals(before)) {
            Map<String, Object> changed = new LinkedHashMap<>();
            changed.put("from", before);
            changed.put("slug", now);
            events.publishEvent(new NovelChanged(who.editionId(), who.viewer().accountId(), NovelChanged.Action.SLUG, changed));
        }
        return now;
    }

    /** A cover the person uploaded before (as on the site), or none: the generated one shows then. */
    @Transactional
    void setCover(long editionId, Long imageId) {
        EditionAccess who = access.requireEditionOwner(editionId);
        if (imageId != null) {
            boolean own = db.fetchExists(IMAGE, IMAGE.ID.eq(imageId).and(IMAGE.KIND.eq(ImageKind.COVER.code()))
                    .and(IMAGE.OWNER_ACCOUNT_ID.eq(who.viewer().accountId())).and(IMAGE.HIDDEN_AT.isNull()));
            if (!own) {
                throw UserFacingException.badRequest("Спершу завантажте обкладинку.");
            }
        }
        cover(who, imageId);
    }

    /** A new cover from a file: JPEG, PNG or WebP up to 5 MB, cropped to 2:3 at the centre. */
    @Transactional
    StoredImage uploadCover(long editionId, byte[] content, String fileName) {
        EditionAccess who = access.requireEditionOwner(editionId);
        StoredImage stored = images.store(who.viewer().accountId(), ImageKind.COVER, content, fileName);
        cover(who, stored.id());
        return stored;
    }

    private void cover(EditionAccess who, Long imageId) {
        if (imageId != null) {
            images.requireUsable(imageId, who.editionId(), who.viewer().accountId());
        }
        catalog.setCover(who.editionId(), imageId);
        Map<String, Object> changed = new LinkedHashMap<>();
        changed.put("cover", imageId);
        events.publishEvent(new NovelChanged(who.editionId(), who.viewer().accountId(), NovelChanged.Action.COVER, changed));
    }

    /** A description is paragraphs only, up to 3000 characters. */
    static List<Block> description(List<Block> blocks) {
        List<Block> paragraphs = blocks.stream().filter(block -> block.type().equals("paragraph"))
                .map(block -> Block.paragraph(block.id(), block.content())).toList();
        if (paragraphs.isEmpty()) {
            return List.of();
        }
        List<Block> normalized = space.panrid.novelka.text.BlockRules.normalize(paragraphs);
        if (normalized.stream().mapToInt(block -> block.text().length()).sum() > 3000) {
            throw UserFacingException.badRequest("Опис — до 3000 знаків.");
        }
        return normalized;
    }

    private static void put(Map<String, Object> changed, String field, Object value) {
        if (value != null) {
            changed.put(field, value);
        }
    }
}
