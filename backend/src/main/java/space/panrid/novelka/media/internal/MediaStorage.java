package space.panrid.novelka.media.internal;

/** Where picture files live. Local disk now; an S3-compatible bucket can replace it. */
interface MediaStorage {

    void write(String key, byte[] content);

    /** The public path readers load the file from. */
    String url(String key);
}
