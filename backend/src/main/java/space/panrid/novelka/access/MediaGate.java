package space.panrid.novelka.access;

import static space.panrid.novelka.jooq.Tables.CHAPTER;
import static space.panrid.novelka.jooq.Tables.EDITOR_DRAFT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.GLOSSARY_ENTRY;
import static space.panrid.novelka.jooq.Tables.IMAGE;
import static space.panrid.novelka.jooq.Tables.IMAGE_FILE;
import static space.panrid.novelka.jooq.Tables.REVISION;
import static space.panrid.novelka.jooq.Tables.WORKSPACE_ATTACHMENT;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import space.panrid.novelka.account.CurrentUser;

/**
 * Files of private publications (етап 24.1). Every file under {@code /media/} is looked up by its
 * key before the static handler answers it, so an old address of a picture does not outlive the
 * publication turning private:
 * <ul>
 * <li>a picture tied to a private publication — its cover, a picture of its team, one in any
 *     revision of its chapters, a portrait in its glossary — is <em>private</em>;</li>
 * <li>a private picture is answered to the publication's team, and to those who read a
 *     publication that shows it to readers: its cover, a picture in a <em>published</em> chapter,
 *     a glossary portrait. A picture only in a draft is not shown to invited readers;</li>
 * <li>to everyone else — 404, as if there were no such file; and never with a shared-cache
 *     header, so each new request is checked again (revoked access stops at once).</li>
 * </ul>
 * Avatars and pictures in messages are no publication's and stay as they were.
 */
@Component
public class MediaGate extends OncePerRequestFilter {

    static final String PREFIX = "/media/";
    private static final Set<String> PERSONAL = Set.of("avatar", "group_avatar", "message");

    private final DSLContext db;
    private final CurrentUser currentUser;

    MediaGate(DSLContext db, CurrentUser currentUser) {
        this.db = db;
        this.currentUser = currentUser;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = java.net.URLDecoder.decode(request.getRequestURI().substring(PREFIX.length()), java.nio.charset.StandardCharsets.UTF_8);
        Optional<Decision> decision = decide(key, currentUser.accountId().orElse(null));
        if (decision.isPresent() && decision.get() == Decision.OPEN) {
            chain.doFilter(request, response);
            return;
        }
        if (decision.isEmpty() || decision.get() == Decision.REFUSED) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        chain.doFilter(request, new PrivateCaching(response));
    }

    /** What a file is for this person: anyone's, theirs as a private one, or not there for them. */
    enum Decision {
        OPEN, PRIVATE, REFUSED
    }

    /** @return empty when the key is no picture's (the static handler answers 404 itself) */
    Optional<Decision> decide(String key, Long accountId) {
        var image = db.select(IMAGE.ID, IMAGE.KIND, IMAGE.RESTRICTED, IMAGE.WORKSPACE_EDITION_ID, IMAGE.OWNER_ACCOUNT_ID, IMAGE.ORIGINAL_KEY, IMAGE.HIDDEN_AT).from(IMAGE_FILE).join(IMAGE).on(IMAGE.ID.eq(IMAGE_FILE.IMAGE_ID))
                .where(IMAGE_FILE.KEY.eq(key)).fetchOptional();
        if (image.isEmpty()) {
            return Optional.empty();
        }
        long imageId = image.get().value1();
        if (image.get().value7() != null) {
            return Optional.of(Decision.REFUSED);
        }
        boolean original = key.equals(image.get().value6());
        if (!original && PERSONAL.contains(image.get().value2())) {
            return Optional.of(Decision.OPEN);
        }
        Long bound = image.get().value4();
        if (bound != null) {
            boolean team = accountId != null && db.fetchExists(EDITION, EDITION.ID.eq(bound),
                    Visibility.inTeam(accountId), Visibility.tokenEdition() == null ? DSL.noCondition() : EDITION.ID.eq(Visibility.tokenEdition()));
            boolean reader = !original && db.fetchExists(EDITION, EDITION.ID.eq(bound), shownToReaders(imageId),
                    EDITION.HIDDEN_AT.isNull(), EDITION.DELETED_AT.isNull(), Visibility.readableNow(accountId));
            return Optional.of(team || reader ? Decision.PRIVATE : Decision.REFUSED);
        }
        boolean protectedFile = original || image.get().value3()
                || db.fetchExists(EDITION, EDITION.VISIBILITY.eq(Visibility.PRIVATE).and(tied(imageId)));
        if (!protectedFile) {
            return Optional.of(Decision.OPEN);
        }
        boolean owner = accountId != null && accountId.equals(image.get().value5()) && Visibility.tokenEdition() == null;
        boolean team = accountId != null && db.fetchExists(EDITION, tied(imageId), Visibility.inTeam(accountId),
                Visibility.tokenEdition() == null ? DSL.noCondition() : EDITION.ID.eq(Visibility.tokenEdition()));
        boolean reader = !original && db.fetchExists(EDITION, shownToReaders(imageId), EDITION.HIDDEN_AT.isNull(),
                EDITION.DELETED_AT.isNull(), Visibility.readableNow(accountId));
        return Optional.of(owner || team || reader ? Decision.PRIVATE : Decision.REFUSED);
    }

    /** The picture belongs to the edition in any way: cover, its team's, any revision, a draft, a portrait. */
    private static Condition tied(long imageId) {
        return EDITION.COVER_IMAGE_ID.eq(imageId)
                .or(DSL.exists(DSL.selectOne().from(IMAGE).where(IMAGE.ID.eq(imageId), IMAGE.TEAM_ID.eq(EDITION.TEAM_ID))))
                .or(DSL.exists(DSL.selectOne().from(REVISION).join(CHAPTER).on(CHAPTER.ID.eq(REVISION.CHAPTER_ID))
                        .where(CHAPTER.EDITION_ID.eq(EDITION.ID), containsRevision(imageId))))
                .or(DSL.exists(DSL.selectOne().from(EDITOR_DRAFT).join(CHAPTER).on(CHAPTER.ID.eq(EDITOR_DRAFT.CHAPTER_ID))
                        .where(CHAPTER.EDITION_ID.eq(EDITION.ID), containsDraft(imageId))))
                .or(DSL.exists(DSL.selectOne().from(WORKSPACE_ATTACHMENT)
                        .where(WORKSPACE_ATTACHMENT.EDITION_ID.eq(EDITION.ID), WORKSPACE_ATTACHMENT.IMAGE_ID.eq(imageId))))
                .or(portrait(imageId));
    }

    /** The edition shows the picture to its readers: cover, a published chapter, a portrait. */
    private static Condition shownToReaders(long imageId) {
        return EDITION.COVER_IMAGE_ID.eq(imageId)
                .or(DSL.exists(DSL.selectOne().from(CHAPTER).join(REVISION).on(REVISION.ID.eq(CHAPTER.PUBLISHED_REVISION_ID))
                        .where(CHAPTER.EDITION_ID.eq(EDITION.ID), CHAPTER.DELETED_AT.isNull(), containsRevision(imageId))))
                .or(portrait(imageId));
    }

    private static Condition portrait(long imageId) {
        return DSL.exists(DSL.selectOne().from(GLOSSARY_ENTRY)
                .where(GLOSSARY_ENTRY.NOVEL_ID.eq(EDITION.NOVEL_ID), GLOSSARY_ENTRY.PORTRAIT_IMAGE_ID.eq(imageId)));
    }

    private static Condition containsRevision(long imageId) {
        return DSL.condition("{0} @> {1}::jsonb", REVISION.BLOCKS, DSL.val("[{\"imageId\":" + imageId + "}]"));
    }

    private static Condition containsDraft(long imageId) {
        return DSL.condition("{0} @> {1}::jsonb", EDITOR_DRAFT.BLOCKS, DSL.val("[{\"imageId\":" + imageId + "}]"));
    }

    /** Keeps the static handler from marking a private file as cacheable by anyone for a year. */
    private static final class PrivateCaching extends HttpServletResponseWrapper {

        PrivateCaching(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setHeader(String name, String value) {
            if (!ignored(name)) {
                super.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (!ignored(name)) {
                super.addHeader(name, value);
            }
        }

        @Override
        public void setDateHeader(String name, long date) {
            if (!ignored(name)) {
                super.setDateHeader(name, date);
            }
        }

        @Override
        public void addDateHeader(String name, long date) {
            if (!ignored(name)) {
                super.addDateHeader(name, date);
            }
        }

        private static boolean ignored(String name) {
            return HttpHeaders.CACHE_CONTROL.equalsIgnoreCase(name) || HttpHeaders.EXPIRES.equalsIgnoreCase(name);
        }
    }
}
