package space.panrid.novelka.apitoken.internal;

import java.util.Map;
import java.util.Set;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import space.panrid.novelka.platform.text.Handles;

/**
 * Where a token limited to one translation may go (етап 22.12), on top of its scopes. An
 * allow-list, like {@link TokenScopes}: the translation's own addresses in the Studio's API, its
 * novel, the person's «Мої переклади» and «Мої картинки», and the public reading that names no
 * other novel. Anything else — another translation, a new novel, the site's own {@code /api/studio},
 * an address added later and not named here — is refused.
 */
final class OneTranslation {

    /**
     * The translation a token is limited to, as its addresses name it.
     *
     * @param novel   the novel's slug
     * @param teamKey the team's handle, {@link Handles#key normalised}
     */
    record Bound(String novel, String teamKey) {
    }

    private static final Set<String> READING = Set.of("GET", "HEAD");

    private static final PathPattern TRANSLATION = parse("/api/v1/studio/novels/{novel}/translations/{team}/**");
    /** The novel's translations: reading them, never starting another one with this token. */
    private static final PathPattern TRANSLATIONS = parse("/api/v1/studio/novels/{novel}/translations");
    private static final PathPattern STUDIO_NOVEL = parse("/api/v1/studio/novels/{novel}/**");
    private static final PathPattern PUBLIC_NOVEL = parse("/api/v1/novels/{novel}/**");
    /** The person's own: «Мої переклади» (only this one is listed) and «Мої картинки». */
    private static final PathPattern MY_TRANSLATIONS = parse("/api/v1/studio/translations");
    private static final PathPattern MY_PICTURES = parse("/api/v1/studio/pictures");
    private static final PathPattern ME = parse("/api/me");
    private static final PathPattern STUDIO = parse("/api/v1/studio/**");
    private static final PathPattern PUBLIC = parse("/api/v1/**");

    private OneTranslation() {
    }

    private static PathPattern parse(String pattern) {
        return PathPatternParser.defaultInstance.parse(pattern);
    }

    /** Whether a token limited to {@code bound} may make this request. */
    static boolean allows(Bound bound, String method, String path) {
        PathContainer parsed = PathContainer.parsePath(path);
        Map<String, String> found = variables(TRANSLATION, parsed);
        if (found != null) {
            return bound.novel().equals(found.get("novel")) && bound.teamKey().equals(Handles.key(found.get("team")));
        }
        found = variables(TRANSLATIONS, parsed);
        if (found != null) {
            return READING.contains(method) && bound.novel().equals(found.get("novel"));
        }
        found = variables(STUDIO_NOVEL, parsed);
        if (found == null) {
            found = variables(PUBLIC_NOVEL, parsed);
        }
        if (found != null) {
            return bound.novel().equals(found.get("novel"));
        }
        if (MY_TRANSLATIONS.matches(parsed) || ME.matches(parsed)) {
            return READING.contains(method);
        }
        if (MY_PICTURES.matches(parsed)) {
            return true;
        }
        return !STUDIO.matches(parsed) && PUBLIC.matches(parsed);
    }

    private static Map<String, String> variables(PathPattern pattern, PathContainer path) {
        PathPattern.PathMatchInfo info = pattern.matchAndExtract(path);
        return info == null ? null : info.getUriVariables();
    }
}
