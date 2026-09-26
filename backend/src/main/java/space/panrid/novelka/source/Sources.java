package space.panrid.novelka.source;

/** Originals. Network calls are slow; callers must not hold a transaction around them. */
public interface Sources {

    /** The link as one of the sites understands it; a person's error if none does. */
    SourceLink link(String raw);

    SourceNovel novel(SourceLink link);

    /** Keeps the table of contents of an imported novel, replacing the previous one. */
    void keep(long novelId, SourceNovel novel);

    /** The chapter from the database, downloaded first if the site has not seen it yet. */
    SourceText chapter(long novelId, int number);
}
