package panrid.space.novelka.server.models;

import java.util.List;

/** Cached provider models; {@code error} is set when the last refresh failed and older data is shown. */
public record ModelCatalog(String provider, String refreshedAt, boolean stale, String error, List<ModelInfo> items) {
}
