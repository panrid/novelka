package space.panrid.novelka.studio.internal;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.access.EditionAccess;
import space.panrid.novelka.platform.web.Idempotency;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.databind.json.JsonMapper;

/**
 * REST API for workspace materials (canon, timeline, plans, decisions, retcons, status, notes,
 * revisions and attachments) under {@code /api/v1/studio/novels/{novel}/translations/{team}/workspace}
 * (етап 24.3, issue #331).
 */
@RestController
@RequestMapping("/api/v1/studio/novels/{novel}/translations/{team}/workspace")
@Tag(name = "Студія: Авторський простір", description = "Матеріали твору: канон, плани, рішення, реткони, стан роботи, вкладення та історія змін. Право токена — `studio`.")
class StudioWorkspaceApiController {

    private final AccessPolicy access;
    private final WorkspaceService workspace;
    private final Idempotency idempotency;
    private final JsonMapper json;

    StudioWorkspaceApiController(AccessPolicy access, WorkspaceService workspace, Idempotency idempotency, JsonMapper json) {
        this.access = access;
        this.workspace = workspace;
        this.idempotency = idempotency;
        this.json = json;
    }

    @GetMapping("/documents")
    @Operation(operationId = "listWorkspaceDocuments", summary = "Список документів та пошук",
            description = "Список матеріалів авторського простору сторінками або пошук за текстом і назвою. Повертає короткі сніпети для економії контексту.")
    StudioApiV1.Page<StudioApiV1.WorkspaceDocumentSummary> listWorkspaceDocuments(
            @PathVariable String novel, @PathVariable String team,
            @Parameter(description = "Вид: canon, timeline, plan, decision, status, meta, note") @RequestParam(required = false) String kind,
            @Parameter(description = "Пошуковий запит") @RequestParam(required = false) String q,
            @Parameter(description = "Ключ наступної сторінки з попередньої відповіді (`next`)") @RequestParam(required = false) String after,
            @Parameter(description = "Скільки записів на сторінці (до 100)") @RequestParam(defaultValue = "20") int limit) {
        long editionId = access.editionAt(novel, team);
        access.requireTextEditor(editionId);
        int size = Math.max(1, Math.min(limit, 100));
        return workspace.listApiPaged(editionId, kind, q, after, size);
    }

    @GetMapping("/documents/{slug}")
    @Operation(operationId = "getWorkspaceDocument", summary = "Документ авторського простору",
            description = "Повний текст документа у форматі Markdown, його метадані, посилання та прикріплені ілюстрації.")
    @ApiResponse(responseCode = "404", description = "Документа не знайдено",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/Problem")))
    StudioApiV1.WorkspaceDocument getWorkspaceDocument(
            @PathVariable String novel, @PathVariable String team,
            @PathVariable String slug) {
        long editionId = access.editionAt(novel, team);
        access.requireTextEditor(editionId);
        return workspace.getApi(editionId, slug)
                .orElseThrow(() -> UserFacingException.notFound("Документа не знайдено."));
    }

    @PutMapping("/documents/{slug}")
    @Operation(operationId = "saveWorkspaceDocument", summary = "Створити або оновити документ",
            description = "Створення нового або збереження змін чинного документа. "
                    + "При збереженні обов'язково вказуйте `version` (номер поточної версії): якщо документ уже змінено іншим учасником, повертається 409 Conflict. "
                    + "Підтримує `Idempotency-Key` для безпечного повтору запиту після збою.")
    @ApiResponse(responseCode = "200", description = "Документ збережено",
            content = @Content(schema = @Schema(implementation = StudioApiV1.WorkspaceDocument.class)),
            headers = @Header(name = Idempotency.REPLAYED, description = "З `Idempotency-Key`: `true` — це збережена відповідь першого такого запиту, нічого не змінено", schema = @Schema(type = "string", example = "true")))
    @ApiResponse(responseCode = "409", description = "Конфлікт паралельного редагування: документ уже змінено іншим учасником",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/Problem")))
    ResponseEntity<String> saveWorkspaceDocument(
            @PathVariable String novel, @PathVariable String team,
            @PathVariable String slug,
            @Parameter(description = "Будь-який унікальний рядок до 255 знаків (напр. UUID); повтор із ним не зробить нічого вдруге",
                    example = "3f1c9a6e-0d4b-4a57-9a8e-2b6f1c0d7e21")
            @RequestHeader(name = Idempotency.HEADER, required = false) String key,
            @RequestBody StudioApiV1.WorkspaceDocumentInput body) {
        if (body == null || body.title() == null || body.title().isBlank()) {
            throw UserFacingException.badRequest("Вкажіть назву документа.");
        }
        long editionId = access.editionAt(novel, team);
        EditionAccess who = access.requireTextEditor(editionId);
        long accountId = who.viewer().accountId();
        String request = "PUT workspace doc " + editionId + " " + slug + " " + json.writeValueAsString(body);
        return idempotency.once(accountId, key, request, () -> {
            StudioApiV1.WorkspaceDocument saved = workspace.saveApi(editionId, slug, body.title(), body.kind(),
                    body.content(), body.version(), accountId, body.comment());
            return new Idempotency.Answer(HttpStatus.OK, saved);
        });
    }

    @DeleteMapping("/documents/{slug}")
    @Operation(operationId = "deleteWorkspaceDocument", summary = "Вилучити документ",
            description = "Вилучає документ, його історію змін та внутрішні посилання.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteWorkspaceDocument(
            @PathVariable String novel, @PathVariable String team,
            @PathVariable String slug) {
        long editionId = access.editionAt(novel, team);
        access.requireTextEditor(editionId);
        workspace.delete(editionId, slug);
    }

    @GetMapping("/documents/{slug}/revisions")
    @Operation(operationId = "listWorkspaceRevisions", summary = "Історія змін документа",
            description = "Список збережених ревізій документа сторінками.")
    StudioApiV1.Page<StudioApiV1.WorkspaceRevision> listWorkspaceRevisions(
            @PathVariable String novel, @PathVariable String team,
            @PathVariable String slug,
            @Parameter(description = "Ключ наступної сторінки з попередньої відповіді (`next`)") @RequestParam(required = false) String after,
            @Parameter(description = "Скільки записів на сторінці (до 100)") @RequestParam(defaultValue = "20") int limit) {
        long editionId = access.editionAt(novel, team);
        access.requireTextEditor(editionId);
        int size = Math.max(1, Math.min(limit, 100));
        return workspace.revisionsApiPaged(editionId, slug, after, size);
    }

    @PostMapping("/documents/{slug}/revisions/{revisionId}/restore")
    @Operation(operationId = "restoreWorkspaceRevision", summary = "Відновити версію документа",
            description = "Повертає текст і назву документа до вказаної ревізії, створюючи нову версію.")
    @ApiResponse(responseCode = "200", description = "Версію відновлено",
            content = @Content(schema = @Schema(implementation = StudioApiV1.WorkspaceDocument.class)),
            headers = @Header(name = Idempotency.REPLAYED, description = "З `Idempotency-Key`: `true` — це збережена відповідь першого такого запиту, нічого не змінено", schema = @Schema(type = "string", example = "true")))
    ResponseEntity<String> restoreWorkspaceRevision(
            @PathVariable String novel, @PathVariable String team,
            @PathVariable String slug,
            @PathVariable long revisionId,
            @Parameter(description = "Будь-який унікальний рядок до 255 знаків (напр. UUID); повтор із ним не зробить нічого вдруге",
                    example = "3f1c9a6e-0d4b-4a57-9a8e-2b6f1c0d7e21")
            @RequestHeader(name = Idempotency.HEADER, required = false) String key) {
        long editionId = access.editionAt(novel, team);
        EditionAccess who = access.requireTextEditor(editionId);
        long accountId = who.viewer().accountId();
        String request = "POST restore workspace " + editionId + " " + slug + " " + revisionId;
        return idempotency.once(accountId, key, request, () -> {
            StudioApiV1.WorkspaceDocument doc = workspace.restoreRevisionApi(editionId, slug, revisionId, accountId);
            return new Idempotency.Answer(HttpStatus.OK, doc);
        });
    }

    @GetMapping("/attachments")
    @Operation(operationId = "listWorkspaceAttachments", summary = "Список вкладень",
            description = "Список ілюстрацій та вкладень авторського простору.")
    List<StudioApiV1.WorkspaceAttachment> listWorkspaceAttachments(
            @PathVariable String novel, @PathVariable String team,
            @Parameter(description = "Адреса документа (slug) для фільтрації вкладень") @RequestParam(required = false) String documentSlug) {
        long editionId = access.editionAt(novel, team);
        access.requireTextEditor(editionId);
        return workspace.attachmentsApi(editionId, documentSlug);
    }

    @PostMapping("/attachments")
    @Operation(operationId = "addWorkspaceAttachment", summary = "Прикріпити ілюстрацію",
            description = "Прикріплює зображення з «Мої картинки» до авторського простору чи окремого документа.")
    @ResponseStatus(HttpStatus.CREATED)
    StudioApiV1.WorkspaceAttachment addWorkspaceAttachment(
            @PathVariable String novel, @PathVariable String team,
            @RequestBody StudioApiV1.WorkspaceAttachmentInput body) {
        if (body == null) {
            throw UserFacingException.badRequest("Вкажіть дані вкладення.");
        }
        long editionId = access.editionAt(novel, team);
        access.requireTextEditor(editionId);
        return workspace.addAttachmentApi(editionId, body.documentSlug(), body.imageId(), body.name(), body.description());
    }

    @DeleteMapping("/attachments/{id}")
    @Operation(operationId = "deleteWorkspaceAttachment", summary = "Вилучити вкладення",
            description = "Вилучає прив'язку вкладення до авторського простору.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteWorkspaceAttachment(
            @PathVariable String novel, @PathVariable String team,
            @PathVariable long id) {
        long editionId = access.editionAt(novel, team);
        access.requireTextEditor(editionId);
        workspace.deleteAttachment(editionId, id);
    }

    @GetMapping("/export")
    @Operation(operationId = "exportWorkspace", summary = "Експорт матеріалів",
            description = "Повний знімок матеріалів авторського простору: усі документи з вмістом, посиланнями, метаданими та список вкладень.")
    StudioApiV1.WorkspaceExport exportWorkspace(
            @PathVariable String novel, @PathVariable String team) {
        long editionId = access.editionAt(novel, team);
        access.requireTextEditor(editionId);
        return workspace.exportApi(editionId, novel, team);
    }
}
