package space.panrid.novelka.apitoken.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Which scope a request needs (етап 22.1). An allow-list: the first rule that matches decides,
 * and a request no rule names cannot be made with a token at all — the password, the email,
 * signing in and out, the tokens themselves, deleting a translation, giving roles. Whatever a rule
 * lets through still meets the owner's own rights in {@code AccessPolicy}.
 */
final class TokenScopes {

    /** A rule: these methods (empty: any) on this path need {@code scope}; null scope — the site only. */
    private record Rule(Set<String> methods, PathPattern pattern, Scope scope) {

        boolean matches(String method, PathContainer path) {
            return (methods.isEmpty() || methods.contains(method)) && pattern.matches(path);
        }
    }

    private static final Set<String> ANY = Set.of();
    private static final Set<String> READING = Set.of("GET", "HEAD");
    private static final List<Rule> RULES = new ArrayList<>();

    static {
        // Only with the session, never with a token.
        site("/api/me/api-tokens/**", "/api/admin/api-tokens/**", "/api/me/webhooks/**", "/api/auth/**", "/api/me/password", "/api/me/email",
                "/api/me/nick", "/api/me/google", "/api/me/telegram/**", "/api/studio/editions/*/delete-code",
                "/api/studio/editions/*/delete", "/api/admin/users/*/role");

        // The Studio's API (етап 22.3), before the public reading API it sits under: runs spend шаги,
        // a batch of правки is a reader's sending, the rest is the team's work.
        rule(Scope.TRANSLATE, ANY, "/api/v1/studio/novels/*/translations/*/runs/**");
        rule(Scope.SUGGEST, Set.of("POST"), "/api/v1/studio/novels/*/translations/*/suggestions");
        rule(Scope.STUDIO, ANY, "/api/v1/studio/**");

        rule(Scope.READ, READING, "/api/v1/**", "/api/me", "/api/home", "/api/catalog/**", "/api/tags/**", "/api/search/hints",
                "/api/novels/**", "/api/users/**", "/api/teams/*", "/api/editions/*/comments");

        rule(Scope.LIBRARY, ANY, "/api/library/**", "/api/progress/*", "/api/reads/*", "/api/editions/*/rating");

        rule(Scope.SUGGEST, ANY, "/api/suggestions/**");
        rule(Scope.SUGGEST, READING, "/api/me/suggestions");

        // AI that spends шаги, before the rest of the Studio.
        rule(Scope.TRANSLATE, ANY, "/api/studio/autotranslate/**", "/api/studio/editions/*/autotranslate/**",
                "/api/studio/illustrations/**", "/api/studio/editions/*/illustrations/**",
                "/api/studio/editions/*/glossary/*/rewrite", "/api/studio/editions/*/glossary/*/regender",
                "/api/studio/editions/*/chapters/*/glossary/source", "/api/studio/editions/*/chapters/*/variants",
                "/api/studio/editions/*/chapters/*/proofread-paragraph", "/api/studio/editions/*/chapters/*/check");

        rule(Scope.STUDIO, ANY, "/api/studio/**", "/api/media/**", "/api/me/pictures/**");
        rule(Scope.STUDIO, READING, "/api/me/teams");

        rule(Scope.ADMIN, ANY, "/api/admin/**");
    }

    private TokenScopes() {
    }

    private static void site(String... patterns) {
        rule(null, ANY, patterns);
    }

    private static void rule(Scope scope, Set<String> methods, String... patterns) {
        for (String pattern : patterns) {
            RULES.add(new Rule(methods, PathPatternParser.defaultInstance.parse(pattern), scope));
        }
    }

    /** The scope the request needs; empty when it cannot be made with a token. */
    static Optional<Scope> required(String method, String path) {
        PathContainer parsed = PathContainer.parsePath(path);
        for (Rule rule : RULES) {
            if (rule.matches(method, parsed)) {
                return Optional.ofNullable(rule.scope());
            }
        }
        return Optional.empty();
    }
}
