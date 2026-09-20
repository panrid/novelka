package panrid.space.novelka.core.integration.source.syosetu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveSourceTest {
    @Test
    void readsMetadataAndChapterFromCurrentSite() throws Exception {
        var source = new Syosetu();
        var novel = source.inspect("https://ncode.syosetu.com/n2267be/");
        assertEquals("n2267be", novel.id());
        assertTrue(novel.chapterCount() > 1);
        var chapter = source.fetch(novel, 1);
        assertTrue(chapter.blocks().size() > 10);
        assertFalse(chapter.title().isBlank());
    }
}
