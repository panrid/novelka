package space.panrid.novelka.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import space.panrid.novelka.platform.security.ApiRateLimits;
import space.panrid.novelka.support.IntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Етап 22.4: the API's OpenAPI description — public and cached, {@code /api/v1} alone, and the
 * same as the committed snapshot {@code doc/api/openapi-v1.json}. A change of the API changes the
 * description: update the snapshot with
 * <pre>./gradlew :backend:test --tests '*ApiDescriptionTests' -PupdateApiSnapshot</pre>
 * (or copy {@code backend/build/api/openapi-v1.json}, which every run writes, or the JSON the
 * failure prints) and commit it with the change. What would break programs written against the
 * snapshot — a path, an operation, a response field or its type gone, a field no longer always
 * there, a new required parameter — fails even then: in {@code /v1} the API only grows.
 */
@IntegrationTest
class ApiDescriptionTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    /** The snapshot names the production site whatever the stand that wrote it. */
    private static final String SERVER = "https://novelka.in.ua";
    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options");
    private static final String UPDATE = "./gradlew :backend:test --tests '*ApiDescriptionTests' -PupdateApiSnapshot";

    @LocalServerPort
    int port;

    @Autowired
    ApiRateLimits limits;

    @BeforeEach
    void countsStartAfresh() {
        limits.forget();
    }

    @Test
    void anyoneReadsTheDescriptionOfV1AndKeepsItFiveMinutes() {
        HttpResponse<String> response = get(null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("public, max-age=300");
        String etag = response.headers().firstValue("ETag").orElseThrow();
        assertThat(get(etag).statusCode()).isEqualTo(304);

        JsonNode spec = JSON.readTree(response.body());
        assertThat(spec.path("openapi").asString()).startsWith("3.1");
        assertThat(spec.path("paths").propertyNames()).isNotEmpty()
                .allSatisfy(path -> assertThat(path).as("the site's own JSON stays undocumented").startsWith("/api/v1/"));
        assertThat(spec.at("/paths/~1api~1v1~1catalog/get/operationId").asString()).isEqualTo("listCatalog");
        assertThat(spec.at("/components/securitySchemes/token/scheme").asString()).isEqualTo("bearer");
        assertThat(spec.at("/paths/~1api~1v1~1catalog/get/responses/429/headers/Retry-After").isMissingNode()).isFalse();
    }

    @Test
    void theDescriptionIsTheCommittedSnapshotAndBreaksNothing() throws IOException {
        JsonNode generated = canonical(JSON.readTree(get(null).body()));
        String text = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(generated) + "\n";
        Path built = Path.of("build", "api", "openapi-v1.json");
        Files.createDirectories(built.getParent());
        Files.writeString(built, text);

        Path snapshot = Path.of(System.getProperty("novelka.apiSnapshot", "../doc/api/openapi-v1.json"));
        boolean update = Boolean.getBoolean("novelka.updateApiSnapshot");
        if (!Files.exists(snapshot)) {
            if (update) {
                Files.createDirectories(snapshot.toAbsolutePath().getParent());
                Files.writeString(snapshot, text);
                return;
            }
            fail("No API snapshot at " + snapshot.toAbsolutePath() + ". Write it with\n  " + UPDATE + "\nThe description now:\n" + text);
        }
        JsonNode committed = JSON.readTree(Files.readString(snapshot));
        List<String> breaking = breaking(committed, generated);
        assertThat(breaking).as("Changes that break programs written against " + snapshot.getFileName()
                + " — /v1 may only grow; anything else waits for a new version. The description now:\n" + text).isEmpty();
        if (committed.equals(generated)) {
            return;
        }
        if (update) {
            Files.writeString(snapshot, text);
            return;
        }
        fail("The API description differs from " + snapshot.toAbsolutePath() + " at:\n  " + String.join("\n  ", differences(committed, generated))
                + "\nIf the change is meant, update the snapshot:\n  " + UPDATE + "\n(or copy " + built.toAbsolutePath()
                + "). The description now:\n" + text);
    }

    @Test
    void removingAnOperationAFieldOrAddingARequiredParameterBreaks() {
        JsonNode old = JSON.readTree("""
                {"paths": {"/api/v1/a": {"get": {"parameters": [{"name": "x", "in": "query"}],
                    "responses": {"200": {"content": {"application/json": {"schema": {"$ref": "#/components/schemas/A"}}}}}},
                  "post": {"responses": {"200": {}}}}},
                 "components": {"schemas": {"A": {"type": "object", "required": ["id"],
                    "properties": {"id": {"type": "string"}, "n": {"type": "integer"}}}}}}
                """);
        assertThat(breaking(old, old)).isEmpty();

        JsonNode grown = JSON.readTree("""
                {"paths": {"/api/v1/a": {"get": {"parameters": [{"name": "x", "in": "query"}, {"name": "y", "in": "query"}],
                    "responses": {"200": {"content": {"application/json": {"schema": {"$ref": "#/components/schemas/A"}}}}}},
                  "post": {"responses": {"200": {}}}},
                  "/api/v1/b": {"get": {"responses": {"200": {}}}}},
                 "components": {"schemas": {"A": {"type": "object", "required": ["id", "m"],
                    "properties": {"id": {"type": "string"}, "n": {"type": "integer"}, "m": {"type": "string"}}}}}}
                """);
        assertThat(breaking(old, grown)).as("only new things").isEmpty();

        JsonNode broken = JSON.readTree("""
                {"paths": {"/api/v1/a": {"get": {"parameters": [{"name": "z", "in": "query", "required": true}],
                    "responses": {"200": {"content": {"application/json": {"schema": {"$ref": "#/components/schemas/A"}}}}}}}},
                 "components": {"schemas": {"A": {"type": "object", "required": [],
                    "properties": {"id": {"type": "integer"}}}}}}
                """);
        assertThat(breaking(old, broken)).containsExactlyInAnyOrder(
                "POST /api/v1/a: operation removed",
                "GET /api/v1/a: parameter query x removed",
                "GET /api/v1/a: new required parameter query z",
                "schema A.id: type changed from \"string\" to \"integer\"",
                "schema A: field id no longer always present",
                "schema A: field n removed");
    }

    // ---- the description ------------------------------------------------------------------------

    private HttpResponse<String> get(String ifNoneMatch) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/openapi.json")).GET();
        if (ifNoneMatch != null) {
            request.header("If-None-Match", ifNoneMatch);
        }
        try {
            return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Keys in order and the production address, so the snapshot does not depend on where it was written. */
    private static JsonNode canonical(JsonNode spec) {
        for (JsonNode server : spec.path("servers")) {
            if (server instanceof ObjectNode object) {
                object.put("url", SERVER);
            }
        }
        return sorted(spec);
    }

    private static JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = JSON.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            node.properties().forEach(field -> fields.put(field.getKey(), field.getValue()));
            fields.forEach((name, value) -> copy.set(name, sorted(value)));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JSON.createArrayNode();
            node.forEach(value -> copy.add(sorted(value)));
            return copy;
        }
        return node;
    }

    /** Where the two differ, as JSON pointers; the first fifty. */
    private static List<String> differences(JsonNode was, JsonNode now) {
        List<String> found = new ArrayList<>();
        differences("", was, now, found);
        return found.size() > 50 ? found.subList(0, 50) : found;
    }

    private static void differences(String at, JsonNode was, JsonNode now, List<String> found) {
        if (was.equals(now)) {
            return;
        }
        if (was.isObject() && now.isObject()) {
            Set<String> names = new TreeSet<>(was.propertyNames());
            names.addAll(now.propertyNames());
            for (String name : names) {
                String path = at + "/" + name.replace("~", "~0").replace("/", "~1");
                if (!was.has(name)) {
                    found.add(path + " (added)");
                } else if (!now.has(name)) {
                    found.add(path + " (removed)");
                } else {
                    differences(path, was.get(name), now.get(name), found);
                }
            }
            return;
        }
        found.add((at.isEmpty() ? "/" : at) + ": " + cut(was) + " → " + cut(now));
    }

    private static String cut(JsonNode node) {
        String text = node.toString();
        return text.length() > 120 ? text.substring(0, 120) + "…" : text;
    }

    // ---- what breaks old programs ---------------------------------------------------------------

    /** What {@code now} takes away from programs written against {@code was}; empty when it only adds. */
    static List<String> breaking(JsonNode was, JsonNode now) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, JsonNode> path : was.path("paths").properties()) {
            JsonNode nowPath = now.path("paths").path(path.getKey());
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                if (!METHODS.contains(operation.getKey())) {
                    continue;
                }
                String where = operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey();
                JsonNode nowOperation = nowPath.path(operation.getKey());
                if (nowOperation.isMissingNode()) {
                    found.add(where + ": operation removed");
                    continue;
                }
                parameters(where, operation.getValue(), nowOperation, found);
                for (Map.Entry<String, JsonNode> response : operation.getValue().path("responses").properties()) {
                    JsonNode nowResponse = nowOperation.path("responses").path(response.getKey());
                    if (!response.getKey().startsWith("2")) {
                        continue;
                    }
                    if (nowResponse.isMissingNode()) {
                        found.add(where + ": response " + response.getKey() + " removed");
                        continue;
                    }
                    for (Map.Entry<String, JsonNode> media : response.getValue().path("content").properties()) {
                        JsonNode nowMedia = nowResponse.path("content").path(media.getKey());
                        if (nowMedia.isMissingNode()) {
                            found.add(where + ": response " + response.getKey() + " no longer " + media.getKey());
                        } else {
                            schema(where + " " + response.getKey(), media.getValue().path("schema"), nowMedia.path("schema"), found);
                        }
                    }
                }
            }
        }
        for (Map.Entry<String, JsonNode> schema : was.path("components").path("schemas").properties()) {
            JsonNode nowSchema = now.path("components").path("schemas").path(schema.getKey());
            if (nowSchema.isMissingNode()) {
                found.add("schema " + schema.getKey() + " removed");
            } else {
                schema("schema " + schema.getKey(), schema.getValue(), nowSchema, found);
            }
        }
        return found;
    }

    private static void parameters(String where, JsonNode was, JsonNode now, List<String> found) {
        Map<String, JsonNode> before = parameters(was);
        Map<String, JsonNode> after = parameters(now);
        before.keySet().stream().filter(key -> !after.containsKey(key)).forEach(key -> found.add(where + ": parameter " + key + " removed"));
        after.forEach((key, parameter) -> {
            if (parameter.path("required").asBoolean(false) && !before.containsKey(key)) {
                found.add(where + ": new required parameter " + key);
            } else if (parameter.path("required").asBoolean(false) && !before.get(key).path("required").asBoolean(false)) {
                found.add(where + ": parameter " + key + " became required");
            }
        });
    }

    private static Map<String, JsonNode> parameters(JsonNode operation) {
        Map<String, JsonNode> found = new LinkedHashMap<>();
        for (JsonNode parameter : operation.path("parameters")) {
            found.put(parameter.path("in").asString() + " " + parameter.path("name").asString(), parameter);
        }
        return found;
    }

    /** A response's shape: the same type, every field still there, and those always present still always present. */
    private static void schema(String where, JsonNode was, JsonNode now, List<String> found) {
        for (String key : List.of("$ref", "type", "format")) {
            if (!was.path(key).equals(now.path(key))) {
                found.add(where + ": " + (key.equals("$ref") ? "schema" : key) + " changed from " + was.path(key) + " to " + now.path(key));
                return;
            }
        }
        for (JsonNode required : was.path("required")) {
            boolean still = false;
            for (JsonNode nowRequired : now.path("required")) {
                still |= nowRequired.equals(required);
            }
            if (!still && now.path("properties").has(required.asString())) {
                found.add(where + ": field " + required.asString() + " no longer always present");
            }
        }
        for (Map.Entry<String, JsonNode> property : was.path("properties").properties()) {
            JsonNode nowProperty = now.path("properties").path(property.getKey());
            if (nowProperty.isMissingNode()) {
                found.add(where + ": field " + property.getKey() + " removed");
            } else {
                schema(where + "." + property.getKey(), property.getValue(), nowProperty, found);
            }
        }
        if (was.has("items")) {
            schema(where + "[]", was.path("items"), now.path("items"), found);
        }
    }
}
