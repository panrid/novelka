package panrid.space.novelka.cli.support;

import panrid.space.novelka.core.support.Json;

public final class Output {
    private Output() {
    }

    public static void json(Object value) {
        System.out.println(Json.write(value));
    }
}
