package space.panrid.novelka.autotranslate.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.access.EditionAccess;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.text.OriginalFiles;

/**
 * «Оригінал» of a translation in the Studio (етап 19): paste text or upload .txt or .epub, see
 * how it splits into chapters, save. While the stage is being made only the site owner has it.
 */
@RestController
@RequestMapping("/api/studio/editions/{editionId}/originals")
class OriginalController {

    /** Pasted text: a few hundred chapters at most, as files are. */
    private static final int MAX_TEXT_CHARS = 20_000_000;

    private final AccessPolicy access;
    private final Originals originals;

    OriginalController(AccessPolicy access, Originals originals) {
        this.access = access;
        this.originals = originals;
    }

    /** A translator of the translation; until етап 19 is open to all, the site owner and those the owner let in. */
    private EditionAccess translator(long editionId) {
        access.requireAutotranslate();
        EditionAccess who = access.requireTranslator(editionId);
        originals.requireTranslation(editionId);
        return who;
    }

    @PostMapping("/preview")
    Originals.Preview preview(@PathVariable long editionId, @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String text, @RequestParam(required = false) Integer first) {
        translator(editionId);
        return originals.preview(editionId, parse(file, text), first);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Originals.Saved save(@PathVariable long editionId, @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String text, @RequestParam(required = false) Integer first,
            @RequestParam String language) {
        EditionAccess who = translator(editionId);
        return originals.save(editionId, who.viewer().accountId(), parse(file, text), first, language);
    }

    /** A chapter in its own fields: the title and the text. */
    record Given(String title, String text) {
    }

    /** Chapters given one by one, each in its own fields, or one chapter edited. */
    record Separate(List<Given> chapters, Integer first, String language) {
    }

    @PostMapping("/separate/preview")
    Originals.Preview previewSeparate(@PathVariable long editionId, @RequestBody Separate body) {
        translator(editionId);
        return originals.preview(editionId, separate(body), body.first());
    }

    @PostMapping("/separate")
    @ResponseStatus(HttpStatus.CREATED)
    Originals.Saved saveSeparate(@PathVariable long editionId, @RequestBody Separate body) {
        EditionAccess who = translator(editionId);
        return originals.save(editionId, who.viewer().accountId(), separate(body), body.first(), body.language());
    }

    @GetMapping
    Originals.Listing list(@PathVariable long editionId, @RequestParam(defaultValue = "1") int page) {
        translator(editionId);
        return originals.list(editionId, page);
    }

    @GetMapping("/{number}")
    Originals.Original one(@PathVariable long editionId, @PathVariable int number) {
        translator(editionId);
        return originals.find(editionId, number);
    }

    record LanguageChange(int from, int to, String language) {
    }

    @PatchMapping("/language")
    Originals.Listing changeLanguage(@PathVariable long editionId, @RequestBody LanguageChange body) {
        translator(editionId);
        originals.changeLanguage(editionId, Math.min(body.from(), body.to()), Math.max(body.from(), body.to()), body.language());
        return originals.list(editionId, 1);
    }

    @DeleteMapping("/{number}")
    void deleteOne(@PathVariable long editionId, @PathVariable int number) {
        translator(editionId);
        originals.delete(editionId, number);
    }

    /** «Видалити оригінали»: the translation stays; the texts it was made from go. */
    @DeleteMapping
    void deleteAll(@PathVariable long editionId) {
        translator(editionId);
        originals.delete(editionId, null);
    }

    private static OriginalFiles.Parsed separate(Separate body) {
        List<Given> given = body.chapters() == null ? List.of() : body.chapters();
        if (given.size() > OriginalFiles.MAX_CHAPTERS) {
            throw UserFacingException.badRequest("Забагато глав за раз: до %d.".formatted(OriginalFiles.MAX_CHAPTERS));
        }
        List<OriginalFiles.Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < given.size(); i++) {
            OriginalFiles.Chapter chapter = OriginalFiles.one(given.get(i).title(), given.get(i).text());
            if (chapter.blocks().isEmpty()) {
                throw UserFacingException.badRequest(given.size() == 1 ? "Вставте текст глави." : "У главі %d немає тексту.".formatted(i + 1));
            }
            chapters.add(chapter);
        }
        if (chapters.isEmpty()) {
            throw UserFacingException.badRequest("Вставте текст глави.");
        }
        return new OriginalFiles.Parsed(chapters, Map.of());
    }

    private static OriginalFiles.Parsed parse(MultipartFile file, String text) {
        if (file != null && !file.isEmpty()) {
            try {
                return OriginalFiles.parse(file.getOriginalFilename() == null ? "file.txt" : file.getOriginalFilename(), file.getBytes());
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }
        if (text == null || text.isBlank()) {
            throw UserFacingException.badRequest("Вставте текст оригіналу або виберіть файл.");
        }
        if (text.length() > MAX_TEXT_CHARS) {
            throw UserFacingException.badRequest("Забагато тексту за раз — розділіть на кілька частин.");
        }
        return OriginalFiles.parse(null, text.getBytes(StandardCharsets.UTF_8));
    }
}
