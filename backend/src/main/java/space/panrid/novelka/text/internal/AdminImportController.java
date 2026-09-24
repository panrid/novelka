package space.panrid.novelka.text.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.catalog.Catalog;
import space.panrid.novelka.catalog.EditionRef;
import space.panrid.novelka.catalog.NewNovel;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.text.Markup;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.team.Teams;
import space.panrid.novelka.text.ChapterFiles;
import space.panrid.novelka.text.Chapters;

/**
 * Temporary way to fill the site before the Studio exists (stage 2 of the plan):
 * an administrator uploads a .txt or .md file and gets a published novel.
 */
@RestController
class AdminImportController {

    record ImportResult(String slug, int chapters, List<String> simplified) {
    }

    private final AccessPolicy access;
    private final Teams teams;
    private final Catalog catalog;
    private final Chapters chapters;

    AdminImportController(AccessPolicy access, Teams teams, Catalog catalog, Chapters chapters) {
        this.access = access;
        this.teams = teams;
        this.catalog = catalog;
        this.chapters = chapters;
    }

    @PostMapping("/api/admin/novels")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    ImportResult importNovel(@RequestParam("file") MultipartFile file, @RequestParam String title,
            @RequestParam(defaultValue = "") String author, @RequestParam(defaultValue = "") String description,
            @RequestParam(defaultValue = "") String tags, @RequestParam(defaultValue = "human") String kind,
            @RequestParam(defaultValue = "false") boolean adult) {
        Viewer viewer = access.requireSiteRole(SiteRole.ADMIN);
        ChapterFiles.Parsed parsed = ChapterFiles.parse(file.getOriginalFilename(), utf8(file));

        List<String> simplified = new ArrayList<>(parsed.simplified());
        List<ChapterFiles.ParsedChapter> withoutLinks = parsed.chapters().stream().map(chapter -> {
            List<Block> kept = chapter.blocks().stream().filter(block -> block.sourceUrl() == null).toList();
            if (kept.size() < chapter.blocks().size()) {
                simplified.add("Картинки за посиланням поки пропущено: їх можна буде додати в редакторі.");
            }
            return new ChapterFiles.ParsedChapter(chapter.title(), kept);
        }).toList();

        EditionRef edition = catalog.createNovel(new NewNovel(title, author, paragraphs(description),
                Arrays.stream(tags.split(",")).toList(), kind, adult, teams.personalTeam(viewer.accountId()),
                kind.equals("original") ? viewer.accountId() : null));
        List<Integer> numbers = chapters.publishNew(edition.editionId(), withoutLinks, "import", viewer.accountId());
        return new ImportResult(edition.novelSlug(), numbers.size(), simplified.stream().distinct().toList());
    }

    private static List<Block> paragraphs(String text) {
        List<Block> blocks = new ArrayList<>();
        for (String line : text.replace("\r\n", "\n").split("\n")) {
            if (!line.isBlank()) {
                blocks.add(Block.paragraph("d" + (blocks.size() + 1), Markup.parse(line.strip())));
            }
        }
        return blocks;
    }

    /** Files must be UTF-8: a Windows-1251 file would otherwise become mojibake silently. */
    private static String utf8(MultipartFile file) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
        } catch (CharacterCodingException error) {
            throw UserFacingException.badRequest("Файл має бути в кодуванні UTF-8. Збережіть його як UTF-8 і спробуйте ще раз.");
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
