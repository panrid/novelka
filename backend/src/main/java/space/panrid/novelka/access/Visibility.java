package space.panrid.novelka.access;

import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.EDITION_READER;
import static space.panrid.novelka.jooq.Tables.TEAM;
import static space.panrid.novelka.jooq.Tables.TEAM_MEMBER;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

/**
 * Who reads a publication (етап 24.1), as SQL conditions over {@code edition}, so every list
 * filters before it counts and pages. A public publication is read by everyone; a private one
 * by its team (owner and members) and by the readers its owner invited. The site's own role
 * gives nothing here.
 *
 * <p>Global lists (catalog, search, sitemap, feeds, profiles, team pages) show only public
 * publications ({@link #isPublic()}); a person's own lists (library, notifications) and the
 * pages of one publication use {@link #readableBy(Long, Long)}.
 */
public final class Visibility {

    public static final String PUBLIC = "public";
    public static final String PRIVATE = "private";

    private Visibility() {
    }

    /** Publications anyone may see: not private. */
    public static Condition isPublic() {
        return EDITION.VISIBILITY.eq(PUBLIC);
    }

    /** {@link #isPublic()} for an edition named by an id column of another table. */
    public static Condition isPublic(Field<Long> editionId) {
        return DSL.exists(DSL.selectOne().from(EDITION).where(EDITION.ID.eq(editionId), EDITION.VISIBILITY.eq(PUBLIC)));
    }

    /**
     * Publications the person reads: public ones, and private ones of their team or invited to.
     *
     * @param accountId    the person, or null for a guest
     * @param tokenEdition the one translation an API token is limited to, or null: a limited token
     *                     reads no other private publication, whatever its owner may
     */
    public static Condition readableBy(Long accountId, Long tokenEdition) {
        if (accountId == null) {
            return isPublic();
        }
        Condition privateOk = inTeam(accountId).or(DSL.exists(DSL.selectOne().from(EDITION_READER)
                .where(EDITION_READER.EDITION_ID.eq(EDITION.ID), EDITION_READER.ACCOUNT_ID.eq(accountId))));
        if (tokenEdition != null) {
            privateOk = privateOk.and(EDITION.ID.eq(tokenEdition));
        }
        return isPublic().or(privateOk);
    }

    /** {@link #readableBy(Long, Long)} for an edition named by an id column of another table. */
    public static Condition readableBy(Field<Long> editionId, Long accountId, Long tokenEdition) {
        return DSL.exists(DSL.selectOne().from(EDITION).where(EDITION.ID.eq(editionId), readableBy(accountId, tokenEdition)));
    }

    /** The one translation an API token of the current request is limited to, or null (also outside requests). */
    public static Long tokenEdition() {
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes attributes) {
            return space.panrid.novelka.platform.security.TokenGrant.edition(attributes.getRequest());
        }
        return null;
    }

    /** {@link #readableBy(Long, Long)} for the person of the current request, limited as their token is. */
    public static Condition readableNow(Long accountId) {
        return readableBy(accountId, tokenEdition());
    }

    /**
     * {@link #readableBy(Long, Long)} with both sides named by columns: for queues and inboxes
     * that hold many people (chapter news, notifications, deliveries).
     */
    public static Condition readableByAccount(Field<Long> editionId, Field<Long> accountId) {
        var e = EDITION.as("readable_edition");
        Condition team = DSL.exists(DSL.selectOne().from(TEAM).where(TEAM.ID.eq(e.TEAM_ID), TEAM.OWNER_ID.eq(accountId)))
                .or(DSL.exists(DSL.selectOne().from(TEAM_MEMBER)
                        .where(TEAM_MEMBER.TEAM_ID.eq(e.TEAM_ID), TEAM_MEMBER.ACCOUNT_ID.eq(accountId))))
                .or(DSL.exists(DSL.selectOne().from(EDITION_READER)
                        .where(EDITION_READER.EDITION_ID.eq(e.ID), EDITION_READER.ACCOUNT_ID.eq(accountId))));
        return DSL.exists(DSL.selectOne().from(e).where(e.ID.eq(editionId), e.VISIBILITY.eq(PUBLIC).or(team)));
    }

    /** The edition's team: its owner or a member, whatever the role. */
    public static Condition inTeam(long accountId) {
        return DSL.exists(DSL.selectOne().from(TEAM).where(TEAM.ID.eq(EDITION.TEAM_ID), TEAM.OWNER_ID.eq(accountId)))
                .or(DSL.exists(DSL.selectOne().from(TEAM_MEMBER)
                        .where(TEAM_MEMBER.TEAM_ID.eq(EDITION.TEAM_ID), TEAM_MEMBER.ACCOUNT_ID.eq(accountId))));
    }
}
