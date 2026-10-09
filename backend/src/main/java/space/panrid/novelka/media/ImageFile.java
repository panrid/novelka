package space.panrid.novelka.media;

/** A stored picture's bytes, for a downloaded book. @param extension {@code jpg} or {@code png} */
public record ImageFile(byte[] content, String mediaType, String extension) {
}
