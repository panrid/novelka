package space.panrid.novelka.studio.internal;

import static space.panrid.novelka.jooq.Tables.NOVEL;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.access.EditionAccess;
import space.panrid.novelka.catalog.Catalog;
import space.panrid.novelka.catalog.EditionChanges;
import space.panrid.novelka.catalog.EditionData;
import space.panrid.novelka.catalog.EditionRef;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.team.TeamInfo;
import space.panrid.novelka.team.Teams;

/**
 * A work from nothing through the Studio's API (етап 22.9): create a translation or an own work,
 * change its data, address, state and cover. The same {@link StudioNovels} as the Studio's pages, so
 * the same rights, checks and errors; a work made here shows in the Studio as one made on the site.
 */
@RestController
@RequestMapping("/api/v1/studio")
@Tag(name = "Студія")
class StudioNovelApiController {

    private final AccessPolicy access;
    private final StudioNovels novels;
    private final Catalog catalog;
    private final Teams teams;
    private final Images images;
    private final DSLContext db;

    StudioNovelApiController(AccessPolicy access, StudioNovels novels, Catalog catalog, Teams teams, Images images, DSLContext db) {
        this.access = access;
        this.novels = novels;
        this.catalog = catalog;
        this.teams = teams;
        this.images = images;
        this.db = db;
    }

    @PostMapping("/novels")
    @Operation(summary = "Новий твір", description = "Свій переклад чи свій твір — у вашій команді або в тій, де ви власник чи "
            + "перекладач. Обкладинку додайте окремим запитом `PUT …/cover`.")
    @ApiResponse(responseCode = "409", description = "Така адреса вже зайнята іншою новелою",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/Problem")))
    @ResponseStatus(HttpStatus.CREATED)
    StudioApiV1.Novel create(@RequestBody StudioApiV1.NewNovel body) {
        if (body == null) {
            throw UserFacingException.badRequest("Опишіть твір: вид, назва.");
        }
        EditionRef ref = novels.create(new StudioNovels.Creation(body.kind(), body.title(), body.slug(), body.author(),
                body.description() == null ? null : StudioApiV1.blocks(body.description()), body.tags(), body.adult(), body.team(),
                body.rightsConfirmed(), body.titleOriginal(), body.language()));
        return novel(access.requireTextEditor(ref.editionId()));
    }

    @GetMapping("/novels/{novel}/translations/{team}/settings")
    @Operation(summary = "Дані твору", description = "Назва, адреса, опис, теги, 18+, стан, обкладинка — як у «Налаштуваннях» перекладу.")
    StudioApiV1.Novel settings(@PathVariable String novel, @PathVariable String team) {
        return novel(access.requireTextEditor(access.editionAt(novel, team)));
    }

    @PatchMapping("/novels/{novel}/translations/{team}/settings")
    @Operation(summary = "Змінити дані й стан", description = "Лише власник команди. Поле, якого немає, лишається як є. Нова адреса "
            + "не скасовує стару: вона й далі веде сюди.")
    @ApiResponse(responseCode = "409", description = "Така адреса вже зайнята іншою новелою; або переклад у кошику",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = "#/components/schemas/Problem")))
    StudioApiV1.Novel update(@PathVariable String novel, @PathVariable String team, @RequestBody StudioApiV1.NovelChanges body) {
        if (body == null) {
            throw UserFacingException.badRequest("Вкажіть, що змінити.");
        }
        long editionId = access.editionAt(novel, team);
        novels.update(editionId, new EditionChanges(body.title(), body.author(),
                body.description() == null ? null : StudioNovels.description(StudioApiV1.blocks(body.description())), body.tags(),
                body.status(), body.adult(), body.pausedUntil(), null, body.downloadAllowed()), body.slug());
        return novel(access.requireTextEditor(editionId));
    }

    @PutMapping(path = "/novels/{novel}/translations/{team}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Обкладинка", description = "Лише власник команди. JPEG, PNG чи WebP до 5 МБ; сервер обріже її до 2:3 по центру.")
    StudioApiV1.Novel cover(@PathVariable String novel, @PathVariable String team, @RequestParam("file") MultipartFile file) {
        long editionId = access.editionAt(novel, team);
        access.requireEditionOwner(editionId);
        if (file == null || file.isEmpty()) {
            throw UserFacingException.badRequest("Виберіть файл обкладинки.");
        }
        novels.uploadCover(editionId, bytes(file), file.getOriginalFilename());
        return novel(access.requireTextEditor(editionId));
    }

    @DeleteMapping("/novels/{novel}/translations/{team}/cover")
    @Operation(summary = "Прибрати обкладинку", description = "Лише власник команди. Без обкладинки показується згенерована з назвою.")
    StudioApiV1.Novel removeCover(@PathVariable String novel, @PathVariable String team) {
        long editionId = access.editionAt(novel, team);
        novels.setCover(editionId, null);
        return novel(access.requireTextEditor(editionId));
    }

    private StudioApiV1.Novel novel(EditionAccess who) {
        EditionData data = catalog.edition(who.editionId()).orElseThrow(() -> UserFacingException.notFound("Такої новели немає."));
        TeamInfo team = teams.find(data.teamId()).orElseThrow();
        String language = db.select(NOVEL.SOURCE_LANGUAGE).from(NOVEL).where(NOVEL.ID.eq(data.novelId())).fetchOne(NOVEL.SOURCE_LANGUAGE);
        String cover = data.coverImageId() == null ? null
                : images.find(data.coverImageId()).map(image -> image.url(480)).orElse(null);
        Map<Long, StoredImage> none = Map.of();
        return new StudioApiV1.Novel(data.novelSlug(), team.handle(), team.name(), data.kind(), data.title(), data.author(),
                StudioApiV1.blocks(data.description() == null ? List.of() : data.description(), none),
                data.tags(), data.adult(), data.status(), data.pausedUntil(), data.downloadAllowed(), cover, language,
                data.facts() == null ? null : data.facts().titleOriginal(), data.chapterCount(), who.role().code());
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
