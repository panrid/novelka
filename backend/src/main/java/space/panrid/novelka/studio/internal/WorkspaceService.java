package space.panrid.novelka.studio.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.IMAGE;
import static space.panrid.novelka.jooq.Tables.WORKSPACE_ATTACHMENT;
import static space.panrid.novelka.jooq.Tables.WORKSPACE_DOCUMENT;
import static space.panrid.novelka.jooq.Tables.WORKSPACE_LINK;
import static space.panrid.novelka.jooq.Tables.WORKSPACE_REVISION;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.media.Images;
import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.text.Slugs;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Workspace materials: canon, timeline, plans, decisions, retcons, status, notes and attachments (етап 24.2, issue #330).
 */
@Service
public class WorkspaceService {

    public static final Set<String> KINDS = Set.of("canon", "timeline", "plan", "decision", "status", "meta", "note");
    private static final Pattern WIKILINK = Pattern.compile("\\[\\[([^\\]|]+)(?:\\|[^\\]]+)?\\]\\]");
    private static final Pattern CHAPTER_NUM = Pattern.compile("(?:розділ|глава|chapter)\\s*(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final DSLContext db;
    private final Images images;
    private final space.panrid.novelka.account.CurrentUser currentUser;

    WorkspaceService(DSLContext db, Images images, space.panrid.novelka.account.CurrentUser currentUser) {
        this.currentUser = currentUser;
        this.db = db;
        this.images = images;
    }

    public record DocumentSummary(long id, String slug, String title, String kind, long version,
            OffsetDateTime updatedAt, String updatedByNick, String snippet) {
    }

    public record AttachmentItem(long id, long imageId, String url, String name, String description,
            OffsetDateTime createdAt) {
    }

    public record DocumentDetails(long id, String slug, String title, String kind, String content, long version,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, String updatedByNick, int revisionsCount,
            List<String> links, List<AttachmentItem> attachments) {
    }

    public record RevisionItem(long id, long version, String title, String content, String authorNick,
            OffsetDateTime createdAt, String comment) {
    }

    public List<DocumentSummary> list(long editionId, String kind, String query, int offset) {
        Condition cond = WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId);
        if (kind != null && !kind.isBlank() && !kind.equalsIgnoreCase("all")) {
            cond = cond.and(WORKSPACE_DOCUMENT.KIND.eq(kind.toLowerCase()));
        }
        if (query != null && !query.isBlank()) {
            String q = "%" + query.strip().toLowerCase() + "%";
            cond = cond.and(DSL.lower(WORKSPACE_DOCUMENT.TITLE).like(q)
                    .or(DSL.lower(WORKSPACE_DOCUMENT.CONTENT).like(q)));
        }

        var rows = db.select(WORKSPACE_DOCUMENT.ID, WORKSPACE_DOCUMENT.SLUG, WORKSPACE_DOCUMENT.TITLE,
                        WORKSPACE_DOCUMENT.KIND, WORKSPACE_DOCUMENT.VERSION, WORKSPACE_DOCUMENT.UPDATED_AT,
                        ACCOUNT.NICK, WORKSPACE_DOCUMENT.CONTENT)
                .from(WORKSPACE_DOCUMENT)
                .leftJoin(ACCOUNT).on(ACCOUNT.ID.eq(WORKSPACE_DOCUMENT.UPDATED_BY))
                .where(cond)
                .orderBy(WORKSPACE_DOCUMENT.UPDATED_AT.desc(), WORKSPACE_DOCUMENT.ID.desc())
                .limit(20).offset(Math.max(0, offset))
                .fetch();

        return rows.map(r -> new DocumentSummary(
                r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6(), r.value7(),
                snippet(r.value8(), query)
        ));
    }

    public Optional<DocumentDetails> get(long editionId, String slug) {
        var doc = db.select(WORKSPACE_DOCUMENT.ID, WORKSPACE_DOCUMENT.SLUG, WORKSPACE_DOCUMENT.TITLE,
                        WORKSPACE_DOCUMENT.KIND, WORKSPACE_DOCUMENT.CONTENT, WORKSPACE_DOCUMENT.VERSION,
                        WORKSPACE_DOCUMENT.CREATED_AT, WORKSPACE_DOCUMENT.UPDATED_AT, ACCOUNT.NICK)
                .from(WORKSPACE_DOCUMENT)
                .leftJoin(ACCOUNT).on(ACCOUNT.ID.eq(WORKSPACE_DOCUMENT.UPDATED_BY))
                .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId), WORKSPACE_DOCUMENT.SLUG.eq(slug))
                .fetchOptional();

        if (doc.isEmpty()) {
            return Optional.empty();
        }

        long docId = doc.get().value1();
        int revCount = db.fetchCount(WORKSPACE_REVISION, WORKSPACE_REVISION.DOCUMENT_ID.eq(docId));

        List<String> links = db.select(WORKSPACE_LINK.RAW_TARGET)
                .from(WORKSPACE_LINK)
                .where(WORKSPACE_LINK.SOURCE_DOCUMENT_ID.eq(docId))
                .orderBy(WORKSPACE_LINK.ID)
                .fetch(WORKSPACE_LINK.RAW_TARGET);

        List<AttachmentItem> attachments = loadAttachments(editionId, docId);

        return Optional.of(new DocumentDetails(
                docId, doc.get().value2(), doc.get().value3(), doc.get().value4(), doc.get().value5(),
                doc.get().value6(), doc.get().value7(), doc.get().value8(), doc.get().value9(),
                revCount, links, attachments
        ));
    }

    @Transactional
    public DocumentDetails save(long editionId, String slug, String title, String kind, String content,
            Long expectedVersion, Long accountId, String comment) {
        lockEdition(editionId);
        if (title == null || title.isBlank()) {
            throw UserFacingException.badRequest("Назва документа обов’язкова.");
        }
        String cleanTitle = title.strip();
        if (cleanTitle.length() > 300) {
            throw UserFacingException.badRequest("Назва документа задовга: до 300 знаків.");
        }
        String docKind = kind == null || !KINDS.contains(kind.toLowerCase()) ? "note" : kind.toLowerCase();
        String safeContent = content == null ? "" : content;

        String docSlug = slug == null || slug.isBlank() ? Slugs.slug(cleanTitle, 80) : slug.strip().toLowerCase();

        var existing = db.select(WORKSPACE_DOCUMENT.ID, WORKSPACE_DOCUMENT.VERSION,
                        WORKSPACE_DOCUMENT.TITLE, WORKSPACE_DOCUMENT.KIND, WORKSPACE_DOCUMENT.CONTENT)
                .from(WORKSPACE_DOCUMENT)
                .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId), WORKSPACE_DOCUMENT.SLUG.eq(docSlug))
                .fetchOptional();

        long docId;
        long newVersion;
        if (existing.isEmpty()) {
            if (expectedVersion != null) {
                throw UserFacingException.conflict("Документ видалено або його ще не створено. Оновіть список документів.");
            }
            newVersion = 1;
            docId = db.insertInto(WORKSPACE_DOCUMENT)
                    .set(WORKSPACE_DOCUMENT.EDITION_ID, editionId)
                    .set(WORKSPACE_DOCUMENT.SLUG, docSlug)
                    .set(WORKSPACE_DOCUMENT.TITLE, cleanTitle)
                    .set(WORKSPACE_DOCUMENT.KIND, docKind)
                    .set(WORKSPACE_DOCUMENT.CONTENT, safeContent)
                    .set(WORKSPACE_DOCUMENT.VERSION, newVersion)
                    .set(WORKSPACE_DOCUMENT.UPDATED_BY, accountId)
                    .returning(WORKSPACE_DOCUMENT.ID)
                    .fetchSingle(WORKSPACE_DOCUMENT.ID);
        } else {
            long currentVersion = existing.get().value2();
            if (expectedVersion == null || expectedVersion != currentVersion) {
                throw UserFacingException.conflict("Цей документ уже змінено іншим учасником (поточна версія: "
                        + currentVersion + "). Збережіть свій текст, щоб розв'язати конфлікт.");
            }
            docId = existing.get().value1();
            if (Objects.equals(existing.get().value3(), cleanTitle)
                    && Objects.equals(existing.get().value4(), docKind)
                    && Objects.equals(existing.get().value5(), safeContent)
                    && (comment == null || !comment.startsWith("Відновлено з версії"))) {
                syncLinks(editionId, docId, safeContent);
                return get(editionId, docSlug).orElseThrow();
            }
            newVersion = currentVersion + 1;
            db.update(WORKSPACE_DOCUMENT)
                    .set(WORKSPACE_DOCUMENT.TITLE, cleanTitle)
                    .set(WORKSPACE_DOCUMENT.KIND, docKind)
                    .set(WORKSPACE_DOCUMENT.CONTENT, safeContent)
                    .set(WORKSPACE_DOCUMENT.VERSION, newVersion)
                    .set(WORKSPACE_DOCUMENT.UPDATED_AT, DSL.currentOffsetDateTime())
                    .set(WORKSPACE_DOCUMENT.UPDATED_BY, accountId)
                    .where(WORKSPACE_DOCUMENT.ID.eq(docId))
                    .execute();
        }

        db.insertInto(WORKSPACE_REVISION)
                .set(WORKSPACE_REVISION.DOCUMENT_ID, docId)
                .set(WORKSPACE_REVISION.EDITION_ID, editionId)
                .set(WORKSPACE_REVISION.VERSION, newVersion)
                .set(WORKSPACE_REVISION.TITLE, cleanTitle)
                .set(WORKSPACE_REVISION.CONTENT, safeContent)
                .set(WORKSPACE_REVISION.AUTHOR_ID, accountId)
                .set(WORKSPACE_REVISION.COMMENT, comment)
                .execute();

        syncLinks(editionId, docId, safeContent);

        return get(editionId, docSlug).orElseThrow();
    }

    @Transactional
    public void delete(long editionId, String slug) {
        db.deleteFrom(WORKSPACE_DOCUMENT)
                .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId), WORKSPACE_DOCUMENT.SLUG.eq(slug))
                .execute();
    }

    public List<RevisionItem> revisions(long editionId, String slug) {
        Long docId = db.select(WORKSPACE_DOCUMENT.ID).from(WORKSPACE_DOCUMENT)
                .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId), WORKSPACE_DOCUMENT.SLUG.eq(slug))
                .fetchOne(WORKSPACE_DOCUMENT.ID);
        if (docId == null) {
            throw UserFacingException.notFound("Документа не знайдено.");
        }

        return db.select(WORKSPACE_REVISION.ID, WORKSPACE_REVISION.VERSION, WORKSPACE_REVISION.TITLE,
                        WORKSPACE_REVISION.CONTENT, ACCOUNT.NICK, WORKSPACE_REVISION.CREATED_AT,
                        WORKSPACE_REVISION.COMMENT)
                .from(WORKSPACE_REVISION)
                .leftJoin(ACCOUNT).on(ACCOUNT.ID.eq(WORKSPACE_REVISION.AUTHOR_ID))
                .where(WORKSPACE_REVISION.DOCUMENT_ID.eq(docId))
                .orderBy(WORKSPACE_REVISION.VERSION.desc())
                .fetch(r -> new RevisionItem(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6(), r.value7()));
    }

    @Transactional
    public DocumentDetails restoreRevision(long editionId, String slug, long revisionId, Long accountId, Long expectedVersion) {
        lockEdition(editionId);
        var doc = db.select(WORKSPACE_DOCUMENT.ID, WORKSPACE_DOCUMENT.KIND, WORKSPACE_DOCUMENT.VERSION)
                .from(WORKSPACE_DOCUMENT)
                .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId), WORKSPACE_DOCUMENT.SLUG.eq(slug))
                .fetchOptional();
        if (doc.isEmpty()) {
            throw UserFacingException.notFound("Документа не знайдено.");
        }

        var rev = db.select(WORKSPACE_REVISION.TITLE, WORKSPACE_REVISION.CONTENT, WORKSPACE_REVISION.VERSION)
                .from(WORKSPACE_REVISION)
                .where(WORKSPACE_REVISION.ID.eq(revisionId), WORKSPACE_REVISION.DOCUMENT_ID.eq(doc.get().value1()))
                .fetchOptional();
        if (rev.isEmpty()) {
            throw UserFacingException.notFound("Версії не знайдено.");
        }

        String comment = "Відновлено з версії " + rev.get().value3();
        return save(editionId, slug, rev.get().value1(), doc.get().value2(), rev.get().value2(),
                expectedVersion, accountId, comment);
    }

    public List<AttachmentItem> loadAttachments(long editionId, Long documentId) {
        Condition cond = WORKSPACE_ATTACHMENT.EDITION_ID.eq(editionId);
        if (documentId != null) {
            cond = cond.and(WORKSPACE_ATTACHMENT.DOCUMENT_ID.eq(documentId));
        }
        var rows = db.select(WORKSPACE_ATTACHMENT.ID, WORKSPACE_ATTACHMENT.IMAGE_ID,
                        WORKSPACE_ATTACHMENT.NAME, WORKSPACE_ATTACHMENT.DESCRIPTION,
                        WORKSPACE_ATTACHMENT.CREATED_AT)
                .from(WORKSPACE_ATTACHMENT)
                .where(cond)
                .orderBy(WORKSPACE_ATTACHMENT.CREATED_AT.desc())
                .fetch();

        Map<Long, StoredImage> stored = images.findAll(rows.map(r -> r.value2()));
        List<AttachmentItem> items = new ArrayList<>();
        for (var r : rows) {
            long imgId = r.value2();
            String url = stored.containsKey(imgId) ? stored.get(imgId).url(480) : "";
            items.add(new AttachmentItem(r.value1(), imgId, url, r.value3(), r.value4(), r.value5()));
        }
        return items;
    }

    @Transactional
    public AttachmentItem addAttachment(long editionId, String documentSlug, long imageId, String name, String description) {
        images.bindWorkspace(imageId, editionId, currentUser.requireSignedIn().accountId());
        Long docId = null;
        if (documentSlug != null && !documentSlug.isBlank()) {
            docId = db.select(WORKSPACE_DOCUMENT.ID).from(WORKSPACE_DOCUMENT)
                    .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId), WORKSPACE_DOCUMENT.SLUG.eq(documentSlug))
                    .fetchOne(WORKSPACE_DOCUMENT.ID);
        }

        String safeName = name == null || name.isBlank() ? "attachment" : name.strip();
        var existingAtt = db.select(WORKSPACE_ATTACHMENT.ID, WORKSPACE_ATTACHMENT.CREATED_AT)
                .from(WORKSPACE_ATTACHMENT)
                .where(WORKSPACE_ATTACHMENT.EDITION_ID.eq(editionId),
                        WORKSPACE_ATTACHMENT.IMAGE_ID.eq(imageId),
                        WORKSPACE_ATTACHMENT.NAME.equalIgnoreCase(safeName))
                .fetchOptional();
        if (existingAtt.isPresent()) {
            StoredImage stored = images.find(imageId).orElse(null);
            String url = stored != null ? stored.url(480) : "";
            return new AttachmentItem(existingAtt.get().value1(), imageId, url, safeName, description, existingAtt.get().value2());
        }
        long id = db.insertInto(WORKSPACE_ATTACHMENT)
                .set(WORKSPACE_ATTACHMENT.EDITION_ID, editionId)
                .set(WORKSPACE_ATTACHMENT.DOCUMENT_ID, docId)
                .set(WORKSPACE_ATTACHMENT.IMAGE_ID, imageId)
                .set(WORKSPACE_ATTACHMENT.NAME, safeName)
                .set(WORKSPACE_ATTACHMENT.DESCRIPTION, description)
                .returning(WORKSPACE_ATTACHMENT.ID)
                .fetchSingle(WORKSPACE_ATTACHMENT.ID);

        StoredImage stored = images.find(imageId).orElse(null);
        String url = stored != null ? stored.url(480) : "";
        return new AttachmentItem(id, imageId, url, safeName, description, OffsetDateTime.now());
    }

    @Transactional
    public void deleteAttachment(long editionId, long attachmentId) {
        db.deleteFrom(WORKSPACE_ATTACHMENT)
                .where(WORKSPACE_ATTACHMENT.ID.eq(attachmentId), WORKSPACE_ATTACHMENT.EDITION_ID.eq(editionId))
                .execute();
    }

    // ---- API v1 methods (етап 24.3, issue #331) ------------------------------------------------

    public StudioApiV1.Page<StudioApiV1.WorkspaceDocumentSummary> listApiPaged(
            long editionId, String kind, String query, String after, int limit) {
        Condition cond = WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId);
        if (kind != null && !kind.isBlank() && !kind.equalsIgnoreCase("all")) {
            cond = cond.and(WORKSPACE_DOCUMENT.KIND.eq(kind.toLowerCase()));
        }
        if (query != null && !query.isBlank()) {
            String q = "%" + query.strip().toLowerCase() + "%";
            cond = cond.and(DSL.lower(WORKSPACE_DOCUMENT.TITLE).like(q)
                    .or(DSL.lower(WORKSPACE_DOCUMENT.CONTENT).like(q)));
        }
        Long cursorId = null;
        if (after != null && !after.isBlank()) {
            try {
                cursorId = Long.parseLong(after.strip());
            } catch (NumberFormatException e) {
                cursorId = db.select(WORKSPACE_DOCUMENT.ID).from(WORKSPACE_DOCUMENT)
                        .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId), WORKSPACE_DOCUMENT.SLUG.eq(after.strip().toLowerCase()))
                        .fetchOne(WORKSPACE_DOCUMENT.ID);
            }
        }
        if (cursorId != null) {
            cond = cond.and(WORKSPACE_DOCUMENT.ID.lt(cursorId));
        }

        var rows = db.select(WORKSPACE_DOCUMENT.ID, WORKSPACE_DOCUMENT.SLUG, WORKSPACE_DOCUMENT.TITLE,
                        WORKSPACE_DOCUMENT.KIND, WORKSPACE_DOCUMENT.VERSION, WORKSPACE_DOCUMENT.UPDATED_AT,
                        ACCOUNT.NICK, WORKSPACE_DOCUMENT.CONTENT)
                .from(WORKSPACE_DOCUMENT)
                .leftJoin(ACCOUNT).on(ACCOUNT.ID.eq(WORKSPACE_DOCUMENT.UPDATED_BY))
                .where(cond)
                .orderBy(WORKSPACE_DOCUMENT.ID.desc())
                .limit(limit)
                .fetch();

        List<StudioApiV1.WorkspaceDocumentSummary> items = rows.map(r -> new StudioApiV1.WorkspaceDocumentSummary(
                r.value2(), r.value3(), r.value4(), r.value5(), snippet(r.value8(), query), r.value6(), r.value7()
        ));
        String next = rows.size() == limit ? String.valueOf(rows.getLast().value1()) : null;
        return new StudioApiV1.Page<>(items, next);
    }

    public Optional<StudioApiV1.WorkspaceDocument> getApi(long editionId, String slug) {
        return get(editionId, slug).map(d -> new StudioApiV1.WorkspaceDocument(
                d.slug(), d.title(), d.kind(), d.content(), d.version(),
                d.createdAt(), d.updatedAt(), d.updatedByNick(), d.revisionsCount(),
                d.links(), d.attachments().stream().map(a -> new StudioApiV1.WorkspaceAttachment(
                        a.id(), a.imageId(), a.url(), a.name(), a.description(), a.createdAt()
                )).toList()
        ));
    }

    @Transactional
    public StudioApiV1.WorkspaceDocument saveApi(long editionId, String slug, String title, String kind, String content,
            Long expectedVersion, Long accountId, String comment) {
        DocumentDetails saved = save(editionId, slug, title, kind, content, expectedVersion, accountId, comment);
        return getApi(editionId, saved.slug()).orElseThrow();
    }

    public StudioApiV1.Page<StudioApiV1.WorkspaceRevision> revisionsApiPaged(
            long editionId, String slug, String after, int limit) {
        Long docId = db.select(WORKSPACE_DOCUMENT.ID).from(WORKSPACE_DOCUMENT)
                .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId), WORKSPACE_DOCUMENT.SLUG.eq(slug))
                .fetchOne(WORKSPACE_DOCUMENT.ID);
        if (docId == null) {
            throw UserFacingException.notFound("Документа не знайдено.");
        }
        Condition cond = WORKSPACE_REVISION.DOCUMENT_ID.eq(docId);
        if (after != null && !after.isBlank()) {
            try {
                long afterId = Long.parseLong(after.strip());
                cond = cond.and(WORKSPACE_REVISION.ID.lt(afterId));
            } catch (NumberFormatException ignored) {
            }
        }
        var rows = db.select(WORKSPACE_REVISION.ID, WORKSPACE_REVISION.VERSION, WORKSPACE_REVISION.TITLE,
                        WORKSPACE_REVISION.CONTENT, ACCOUNT.NICK, WORKSPACE_REVISION.CREATED_AT,
                        WORKSPACE_REVISION.COMMENT)
                .from(WORKSPACE_REVISION)
                .leftJoin(ACCOUNT).on(ACCOUNT.ID.eq(WORKSPACE_REVISION.AUTHOR_ID))
                .where(cond)
                .orderBy(WORKSPACE_REVISION.ID.desc())
                .limit(limit)
                .fetch();

        List<StudioApiV1.WorkspaceRevision> items = rows.map(r -> new StudioApiV1.WorkspaceRevision(
                r.value1(), r.value2(), r.value3(), r.value4(), r.value5(), r.value6(), r.value7()
        ));
        String next = rows.size() == limit ? String.valueOf(rows.getLast().value1()) : null;
        return new StudioApiV1.Page<>(items, next);
    }

    @Transactional
    public StudioApiV1.WorkspaceDocument restoreRevisionApi(long editionId, String slug, long revisionId, Long accountId, Long expectedVersion) {
        DocumentDetails doc = restoreRevision(editionId, slug, revisionId, accountId, expectedVersion);
        return getApi(editionId, doc.slug()).orElseThrow();
    }

    public List<StudioApiV1.WorkspaceAttachment> attachmentsApi(long editionId, String documentSlug) {
        Long docId = null;
        if (documentSlug != null && !documentSlug.isBlank()) {
            docId = db.select(WORKSPACE_DOCUMENT.ID).from(WORKSPACE_DOCUMENT)
                    .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId), WORKSPACE_DOCUMENT.SLUG.eq(documentSlug))
                    .fetchOne(WORKSPACE_DOCUMENT.ID);
        }
        return loadAttachments(editionId, docId).stream().map(a -> new StudioApiV1.WorkspaceAttachment(
                a.id(), a.imageId(), a.url(), a.name(), a.description(), a.createdAt()
        )).toList();
    }

    @Transactional
    public StudioApiV1.WorkspaceAttachment addAttachmentApi(long editionId, String documentSlug, long imageId, String name, String description) {
        if (imageId <= 0) {
            throw UserFacingException.badRequest("Вкажіть ілюстрацію для вкладення.");
        }
        AttachmentItem item = addAttachment(editionId, documentSlug, imageId, name, description);
        return new StudioApiV1.WorkspaceAttachment(item.id(), item.imageId(), item.url(), item.name(), item.description(), item.createdAt());
    }

    public StudioApiV1.WorkspaceExport exportApi(long editionId, String novel, String team) {
        var slugs = db.select(WORKSPACE_DOCUMENT.SLUG).from(WORKSPACE_DOCUMENT)
                .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId))
                .orderBy(WORKSPACE_DOCUMENT.ID)
                .fetch(WORKSPACE_DOCUMENT.SLUG);
        List<StudioApiV1.WorkspaceDocument> docs = new ArrayList<>();
        for (String slug : slugs) {
            getApi(editionId, slug).ifPresent(docs::add);
        }
        List<StudioApiV1.WorkspaceAttachment> atts = attachmentsApi(editionId, null);
        return new StudioApiV1.WorkspaceExport(novel, team, OffsetDateTime.now(), docs, atts);
    }

    // ---- Batch import / dry-run preview (етап 24.4, issue #332) --------------------------------

    @Transactional
    public StudioApiV1.WorkspaceImportReport importBatch(long editionId, Long accountId, StudioApiV1.WorkspaceImportRequest request) {
        lockEdition(editionId);
        boolean dryRun = request != null && request.dryRun();
        List<StudioApiV1.WorkspaceImportDocInput> docInputs = request == null || request.documents() == null
                ? List.of() : request.documents();
        List<StudioApiV1.WorkspaceAttachmentInput> attInputs = request == null || request.attachments() == null
                ? List.of() : request.attachments();

        // Collect known documents (existing in DB + incoming in this batch) for link resolution
        Map<String, String> titleToSlug = new HashMap<>();
        Set<String> knownSlugs = new LinkedHashSet<>();

        db.select(WORKSPACE_DOCUMENT.SLUG, WORKSPACE_DOCUMENT.TITLE)
                .from(WORKSPACE_DOCUMENT)
                .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId))
                .forEach(r -> {
                    knownSlugs.add(r.value1().toLowerCase());
                    titleToSlug.put(r.value2().toLowerCase(), r.value1());
                    titleToSlug.put(normalizeTargetTitle(r.value2()).toLowerCase(), r.value1());
                });

        Set<Integer> existingChapters = new LinkedHashSet<>(
                db.select(CHAPTER.NUMBER).from(CHAPTER)
                        .where(CHAPTER.EDITION_ID.eq(editionId), CHAPTER.DELETED_AT.isNull())
                        .fetch(CHAPTER.NUMBER)
        );

        Set<String> knownAttachments = new LinkedHashSet<>();
        db.select(WORKSPACE_ATTACHMENT.NAME)
                .from(WORKSPACE_ATTACHMENT)
                .where(WORKSPACE_ATTACHMENT.EDITION_ID.eq(editionId))
                .forEach(r -> knownAttachments.add(r.value1().toLowerCase()));
        for (var att : attInputs) {
            if (att != null && att.name() != null && !att.name().isBlank()) {
                knownAttachments.add(att.name().strip().toLowerCase());
            }
        }

        // Prepare normalized batch docs
        record PreparedDoc(String slug, String title, String kind, String content, String comment, Long version) {
        }
        List<PreparedDoc> prepared = new ArrayList<>();
        for (var in : docInputs) {
            if (in == null || in.title() == null || in.title().isBlank()) {
                throw UserFacingException.badRequest("Кожен документ імпорту повинен мати назву.");
            }
            String cleanTitle = in.title().strip();
            String docSlug = in.slug() == null || in.slug().isBlank()
                    ? Slugs.slug(cleanTitle, 80) : in.slug().strip().toLowerCase();
            String docKind = in.kind() == null || !KINDS.contains(in.kind().toLowerCase())
                    ? "note" : in.kind().toLowerCase();
            String safeContent = in.content() == null ? "" : in.content();
            prepared.add(new PreparedDoc(docSlug, cleanTitle, docKind, safeContent, in.comment(), in.version()));
            knownSlugs.add(docSlug);
            titleToSlug.put(cleanTitle.toLowerCase(), docSlug);
            titleToSlug.put(normalizeTargetTitle(cleanTitle).toLowerCase(), docSlug);
        }

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        List<StudioApiV1.WorkspaceImportDocResult> docResults = new ArrayList<>();
        List<Long> touchedDocIds = new ArrayList<>();
        Map<Long, String> touchedDocContents = new LinkedHashMap<>();

        for (PreparedDoc doc : prepared) {
            var existing = db.select(WORKSPACE_DOCUMENT.ID, WORKSPACE_DOCUMENT.VERSION,
                            WORKSPACE_DOCUMENT.TITLE, WORKSPACE_DOCUMENT.KIND, WORKSPACE_DOCUMENT.CONTENT)
                    .from(WORKSPACE_DOCUMENT)
                    .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId), WORKSPACE_DOCUMENT.SLUG.eq(doc.slug()))
                    .fetchOptional();

            String action;
            long resultVersion;
            if (existing.isEmpty()) {
                if (doc.version() != null) {
                    throw UserFacingException.conflict("Документ видалено після підготовки імпорту. Оновіть список.");
                }
                action = "create";
                resultVersion = 1;
                created++;
                if (!dryRun) {
                    long docId = db.insertInto(WORKSPACE_DOCUMENT)
                            .set(WORKSPACE_DOCUMENT.EDITION_ID, editionId)
                            .set(WORKSPACE_DOCUMENT.SLUG, doc.slug())
                            .set(WORKSPACE_DOCUMENT.TITLE, doc.title())
                            .set(WORKSPACE_DOCUMENT.KIND, doc.kind())
                            .set(WORKSPACE_DOCUMENT.CONTENT, doc.content())
                            .set(WORKSPACE_DOCUMENT.VERSION, 1L)
                            .set(WORKSPACE_DOCUMENT.UPDATED_BY, accountId)
                            .returning(WORKSPACE_DOCUMENT.ID)
                            .fetchSingle(WORKSPACE_DOCUMENT.ID);
                    db.insertInto(WORKSPACE_REVISION)
                            .set(WORKSPACE_REVISION.DOCUMENT_ID, docId)
                            .set(WORKSPACE_REVISION.EDITION_ID, editionId)
                            .set(WORKSPACE_REVISION.VERSION, 1L)
                            .set(WORKSPACE_REVISION.TITLE, doc.title())
                            .set(WORKSPACE_REVISION.CONTENT, doc.content())
                            .set(WORKSPACE_REVISION.AUTHOR_ID, accountId)
                            .set(WORKSPACE_REVISION.COMMENT, doc.comment() != null ? doc.comment() : "Імпорт")
                            .execute();
                    touchedDocIds.add(docId);
                    touchedDocContents.put(docId, doc.content());
                }
            } else {
                long docId = existing.get().value1();
                long currentVer = existing.get().value2();
                boolean same = Objects.equals(existing.get().value3(), doc.title())
                        && Objects.equals(existing.get().value4(), doc.kind())
                        && Objects.equals(existing.get().value5(), doc.content());
                if (same) {
                    action = "unchanged";
                    resultVersion = currentVer;
                    unchanged++;
                    if (!dryRun) {
                        touchedDocIds.add(docId);
                        touchedDocContents.put(docId, doc.content());
                    }
                } else {
                    if (doc.version() == null || doc.version() != currentVer) {
                        throw UserFacingException.conflict("Документ «" + doc.title() + "» уже існує. Для заміни вкажіть його поточну версію.");
                    }
                    action = "update";
                    resultVersion = currentVer + 1;
                    updated++;
                    if (!dryRun) {
                        db.update(WORKSPACE_DOCUMENT)
                                .set(WORKSPACE_DOCUMENT.TITLE, doc.title())
                                .set(WORKSPACE_DOCUMENT.KIND, doc.kind())
                                .set(WORKSPACE_DOCUMENT.CONTENT, doc.content())
                                .set(WORKSPACE_DOCUMENT.VERSION, resultVersion)
                                .set(WORKSPACE_DOCUMENT.UPDATED_AT, DSL.currentOffsetDateTime())
                                .set(WORKSPACE_DOCUMENT.UPDATED_BY, accountId)
                                .where(WORKSPACE_DOCUMENT.ID.eq(docId))
                                .execute();
                        db.insertInto(WORKSPACE_REVISION)
                                .set(WORKSPACE_REVISION.DOCUMENT_ID, docId)
                                .set(WORKSPACE_REVISION.EDITION_ID, editionId)
                                .set(WORKSPACE_REVISION.VERSION, resultVersion)
                                .set(WORKSPACE_REVISION.TITLE, doc.title())
                                .set(WORKSPACE_REVISION.CONTENT, doc.content())
                                .set(WORKSPACE_REVISION.AUTHOR_ID, accountId)
                                .set(WORKSPACE_REVISION.COMMENT, doc.comment() != null ? doc.comment() : "Оновлення при імпорті")
                                .execute();
                        touchedDocIds.add(docId);
                        touchedDocContents.put(docId, doc.content());
                    }
                }
            }

            List<StudioApiV1.WorkspaceLinkMatch> linkMatches = analyzeLinks(
                    doc.slug(), doc.content(), knownSlugs, titleToSlug, existingChapters, knownAttachments);
            docResults.add(new StudioApiV1.WorkspaceImportDocResult(
                    doc.slug(), doc.title(), doc.kind(), action, resultVersion, linkMatches));
        }

        if (!dryRun) {
            // Second pass: sync all links now that all batch documents exist in DB
            for (var entry : touchedDocContents.entrySet()) {
                syncLinks(editionId, entry.getKey(), entry.getValue());
            }
        }

        int attCreated = 0;
        int attUnchanged = 0;
        List<StudioApiV1.WorkspaceImportAttachmentResult> attResults = new ArrayList<>();
        for (var att : attInputs) {
            if (att == null || att.imageId() <= 0) {
                continue;
            }
            images.requireUsable(att.imageId(), editionId, accountId);
            String safeName = att.name() == null || att.name().isBlank() ? "attachment" : att.name().strip();
            boolean exists = db.fetchExists(WORKSPACE_ATTACHMENT,
                    WORKSPACE_ATTACHMENT.EDITION_ID.eq(editionId)
                            .and(WORKSPACE_ATTACHMENT.IMAGE_ID.eq(att.imageId()))
                            .and(WORKSPACE_ATTACHMENT.NAME.equalIgnoreCase(safeName)));
            if (exists) {
                attUnchanged++;
                attResults.add(new StudioApiV1.WorkspaceImportAttachmentResult(
                        safeName, att.imageId(), att.documentSlug(), "unchanged"));
            } else {
                attCreated++;
                if (!dryRun) {
                    addAttachment(editionId, att.documentSlug(), att.imageId(), safeName, att.description());
                }
                attResults.add(new StudioApiV1.WorkspaceImportAttachmentResult(
                        safeName, att.imageId(), att.documentSlug(), "create"));
            }
        }

        return new StudioApiV1.WorkspaceImportReport(
                dryRun, created, updated, unchanged, attCreated, attUnchanged, docResults, attResults);
    }

    /** Serializes all document writers, including create, import and restore, before reading versions. */
    private void lockEdition(long editionId) {
        db.select(EDITION.ID).from(EDITION).where(EDITION.ID.eq(editionId)).forUpdate().fetchSingle();
    }

    private static String wikiText(String content) {
        return content.replaceAll("(?s)```.*?(?:```|$)", "").replaceAll("`[^`]*`", "");
    }

    private List<StudioApiV1.WorkspaceLinkMatch> analyzeLinks(
            String selfSlug, String content, Set<String> knownSlugs, Map<String, String> titleToSlug,
            Set<Integer> existingChapters, Set<String> knownAttachments) {
        Set<String> targets = new LinkedHashSet<>();
        Matcher m = WIKILINK.matcher(wikiText(content));
        while (m.find()) {
            targets.add(m.group(1).strip());
        }
        List<StudioApiV1.WorkspaceLinkMatch> out = new ArrayList<>();
        for (String raw : targets) {
            if (raw.startsWith("#")) {
                out.add(new StudioApiV1.WorkspaceLinkMatch(raw, selfSlug, null, true));
                continue;
            }
            Integer chapterNum = extractChapterNumber(raw);
            if (chapterNum != null && existingChapters.contains(chapterNum)) {
                out.add(new StudioApiV1.WorkspaceLinkMatch(raw, null, chapterNum, true));
                continue;
            }
            String baseName = normalizeTargetTitle(raw);
            String candidateSlug = Slugs.slug(baseName, 80);
            String resolvedSlug = null;
            if (knownSlugs.contains(raw.toLowerCase())) {
                resolvedSlug = raw.toLowerCase();
            } else if (knownSlugs.contains(baseName.toLowerCase())) {
                resolvedSlug = baseName.toLowerCase();
            } else if (titleToSlug.containsKey(raw.toLowerCase())) {
                resolvedSlug = titleToSlug.get(raw.toLowerCase());
            } else if (titleToSlug.containsKey(baseName.toLowerCase())) {
                resolvedSlug = titleToSlug.get(baseName.toLowerCase());
            } else if (knownSlugs.contains(candidateSlug)) {
                resolvedSlug = candidateSlug;
            }
            boolean isAttachment = knownAttachments.contains(raw.toLowerCase())
                    || knownAttachments.contains(baseName.toLowerCase());
            boolean resolved = resolvedSlug != null || (chapterNum != null && existingChapters.contains(chapterNum)) || isAttachment;
            out.add(new StudioApiV1.WorkspaceLinkMatch(raw, resolvedSlug, chapterNum, resolved));
        }
        return out;
    }

    private static String normalizeTargetTitle(String raw) {
        String s = raw == null ? "" : raw.strip();
        int hashIdx = s.indexOf('#');
        if (hashIdx > 0) {
            s = s.substring(0, hashIdx).strip();
        }
        int slashIdx = s.lastIndexOf('/');
        if (slashIdx >= 0 && slashIdx + 1 < s.length()) {
            s = s.substring(slashIdx + 1).strip();
        }
        if (s.toLowerCase().endsWith(".md")) {
            s = s.substring(0, s.length() - 3).strip();
        }
        return s;
    }

    private static Integer extractChapterNumber(String target) {
        Matcher cm = CHAPTER_NUM.matcher(target);
        if (cm.find()) {
            try {
                return Integer.parseInt(cm.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private void syncLinks(long editionId, long docId, String content) {
        db.deleteFrom(WORKSPACE_LINK).where(WORKSPACE_LINK.SOURCE_DOCUMENT_ID.eq(docId)).execute();
        Set<String> targets = new LinkedHashSet<>();
        Matcher m = WIKILINK.matcher(wikiText(content));
        while (m.find()) {
            targets.add(m.group(1).strip());
        }

        for (String target : targets) {
            String baseName = normalizeTargetTitle(target);
            String candidateSlug = Slugs.slug(baseName, 80);
            Long targetDocId = target.startsWith("#") ? docId : db.select(WORKSPACE_DOCUMENT.ID).from(WORKSPACE_DOCUMENT)
                    .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId),
                            WORKSPACE_DOCUMENT.SLUG.eq(target.toLowerCase())
                                    .or(WORKSPACE_DOCUMENT.SLUG.eq(baseName.toLowerCase()))
                                    .or(WORKSPACE_DOCUMENT.SLUG.eq(candidateSlug))
                                    .or(WORKSPACE_DOCUMENT.TITLE.equalIgnoreCase(target))
                                    .or(WORKSPACE_DOCUMENT.TITLE.equalIgnoreCase(baseName)))
                    .limit(1)
                    .fetchOne(WORKSPACE_DOCUMENT.ID);

            Integer chapterNum = extractChapterNumber(target);

            db.insertInto(WORKSPACE_LINK)
                    .set(WORKSPACE_LINK.SOURCE_DOCUMENT_ID, docId)
                    .set(WORKSPACE_LINK.TARGET_DOCUMENT_ID, targetDocId)
                    .set(WORKSPACE_LINK.TARGET_CHAPTER_NUMBER, chapterNum)
                    .set(WORKSPACE_LINK.RAW_TARGET, target)
                    .execute();
        }
    }

    private static String snippet(String content, String query) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String clean = content.replaceAll("[#*`>\\[\\]_-]", " ").replaceAll("\\s+", " ").strip();
        if (query == null || query.isBlank()) {
            return clean.length() > 160 ? clean.substring(0, 157) + "..." : clean;
        }
        int pos = clean.toLowerCase().indexOf(query.toLowerCase());
        if (pos == -1) {
            return clean.length() > 160 ? clean.substring(0, 157) + "..." : clean;
        }
        int start = Math.max(0, pos - 40);
        int end = Math.min(clean.length(), pos + query.length() + 80);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < clean.length() ? "..." : "";
        return prefix + clean.substring(start, end).strip() + suffix;
    }
}
