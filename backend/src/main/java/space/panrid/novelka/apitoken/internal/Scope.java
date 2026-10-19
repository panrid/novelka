package space.panrid.novelka.apitoken.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import space.panrid.novelka.account.SiteRole;

/** What a token may reach (етап 22.1); its owner's own rights still decide every action. */
enum Scope {
    /** The public: novels, catalog, chapters, tags; «who am I». */
    READ,
    /** My library, reading progress, read marks, ratings. */
    LIBRARY,
    /** Suggestions: drafts, sending, my suggestions. */
    SUGGEST,
    /** Translations where I am in the team: chapters, original, glossary, publication, pictures. */
    STUDIO,
    /** Autotranslation and other AI that spends my шаги. */
    TRANSLATE,
    /** Administration; offered to site roles only, every change journaled. */
    ADMIN;

    String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<Scope> of(String code) {
        return Arrays.stream(values()).filter(scope -> scope.code().equals(code)).findFirst();
    }

    /** The scopes a person may put on a token: «admin» only to moderators, administrators and the owner. */
    static List<Scope> offeredTo(SiteRole role) {
        return Arrays.stream(values()).filter(scope -> scope != ADMIN || role.atLeast(SiteRole.MODERATOR)).toList();
    }
}
