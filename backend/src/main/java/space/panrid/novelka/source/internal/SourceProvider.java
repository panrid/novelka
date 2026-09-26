package space.panrid.novelka.source.internal;

import java.util.List;
import java.util.Optional;

import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.source.SourceEntry;
import space.panrid.novelka.source.SourceLink;
import space.panrid.novelka.source.SourceNovel;

/**
 * One source site. Adding a site is one such class and a test on saved pages; nothing else
 * in the application knows which site a novel came from.
 */
interface SourceProvider {

    /** Stored in novel.source; never changes once novels use it. */
    String id();

    /** The site's name for people. */
    String name();

    /** The link if it points to a novel on this site. */
    Optional<SourceLink> parse(String raw);

    SourceNovel novel(SourceLink link);

    /**
     * The chapter's text. Block ids follow the page ({@code s1}, {@code s2}…) so a translation
     * keeps the same ids. Never called for a locked chapter.
     */
    Page chapter(SourceLink link, SourceEntry entry);

    /** @param title the chapter's title as the page gives it, or blank */
    record Page(String title, List<Block> blocks) {
    }
}
