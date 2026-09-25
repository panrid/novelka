package space.panrid.novelka.source;

/** The only way out to syosetu.com; tests put a fake in its place. */
public interface SyosetuHttp {

    /** Body of a 200 response; anything else is an error for the person who asked. */
    String get(String url);
}
