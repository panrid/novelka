package panrid.space.novelka.server.models;

import java.util.List;

/** Provider that can list its models. Providers without discovery simply have no implementation. */
public interface ModelDirectory {
    String provider();

    List<ModelInfo> fetch() throws Exception;
}
