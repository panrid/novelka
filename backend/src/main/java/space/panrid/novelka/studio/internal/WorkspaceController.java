package space.panrid.novelka.studio.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.access.EditionAccess;
import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Controller for workspace materials in Studio: canon, timeline, plans, decisions, retcons,
 * status, notes, revisions and attachments (етап 24.2, issue #330).
 */
@RestController
@RequestMapping("/api/studio/editions/{editionId}/workspace")
class WorkspaceController {

    record SaveRequest(String title, String kind, String content, Long version, String comment) {
    }

    record AddAttachmentRequest(String documentSlug, long imageId, String name, String description) {
    }

    private final AccessPolicy access;
    private final WorkspaceService workspace;

    WorkspaceController(AccessPolicy access, WorkspaceService workspace) {
        this.access = access;
        this.workspace = workspace;
    }

    @GetMapping("/documents")
    List<WorkspaceService.DocumentSummary> list(@PathVariable long editionId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String q) {
        access.requireTextEditor(editionId);
        return workspace.list(editionId, kind, q);
    }

    @GetMapping("/documents/{slug}")
    WorkspaceService.DocumentDetails get(@PathVariable long editionId, @PathVariable String slug) {
        access.requireTextEditor(editionId);
        return workspace.get(editionId, slug)
                .orElseThrow(() -> UserFacingException.notFound("Документа не знайдено."));
    }

    @PutMapping("/documents/{slug}")
    WorkspaceService.DocumentDetails save(@PathVariable long editionId, @PathVariable String slug,
            @RequestBody SaveRequest body) {
        EditionAccess who = access.requireTextEditor(editionId);
        return workspace.save(editionId, slug, body.title(), body.kind(), body.content(),
                body.version(), who.viewer().accountId(), body.comment());
    }

    @DeleteMapping("/documents/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long editionId, @PathVariable String slug) {
        access.requireTextEditor(editionId);
        workspace.delete(editionId, slug);
    }

    @GetMapping("/documents/{slug}/revisions")
    List<WorkspaceService.RevisionItem> revisions(@PathVariable long editionId, @PathVariable String slug) {
        access.requireTextEditor(editionId);
        return workspace.revisions(editionId, slug);
    }

    @PostMapping("/documents/{slug}/revisions/{revisionId}/restore")
    WorkspaceService.DocumentDetails restore(@PathVariable long editionId, @PathVariable String slug,
            @PathVariable long revisionId) {
        EditionAccess who = access.requireTextEditor(editionId);
        return workspace.restoreRevision(editionId, slug, revisionId, who.viewer().accountId());
    }

    @GetMapping("/attachments")
    List<WorkspaceService.AttachmentItem> attachments(@PathVariable long editionId,
            @RequestParam(required = false) String documentSlug) {
        access.requireTextEditor(editionId);
        Long docId = null;
        if (documentSlug != null && !documentSlug.isBlank()) {
            docId = workspace.get(editionId, documentSlug).map(WorkspaceService.DocumentDetails::id).orElse(null);
        }
        return workspace.loadAttachments(editionId, docId);
    }

    @PostMapping("/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    WorkspaceService.AttachmentItem addAttachment(@PathVariable long editionId,
            @RequestBody AddAttachmentRequest body) {
        access.requireTextEditor(editionId);
        return workspace.addAttachment(editionId, body.documentSlug(), body.imageId(), body.name(), body.description());
    }

    @DeleteMapping("/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAttachment(@PathVariable long editionId, @PathVariable long id) {
        access.requireTextEditor(editionId);
        workspace.deleteAttachment(editionId, id);
    }

    @PostMapping("/import")
    StudioApiV1.WorkspaceImportReport importWorkspace(@PathVariable long editionId,
            @RequestBody StudioApiV1.WorkspaceImportRequest body) {
        EditionAccess who = access.requireTextEditor(editionId);
        return workspace.importBatch(editionId, who.viewer().accountId(), body);
    }

    @GetMapping("/export")
    StudioApiV1.WorkspaceExport exportWorkspace(@PathVariable long editionId) {
        access.requireTextEditor(editionId);
        return workspace.exportApi(editionId, String.valueOf(editionId), "");
    }
}
