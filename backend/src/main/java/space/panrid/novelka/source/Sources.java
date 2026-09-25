package space.panrid.novelka.source;

/** Originals. Network calls are slow; callers must not hold a transaction around them. */
public interface Sources {

    SourceNovel novel(SyosetuLink link);

    /** The chapter from the database, downloaded first if the site has not seen it yet. */
    SourceText chapter(long novelId, int number);
}
