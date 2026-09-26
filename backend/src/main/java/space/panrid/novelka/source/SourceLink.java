package space.panrid.novelka.source;

/**
 * A novel on one of the source sites.
 *
 * @param provider the site, stored in novel.source: {@code syosetu}, {@code fenrir}…
 * @param key      unique across all sites, stored in novel.source_key
 * @param url      the novel's page for people
 * @param adult    the site keeps it among 18+ works
 */
public record SourceLink(String provider, String key, String url, boolean adult) {
}
