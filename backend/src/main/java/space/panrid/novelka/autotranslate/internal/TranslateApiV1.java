package space.panrid.novelka.autotranslate.internal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * The shapes of the Studio's API for the original, autotranslation and the glossary (етап 22.3),
 * under {@code /api/v1/studio/novels/{novel}/translations/{team}}. Apart from the site's own JSON:
 * these change only by adding fields. Costs are in шаги, never in dollars.
 */
final class TranslateApiV1 {

    private TranslateApiV1() {
    }

    /** A list read by key: pass {@code next} as {@code after} for the following page; null on the last. */
    record Page<T>(List<T> items, String next) {
    }

    // ---- the original -------------------------------------------------------------------------------

    /** @param translated the translation has a chapter under this number */
    record OriginalEntry(int number, String title, int chars, String language, boolean translated, OffsetDateTime updatedAt) {
    }

    record Original(int number, String title, String language, List<OriginalBlock> blocks) {
    }

    /** A paragraph of the original under the id its translation keeps. */
    record OriginalBlock(String id, String type, String text) {
    }

    /**
     * Chapters of the original to paste: each in its own fields, or one {@code text} the site splits
     * into chapters as on «Оригінал» («Глава 1», «第1話», «Chapter 1»…).
     *
     * @param language ja, en, zh or ko
     * @param first    the number of the first chapter; the next one after those the translation has when absent
     */
    record Paste(String language, Integer first, List<PastedChapter> chapters, String text) {
    }

    record PastedChapter(String title, String text) {
    }

    /** @param from the first chapter saved; a chapter that was there under that number is replaced */
    record Pasted(int saved, int from, int to) {
    }

    // ---- autotranslation ----------------------------------------------------------------------------

    /**
     * What to run.
     *
     * @param from    the first chapter; the first one not done yet when absent
     * @param to      the last chapter (required)
     * @param steps   analyze, translate, proofread — in any sensible set; translate alone when absent
     * @param preset  a ready set of models ({@code options.presets}); the site's models when absent
     * @param models  the model of single steps, changing the preset's; people may pick only {@code options.models}
     * @param redo    chapters done already are done again (otherwise skipped)
     * @param publish false leaves each chapter as a draft of the one who started the run
     */
    record RunRequest(Integer from, Integer to, List<String> steps, Long preset, StepModels models, Boolean redo, Boolean publish) {
    }

    record StepModels(String analyze, String translate, String proofread) {
    }

    /**
     * What a run may be made with.
     *
     * @param personal paid from the person's шаги (everyone but the site owner)
     * @param balance  the person's шаги; null for the site owner
     * @param pasted   translated from the original the team pasted; otherwise from the original's site
     * @param sourceChapters the last chapter of the original there is
     * @param nextNumber     the first chapter not translated yet
     * @param models   the models a person may put into a step
     */
    record Options(boolean personal, Balance balance, boolean pasted, int sourceChapters, int nextNumber, List<Preset> presets,
            List<String> models) {
    }

    /** @param available free to spend; @param reserved held by runs still going */
    record Balance(int available, int reserved) {
    }

    /** @param quality best, high or draft — what people get it as; null for the site owner's own sets */
    record Preset(long id, String name, String summary, String quality, String analyze, String translate, String proofread) {
    }

    /**
     * Кошторис: what the run would do and cost; nothing is started.
     *
     * @param chapters   chapters it will do
     * @param skipped    chapters in the range done already and left alone
     * @param shah       the expected price in шаги
     * @param reserveShah what a person's run holds until it ends (what is not spent comes back); 0 for the site owner
     * @param available  the person's free шаги; null for the site owner
     * @param enough     the person has the шаги to start it
     * @param estimated  some chapters' length is a guess (not downloaded yet)
     */
    record Estimate(String kind, int from, int to, int chapters, int skipped, List<String> steps, int shah, int reserveShah,
            Integer available, boolean enough, boolean estimated, String analyzeModel, String translateModel, String proofreadModel) {
    }

    /**
     * A run.
     *
     * @param kind        translate, proofread or analyze
     * @param state       queued, running, done, failed or cancelled
     * @param done        chapters finished
     * @param heldShah    for a person's run, what it holds; for the site owner's, its price
     * @param spentShah   what the models cost so far
     * @param chargedShah what a finished person's run was charged
     * @param current     the chapter it works on, null when none is left
     */
    record Run(long id, String kind, String state, int from, int to, int done, int heldShah, int spentShah, int chargedShah,
            boolean personal, Step current, String error, OffsetDateTime createdAt, OffsetDateTime finishedAt) {
    }

    /** @param stage analyze, translate, proofread or publish; @param progress the chapter's part done, 0 to 1 */
    record Step(int chapter, String stage, String state, String error, double progress) {
    }

    /** One event of a run's journal: what it did to a chapter. */
    record Event(long id, int chapter, String kind, Map<String, Object> details, OffsetDateTime at) {
    }

    // ---- the glossary -------------------------------------------------------------------------------

    /**
     * An entry of the novel's glossary (shared by its translations).
     *
     * @param original the form in the original's current language; the team alone sees it
     * @param kind     character, place, organization, term or other
     * @param gender   male, female or unknown
     * @param chapter  the chapter where it was met first
     * @param status   new (nobody checked it yet), approved or rejected
     * @param manual   written or corrected by people
     */
    record Entry(long id, String ukrainian, String original, String reading, String kind, String gender, String note, Integer chapter,
            String status, boolean manual) {
    }

    /** {@code chapter}: where the word is met; {@code original}: how the original writes it. */
    record NewEntry(Integer chapter, String ukrainian, String original, String kind, String gender) {
    }

    /** Fields left out stay as they are. A corrected entry becomes approved. */
    record EntryChange(String ukrainian, String kind, String gender, String note) {
    }

    /** @param status approved, rejected or new */
    record StatusChange(List<Long> ids, String status) {
    }

    record Changed(int changed) {
    }

    /** What the novel tells of the entry by the chapter: its «Хто це» card. @param manual written by people */
    record Fact(long id, int chapter, String text, boolean manual) {
    }

    record FactRequest(Integer chapter, String text) {
    }
}
