package space.panrid.novelka.studio.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.IMAGE;
import static space.panrid.novelka.jooq.Tables.WORKSPACE_ATTACHMENT;
import static space.panrid.novelka.jooq.Tables.WORKSPACE_DOCUMENT;
import static space.panrid.novelka.jooq.Tables.WORKSPACE_LINK;
import static space.panrid.novelka.jooq.Tables.WORKSPACE_REVISION;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final Pattern CHAPTER_NUM = Pattern.compile("(?:розділ|глава|chapter)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private final DSLContext db;
    private final Images images;

    WorkspaceService(DSLContext db, Images images) {
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

    public List<DocumentSummary> list(long editionId, String kind, String query) {
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
                .orderBy(WORKSPACE_DOCUMENT.UPDATED_AT.desc())
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

        var existing = db.select(WORKSPACE_DOCUMENT.ID, WORKSPACE_DOCUMENT.VERSION)
                .from(WORKSPACE_DOCUMENT)
                .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId), WORKSPACE_DOCUMENT.SLUG.eq(docSlug))
                .fetchOptional();

        long docId;
        long newVersion;
        if (existing.isEmpty()) {
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
            if (expectedVersion != null && expectedVersion != currentVersion) {
                throw UserFacingException.conflict("Цей документ уже змінено іншим учасником (поточна версія: "
                        + currentVersion + "). Збережіть свій текст, щоб розв'язати конфлікт.");
            }
            newVersion = currentVersion + 1;
            docId = existing.get().value1();
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
    public DocumentDetails restoreRevision(long editionId, String slug, long revisionId, Long accountId) {
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
                doc.get().value3(), accountId, comment);
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
        Long docId = null;
        if (documentSlug != null && !documentSlug.isBlank()) {
            docId = db.select(WORKSPACE_DOCUMENT.ID).from(WORKSPACE_DOCUMENT)
                    .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId), WORKSPACE_DOCUMENT.SLUG.eq(documentSlug))
                    .fetchOne(WORKSPACE_DOCUMENT.ID);
        }

        String safeName = name == null || name.isBlank() ? "attachment" : name.strip();
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

    private void syncLinks(long editionId, long docId, String content) {
        db.deleteFrom(WORKSPACE_LINK).where(WORKSPACE_LINK.SOURCE_DOCUMENT_ID.eq(docId)).execute();
        Set<String> targets = new LinkedHashSet<>();
        Matcher m = WIKILINK.matcher(content);
        while (m.find()) {
            targets.add(m.group(1).strip());
        }

        for (String target : targets) {
            Long targetDocId = db.select(WORKSPACE_DOCUMENT.ID).from(WORKSPACE_DOCUMENT)
                    .where(WORKSPACE_DOCUMENT.EDITION_ID.eq(editionId),
                            WORKSPACE_DOCUMENT.SLUG.eq(target.toLowerCase())
                                    .or(WORKSPACE_DOCUMENT.TITLE.equalIgnoreCase(target)))
                    .fetchOne(WORKSPACE_DOCUMENT.ID);

            Integer chapterNum = null;
            Matcher cm = CHAPTER_NUM.matcher(target);
            if (cm.find()) {
                try {
                    chapterNum = Integer.parseInt(cm.group(1));
                } catch (NumberFormatException ignored) {
                }
            }

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
