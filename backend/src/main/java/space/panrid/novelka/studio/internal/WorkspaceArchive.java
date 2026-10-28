package space.panrid.novelka.studio.internal;

import static space.panrid.novelka.jooq.Tables.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.media.Images;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.databind.json.JsonMapper;

/** A portable, self-contained copy: notes, all manuscript revisions and drafts, and actual files. */
@Component
class WorkspaceArchive {
    private final DSLContext db;
    private final Images images;
    private final WorkspaceService workspace;
    private final JsonMapper json;

    WorkspaceArchive(DSLContext db, Images images, WorkspaceService workspace, JsonMapper json) {
        this.db = db;
        this.images = images;
        this.workspace = workspace;
        this.json = json;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    byte[] export(long editionId) {
        var chapters = db.selectFrom(CHAPTER).where(CHAPTER.EDITION_ID.eq(editionId)).orderBy(CHAPTER.NUMBER).fetch();
        var revisions = db.select(REVISION.fields()).from(REVISION).join(CHAPTER).on(CHAPTER.ID.eq(REVISION.CHAPTER_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId)).orderBy(REVISION.ID).fetch();
        var drafts = db.select(EDITOR_DRAFT.fields()).from(EDITOR_DRAFT).join(CHAPTER).on(CHAPTER.ID.eq(EDITOR_DRAFT.CHAPTER_ID))
                .where(CHAPTER.EDITION_ID.eq(editionId)).orderBy(EDITOR_DRAFT.CHAPTER_ID, EDITOR_DRAFT.ACCOUNT_ID).fetch();
        var notes = workspace.exportApi(editionId, String.valueOf(editionId), "");
        Set<Long> imageIds = new LinkedHashSet<>(notes.attachments().stream().map(StudioApiV1.WorkspaceAttachment::imageId).toList());
        Long cover = db.select(EDITION.COVER_IMAGE_ID).from(EDITION).where(EDITION.ID.eq(editionId)).fetchOne(EDITION.COVER_IMAGE_ID);
        if (cover != null) {
            imageIds.add(cover);
        }
        for (var revision : revisions) {
            collectImages(revision.get(REVISION.BLOCKS).data(), imageIds);
        }
        for (var draft : drafts) {
            collectImages(draft.get(EDITOR_DRAFT.BLOCKS).data(), imageIds);
        }
        Map<String, String> checksums = new LinkedHashMap<>();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            put(zip, "workspace.json", json.writeValueAsBytes(notes), checksums);
            put(zip, "manuscript/chapters.json", json.writeValueAsBytes(chapters.intoMaps()), checksums);
            put(zip, "manuscript/revisions.json", json.writeValueAsBytes(revisions.intoMaps()), checksums);
            put(zip, "manuscript/drafts.json", json.writeValueAsBytes(drafts.intoMaps()), checksums);
            for (var doc : notes.documents()) {
                String safe = doc.slug().replaceAll("[^\\p{L}\\p{N}_.-]", "_");
                put(zip, "documents/" + safe + ".md", doc.content().getBytes(StandardCharsets.UTF_8), checksums);
            }
            for (long id : imageIds) {
                byte[] file = images.original(id).orElseThrow(() -> UserFacingException.notFound("Не знайдено файл вкладення для експорту."));
                put(zip, "files/" + id + ".bin", file, checksums);
            }
            put(zip, "sha256.json", json.writeValueAsBytes(checksums), new LinkedHashMap<>());
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private void collectImages(String blocks, Set<Long> ids) {
        for (var block : json.readTree(blocks)) {
            if (block.hasNonNull("imageId")) {
                ids.add(block.path("imageId").asLong());
            }
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] data, Map<String, String> checksums) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
        try {
            checksums.put(name, java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data)));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
