package space.panrid.novelka.analytics.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** What the analytics page shows; money in US dollars, time in seconds, shares from 0 to 1. */
record Report(Period period, Spend spend, List<Bucket> spendSeries, List<StageRow> stages, List<ModelRow> models,
        List<Combo> combos, List<NovelCost> novels, Funding funding, List<TranslationRow> translation,
        List<ProofreadRow> proofread, List<AnalysisRow> analysis, List<Reason> reasons, Site site) {

    /** @param bucket «day» or «week»: how the series are grouped */
    record Period(int days, String from, String bucket) {
    }

    /** @param estimateRatio what was paid against what was expected before the calls (1 = exact) */
    record Spend(double usd, double previousUsd, int calls, int failedCalls, int uncertainCalls, long tokensIn,
            long tokensOut, double usdPerCall, Double estimateRatio, int chapters, double usdPerChapter) {
    }

    record Bucket(LocalDate start, Map<String, Double> usdByStage, int calls) {
    }

    record StageRow(String stage, double usd, int calls, double share, long tokensIn, long tokensOut) {
    }

    record ModelRow(String model, String stage, int calls, int failed, long tokensIn, long tokensOut, double usd,
            Double secondsAverage, Double secondsP90, Double usdPerMillionTokens, Double outputPerInput) {
    }

    /** One way of running the steps: which model analyses, which translates, which proofreads. */
    record Combo(String analyze, String translate, String proofread, int chapters, double usd, double usdPerChapter,
            Double usdPerThousandChars, Double secondsPerChapter, Map<String, Double> usdByStage) {
    }

    record NovelCost(String title, String slug, int chapters, double usd, double usdPerChapter, Map<String, Double> usdByStage) {
    }

    /**
     * @param outsideRuns calls that belong to no run: novel details, glossary fixes, illustrations
     * @param chargedUsd  what people paid in шаги for their runs, in dollars
     */
    record Funding(double siteUsd, double peopleUsd, double outsideRunsUsd, int chargedShah, double chargedUsd) {
    }

    /**
     * How people corrected the translation of one translating model (with one proofreading model)
     * after it was published.
     *
     * @param editedShare          chapters that people changed afterwards, of all
     * @param paragraphsChanged    paragraphs that differ now from what the model published
     * @param wordsChanged         words of the model's text that are no longer there
     */
    record TranslationRow(String translate, String proofread, int chapters, int editedChapters, double editedShare,
            int editorRevisions, int suggestions, int acceptedSuggestions, int rejectedSuggestions,
            double suggestionsPerChapter, double acceptedPerChapter, double paragraphsChanged, double wordsChanged,
            double retriesPerChapter, double splitsPerChapter, double missingPerChapter, int failedSteps,
            Double usdPerChapter, Double secondsPerChapter) {
    }

    /** What proofreading did: lines it changed and how often it was skipped. */
    record ProofreadRow(String model, int parts, int changedLines, double changedPerPart, int skipped, double skippedShare,
            double retriesPerPart) {
    }

    /**
     * How good one model's analysis was: glossary entries it proposed and what the people did with
     * them, and the chapter titles they had to fix.
     */
    record AnalysisRow(String model, int chapters, int entries, int approved, int changed, int rejected, int waiting,
            double entriesPerChapter, double rejectedShare, int titlesEdited, double titlesEditedShare,
            Double usdPerChapter, double retriesPerChapter) {
    }

    /** Why a step was repeated, split or skipped, for one model. */
    record Reason(String stage, String model, String kind, String reason, int count) {
    }

    record Site(int newAccounts, int activeAccounts, int readers, int chaptersPublished, int machineChapters,
            int comments, int suggestions, int acceptedSuggestions, int rejectedSuggestions, int libraryAdds,
            List<SiteBucket> series, List<TopEdition> top) {
    }

    record SiteBucket(LocalDate start, int accounts, int chapters, int comments, int suggestions) {
    }

    record TopEdition(String title, String slug, String team, int readers, int library, int chapters) {
    }
}
