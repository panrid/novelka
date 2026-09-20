package panrid.space.novelka.core.service.translation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import panrid.space.novelka.core.integration.ai.AiClient;
import panrid.space.novelka.core.model.Block;
import panrid.space.novelka.core.model.Chapter;
import panrid.space.novelka.core.model.Entry;
import panrid.space.novelka.core.model.Glossary;
import panrid.space.novelka.core.model.Work;
import panrid.space.novelka.core.persistence.DatabaseSession;
import panrid.space.novelka.core.support.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PipelineTest {
    DatabaseSession store;
    Map<String, Work> jobs;
    Glossary glossary;
    Chapter chapter;

    @BeforeEach
    void setup() throws Exception {
        store = mock(DatabaseSession.class);
        when(store.chapters()).thenReturn(mock(panrid.space.novelka.core.repository.ChapterRepository.class));
        when(store.jobs()).thenReturn(mock(panrid.space.novelka.core.repository.JobRepository.class));
        when(store.glossaries()).thenReturn(mock(panrid.space.novelka.core.repository.GlossaryRepository.class));
        when(store.glossaryService()).thenReturn(mock(panrid.space.novelka.core.service.glossary.GlossaryService.class));
        jobs = new LinkedHashMap<>();
        glossary = new Glossary(0, List.of());
        chapter =
                new Chapter(
                        1,
                        "url",
                        "title",
                        List.of(new Block("title", "heading", "題"), new Block("p1", "paragraph", "本文")),
                        "html");
        when(store.chapters().chapter("novel", 1)).thenAnswer(a -> chapter);
        when(store.jobs().latest("novel", 1))
                .thenAnswer(a -> jobs.values().stream().reduce((x, y) -> y).orElse(null));
        when(store.jobs().job(anyString())).thenAnswer(a -> jobs.get(a.getArgument(0)));
        when(store.glossaries().glossary("novel")).thenAnswer(a -> glossary);
        var glossaryService = store.glossaryService();
        var jobRepository = store.jobs();
        doAnswer(
                a -> {
                    glossary = a.getArgument(1);
                    return null;
                })
                .when(glossaryService)
                .update(eq("novel"), any());
        doAnswer(
                a -> {
                    Work w = a.getArgument(0);
                    jobs.put(w.id(), w);
                    return null;
                })
                .when(jobRepository)
                .save(any(Work.class));
        when(store.transaction(any())).thenAnswer(a -> ((Callable<?>) a.getArgument(0)).call());
    }

    @Test
    void malformedGlossaryProposalIsRetainedWithoutBlockingTranslation() throws Exception {
        Entry invalid =
                new Entry(
                        "name", "character", "", "", "Ім’я", List.of(), "unknown", "", "unknown", 1, false);
        AiClient ai =
                (w, i, stage, g, p, b) ->
                        Json.M.valueToTree(
                                stage.equals("analyze")
                                        ? Map.of("entries", List.of(invalid))
                                        : Map.of("blocks", chapter.blocks(), "summary", "scene"));
        var pipeline = new Pipeline(store, ai, 6000);
        assertEquals("complete", pipeline.run(pipeline.create("novel", 1, false), 0).state());
        assertTrue(glossary.entries().isEmpty());
        verify(store.glossaries()).propose(eq("novel"), anyString(),
                argThat(value -> Json.write(value).contains("validationError")));
    }

    @Test
    void interruptionResumesAtProofreadingWithoutTranslatingAgain() throws Exception {
        AtomicInteger analysis = new AtomicInteger(),
                translation = new AtomicInteger(),
                edit = new AtomicInteger();
        AiClient ai =
                (w, i, stage, g, p, b) -> {
                    if (stage.equals("analyze")) {
                        analysis.incrementAndGet();
                        return Json.read("{\"entries\":[]}");
                    }
                    if (stage.equals("translate")) translation.incrementAndGet();
                    if (stage.equals("proofread") && edit.incrementAndGet() == 1)
                        throw new IllegalStateException("network interrupted");
                    return Json.read(Json.write(Map.of("blocks", chapter.blocks(), "summary", "scene")));
                };
        var pipeline = new Pipeline(store, ai, 6000);
        Work first = pipeline.create("novel", 1, false);
        assertThrows(IllegalStateException.class, () -> pipeline.run(first, 0));
        assertEquals("translated", jobs.get(first.id()).segments().getFirst().state());
        Work done = pipeline.run(jobs.get(first.id()), 0);
        assertEquals("complete", done.state());
        assertEquals(1, analysis.get());
        assertEquals(1, translation.get());
        assertEquals(2, edit.get());
        assertEquals(first.id(), pipeline.create("novel", 1, false).id());
    }

    @Test
    void malformedModelBlocksNeverBecomeComplete() throws Exception {
        AiClient ai =
                (w, i, stage, g, p, b) ->
                        Json.read(stage.equals("analyze") ? "{\"entries\":[]}" : "{\"blocks\":[]}");
        var pipeline = new Pipeline(store, ai, 6000);
        var first = pipeline.create("novel", 1, false);
        assertThrows(IllegalArgumentException.class, () -> pipeline.run(first, 0));
        assertEquals("analyzed", jobs.get(first.id()).segments().getFirst().state());
    }

    @Test
    void manualGenderCannotBeOverwrittenByAnalysis() throws Exception {
        Entry manual =
                new Entry(
                        "アキ",
                        "character",
                        "アキ",
                        "",
                        "Акі",
                        List.of(),
                        "female",
                        "manual evidence",
                        "confirmed",
                        1,
                        true);
        glossary = new Glossary(1, List.of(manual));
        Entry proposal =
                new Entry(
                        "アキ", "character", "アキ", "", "Акі", List.of(), "male", "guess", "assumed", 1, false);
        AiClient ai =
                (w, i, stage, g, p, b) ->
                        Json.read(
                                Json.write(
                                        stage.equals("analyze")
                                                ? Map.of("entries", List.of(proposal))
                                                : Map.of("blocks", chapter.blocks(), "summary", "scene")));
        var pipeline = new Pipeline(store, ai, 6000);
        pipeline.run(pipeline.create("novel", 1, false), 0);
        assertEquals(manual, glossary.entries().getFirst());
        assertEquals(1, glossary.revision());
    }

    @Test
    void sourceChangesRequireForceAndPreserveOldVersion() throws Exception {
        var p = new Pipeline(store, null, 6000);
        var first = p.create("novel", 1, false);
        chapter =
                new Chapter(1, "url", "title", List.of(new Block("title", "heading", "新")), "changed");
        assertThrows(IllegalStateException.class, () -> p.create("novel", 1, false));
        assertEquals(2, p.create("novel", 1, true).revision());
        assertTrue(jobs.containsKey(first.id()));
    }
}
