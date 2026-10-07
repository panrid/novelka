package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.PROPOSAL;
import static space.panrid.novelka.jooq.Tables.PROPOSAL_VOTE;
import static space.panrid.novelka.jooq.Tables.TEAM;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.autotranslate.ProposalTaken;
import space.panrid.novelka.catalog.Catalog;
import space.panrid.novelka.catalog.EditionRef;
import space.panrid.novelka.catalog.NewNovel;
import space.panrid.novelka.platform.text.Block;
import space.panrid.novelka.platform.web.RateLimiter;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.source.SourceLink;
import space.panrid.novelka.source.SourceNovel;
import space.panrid.novelka.source.Sources;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * «Що перекласти» (рішення 32): a novel proposed by its link, its page translated at the
 * site's cost (≈ $0,001) so voters read it in Ukrainian, votes, and «Беру перекладати», which
 * makes the team's edition from the proposal without paying for the page again.
 */
@Component
class Proposals {

    static final int PAGE = 20;
    private static final TypeReference<List<Block>> BLOCKS = new TypeReference<>() { };

    private final DSLContext db;
    private final Sources sources;
    private final Catalog catalog;
    private final Preparation preparation;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    /** Each proposal costs the site a model call: a handful a day per person is plenty. */
    private final RateLimiter proposing;
    private final TransactionTemplate transactions;

    Proposals(DSLContext db, Sources sources, Catalog catalog, Preparation preparation, ApplicationEventPublisher events,
            JsonMapper json, Clock clock, TransactionTemplate transactions) {
        this.transactions = transactions;
        this.db = db;
        this.sources = sources;
        this.catalog = catalog;
        this.preparation = preparation;
        this.events = events;
        this.json = json;
        this.proposing = new RateLimiter(5, Duration.ofDays(1), clock);
    }

    /**
     * @param chapters  how many the original has; null when the site cannot read it
     * @param site      where the original is (Syosetu, the link's host); null when there is no link
     * @param link      the original's page as proposed; null when there is none
     * @param automatic the site reads the original: name and description came by themselves, and
     *                  the team that takes it may translate it automatically
     * @param mine      the viewer proposed it: they may correct the title and description
     * @param taken     where it is translated now, once a team took it
     */
    record Item(long id, String title, String author, List<String> description, Integer chapters, boolean adult,
            String site, String proposedBy, OffsetDateTime createdAt, String state, int votes, boolean voted, boolean mine,
            Taken taken, String link, String comment, boolean automatic) {
    }

    /** Proposals of sites the site reads; «link» and «manual» are the ones entered by hand. */
    private static boolean readable(String source) {
        return !"link".equals(source) && !"manual".equals(source);
    }

    record Taken(String novelSlug, String teamHandle) {
    }

    record Page(List<Item> items, int total, int page, boolean hasMore) {
    }

    /** @param created false when the novel was proposed before: the viewer's vote went to it */
    record Proposed(long id, boolean created) {
    }

    /** What a person fills in: a link, or a name, or both; a description and a comment if they like. */
    record Proposal(String url, String title, String author, String description, String comment) {
    }

    /**
     * A link the site can read fills in the name and description by itself, as before. Any
     * other link, or none, needs the name; such a novel is translated by hand until the site
     * learns to read its site.
     */
    Proposed propose(Viewer viewer, Proposal proposal) {
        String url = proposal.url() == null ? "" : proposal.url().strip();
        String comment = proposal.comment() == null ? "" : proposal.comment().strip();
        if (comment.length() > 2_000) {
            throw UserFacingException.badRequest("Коментар — до 2 000 знаків.");
        }
        SourceLink link = null;
        if (!url.isEmpty()) {
            try {
                link = sources.link(url);
            } catch (UserFacingException unread) {
                // Not a site the site reads: kept as the person gave it.
            }
        }
        if (link == null) {
            return proposeByHand(viewer, url, proposal, comment);
        }
        return proposeFromSource(viewer, link, comment);
    }

    private Proposed proposeByHand(Viewer viewer, String url, Proposal proposal, String comment) {
        // A link proposed before is a vote for it, with or without a name typed again.
        String key = null;
        if (!url.isEmpty()) {
            key = comparable(url);
            Long existing = db.select(PROPOSAL.ID).from(PROPOSAL).where(PROPOSAL.SOURCE_KEY.eq(key)).fetchOne(PROPOSAL.ID);
            if (existing != null) {
                if ("open".equals(db.select(PROPOSAL.STATE).from(PROPOSAL).where(PROPOSAL.ID.eq(existing)).fetchOne(PROPOSAL.STATE))) {
                    vote(viewer, existing);
                }
                return new Proposed(existing, false);
            }
        }
        String title = proposal.title() == null ? "" : proposal.title().strip();
        if (title.isEmpty() || title.length() > 200) {
            throw UserFacingException.badRequest(url.isEmpty()
                    ? "Напишіть назву новели (до 200 знаків)."
                    : "Цей сайт Новелка поки не вміє читати сама: напишіть назву новели (до 200 знаків).");
        }
        String author = proposal.author() == null ? "" : proposal.author().strip();
        String description = proposal.description() == null ? "" : proposal.description().strip();
        if (author.length() > 100 || description.length() > 5_000) {
            throw UserFacingException.badRequest("Автор — до 100 знаків, опис — до 5 000.");
        }
        if (!proposing.tryAcquire("proposal:" + viewer.accountId())) {
            throw new UserFacingException(HttpStatus.TOO_MANY_REQUESTS, "Сьогодні ви вже запропонували досить новел. Спробуйте завтра.");
        }
        long id = db.insertInto(PROPOSAL)
                .set(PROPOSAL.SOURCE, url.isEmpty() ? "manual" : "link")
                .set(PROPOSAL.SOURCE_KEY, key)
                .set(PROPOSAL.SOURCE_URL, url.isEmpty() ? null : url)
                .set(PROPOSAL.TITLE_ORIGINAL, "")
                .set(PROPOSAL.AUTHOR_ORIGINAL, "")
                .set(PROPOSAL.TITLE, title)
                .set(PROPOSAL.AUTHOR, author)
                .set(PROPOSAL.DESCRIPTION, JSONB.valueOf(json.writeValueAsString(Preparation.paragraphs(description))))
                .set(PROPOSAL.COMMENT, comment)
                .set(PROPOSAL.PROPOSED_BY, viewer.accountId())
                .returning(PROPOSAL.ID).fetchSingle(PROPOSAL.ID);
        vote(viewer, id);
        return new Proposed(id, true);
    }

    /** One link, one proposal: «https://Site.com/novel/1/?ref=x» and «http://site.com/novel/1» are the same. */
    static String comparable(String url) {
        String text = url.strip();
        if (!text.matches("(?i)https?://[^\\s/]+.*")) {
            throw UserFacingException.badRequest("Посилання має починатися з http:// або https://.");
        }
        if (text.length() > 500) {
            throw UserFacingException.badRequest("Посилання задовге.");
        }
        java.net.URI uri;
        try {
            uri = java.net.URI.create(text.replace(" ", "%20"));
        } catch (IllegalArgumentException odd) {
            throw UserFacingException.badRequest("Це не схоже на посилання.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT).replaceFirst("^www\\.", "");
        String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
        return "link:" + host + path;
    }

    private Proposed proposeFromSource(Viewer viewer, SourceLink link, String comment) {
        Long existing = db.select(PROPOSAL.ID).from(PROPOSAL).where(PROPOSAL.SOURCE_KEY.eq(link.key()))
                .fetchOne(PROPOSAL.ID);
        if (existing != null) {
            if ("open".equals(db.select(PROPOSAL.STATE).from(PROPOSAL).where(PROPOSAL.ID.eq(existing)).fetchOne(PROPOSAL.STATE))) {
                vote(viewer, existing);
            }
            return new Proposed(existing, false);
        }
        if (catalog.novelBySource(link.key()).isPresent()) {
            throw UserFacingException.badRequest("Цю новелу вже перекладають на сайті — знайдіть її в пошуку.");
        }
        if (!proposing.tryAcquire("proposal:" + viewer.accountId())) {
            throw new UserFacingException(HttpStatus.TOO_MANY_REQUESTS, "Сьогодні ви вже запропонували досить новел. Спробуйте завтра.");
        }
        SourceNovel novel = sources.novel(link);
        Preparation.Metadata page = preparation.metadata(novel, "proposal");
        long id = db.insertInto(PROPOSAL)
                .set(PROPOSAL.SOURCE, link.provider())
                .set(PROPOSAL.SOURCE_KEY, link.key())
                .set(PROPOSAL.SOURCE_URL, link.url())
                .set(PROPOSAL.SOURCE_LANGUAGE, novel.language())
                .set(PROPOSAL.TITLE_ORIGINAL, novel.title())
                .set(PROPOSAL.AUTHOR_ORIGINAL, novel.author())
                .set(PROPOSAL.TITLE, page.title())
                .set(PROPOSAL.AUTHOR, page.author())
                .set(PROPOSAL.DESCRIPTION, JSONB.valueOf(json.writeValueAsString(page.description())))
                .set(PROPOSAL.CHAPTER_COUNT, novel.lastAvailable())
                .set(PROPOSAL.ADULT, link.adult())
                .set(PROPOSAL.COMMENT, comment)
                .set(PROPOSAL.PROPOSED_BY, viewer.accountId())
                .onConflictDoNothing()
                .returning(PROPOSAL.ID).fetchOptional(PROPOSAL.ID)
                // Two people proposed it at the same moment: both end up voting for one.
                .orElseGet(() -> db.select(PROPOSAL.ID).from(PROPOSAL).where(PROPOSAL.SOURCE_KEY.eq(link.key())).fetchSingle(PROPOSAL.ID));
        vote(viewer, id);
        return new Proposed(id, true);
    }

    /** @param sort «votes» (most wanted first) or «new»; @param state «open» or «taken» */
    Page page(Long viewerId, String sort, String state, int page) {
        var votes = DSL.field(DSL.selectCount().from(PROPOSAL_VOTE).where(PROPOSAL_VOTE.PROPOSAL_ID.eq(PROPOSAL.ID)));
        var where = PROPOSAL.STATE.eq("taken".equals(state) ? "taken" : "open");
        int total = db.fetchCount(PROPOSAL, where);
        int at = Math.max(1, page);
        var order = "new".equals(sort) || "taken".equals(state)
                ? List.of(PROPOSAL.UPDATED_AT.desc(), PROPOSAL.ID.desc())
                : List.of(votes.desc(), PROPOSAL.CREATED_AT.desc());
        List<Item> items = db.select(PROPOSAL.asterisk(), ACCOUNT.NICK, votes.as("votes"),
                        viewerId == null ? DSL.inline(false).as("voted")
                                : DSL.field(DSL.exists(DSL.selectOne().from(PROPOSAL_VOTE)
                                        .where(PROPOSAL_VOTE.PROPOSAL_ID.eq(PROPOSAL.ID), PROPOSAL_VOTE.ACCOUNT_ID.eq(viewerId)))).as("voted"),
                        NOVEL.SLUG, TEAM.HANDLE)
                .from(PROPOSAL)
                .join(ACCOUNT).on(ACCOUNT.ID.eq(PROPOSAL.PROPOSED_BY))
                .leftJoin(EDITION).on(EDITION.ID.eq(PROPOSAL.EDITION_ID))
                .leftJoin(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                .leftJoin(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .where(where)
                .orderBy(order).limit(PAGE).offset((at - 1) * PAGE)
                .fetch(row -> item(row, viewerId));
        return new Page(items, total, at, at * PAGE < total);
    }

    private Item item(Record row, Long viewerId) {
        List<String> description = json.readValue(row.get(PROPOSAL.DESCRIPTION).data(), BLOCKS).stream().map(Block::text).toList();
        String slug = row.get(NOVEL.SLUG);
        String source = row.get(PROPOSAL.SOURCE);
        return new Item(row.get(PROPOSAL.ID), row.get(PROPOSAL.TITLE), row.get(PROPOSAL.AUTHOR), description,
                row.get(PROPOSAL.CHAPTER_COUNT), row.get(PROPOSAL.ADULT), siteName(source, row.get(PROPOSAL.SOURCE_URL)),
                row.get(ACCOUNT.NICK), row.get(PROPOSAL.CREATED_AT), row.get(PROPOSAL.STATE), row.get("votes", Integer.class),
                Boolean.TRUE.equals(row.get("voted", Boolean.class)),
                viewerId != null && viewerId == row.get(PROPOSAL.PROPOSED_BY),
                slug == null ? null : new Taken(slug, row.get(TEAM.HANDLE)), row.get(PROPOSAL.SOURCE_URL), row.get(PROPOSAL.COMMENT),
                readable(source));
    }

    private static String siteName(String source, String url) {
        if ("syosetu".equals(source)) {
            return "Syosetu";
        }
        if ("link".equals(source) && url != null) {
            String host = java.net.URI.create(url.strip().replace(" ", "%20")).getHost();
            return host == null ? null : host.toLowerCase(java.util.Locale.ROOT).replaceFirst("^www\\.", "");
        }
        return "manual".equals(source) ? null : source;
    }

    void vote(Viewer viewer, long id) {
        open(id);
        db.insertInto(PROPOSAL_VOTE).set(PROPOSAL_VOTE.PROPOSAL_ID, id).set(PROPOSAL_VOTE.ACCOUNT_ID, viewer.accountId())
                .onConflictDoNothing().execute();
    }

    void unvote(Viewer viewer, long id) {
        open(id);
        db.deleteFrom(PROPOSAL_VOTE).where(PROPOSAL_VOTE.PROPOSAL_ID.eq(id), PROPOSAL_VOTE.ACCOUNT_ID.eq(viewer.accountId())).execute();
    }

    /** The proposer corrects what the model wrote (or they did); moderators may too. A null comment stays. */
    void update(Viewer viewer, long id, String title, String author, String description, String comment) {
        Record row = mayChange(viewer, id);
        String name = title == null ? "" : title.strip();
        if (name.isEmpty() || name.length() > 200) {
            throw UserFacingException.badRequest("Назва — від 1 до 200 знаків.");
        }
        String writer = author == null ? "" : author.strip();
        String text = description == null ? "" : description.strip();
        String note = comment == null ? row.get(PROPOSAL.COMMENT) : comment.strip();
        if (writer.length() > 100 || text.length() > 5_000 || note.length() > 2_000) {
            throw UserFacingException.badRequest("Автор — до 100 знаків, опис — до 5 000, коментар — до 2 000.");
        }
        db.update(PROPOSAL).set(PROPOSAL.TITLE, name).set(PROPOSAL.AUTHOR, writer).set(PROPOSAL.COMMENT, note)
                .set(PROPOSAL.DESCRIPTION, JSONB.valueOf(json.writeValueAsString(Preparation.paragraphs(text))))
                .set(PROPOSAL.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(PROPOSAL.ID.eq(row.get(PROPOSAL.ID))).execute();
    }

    void remove(Viewer viewer, long id) {
        mayChange(viewer, id);
        db.update(PROPOSAL).set(PROPOSAL.STATE, "removed").set(PROPOSAL.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(PROPOSAL.ID.eq(id)).execute();
    }

    /**
     * «Беру перекладати»: the team's edition made from the proposal, with the page as the
     * proposer left it; voters hear about it.
     */
    EditionRef take(Viewer viewer, long id, long teamId) {
        Record row = open(id);
        List<Block> description = json.readValue(row.get(PROPOSAL.DESCRIPTION).data(), BLOCKS);
        EditionRef ref;
        if (readable(row.get(PROPOSAL.SOURCE))) {
            SourceLink link = new SourceLink(row.get(PROPOSAL.SOURCE), row.get(PROPOSAL.SOURCE_KEY), row.get(PROPOSAL.SOURCE_URL),
                    row.get(PROPOSAL.ADULT));
            SourceNovel novel = sources.novel(link);
            ref = preparation.importWith(novel, new Preparation.Metadata(row.get(PROPOSAL.TITLE), row.get(PROPOSAL.AUTHOR), description),
                    teamId);
        } else {
            // A site the site cannot read: the team translates by hand; the link stays on the novel's page.
            ref = catalog.createNovel(new NewNovel(row.get(PROPOSAL.TITLE), row.get(PROPOSAL.AUTHOR), description, List.of(), "human",
                    row.get(PROPOSAL.ADULT), teamId, null, row.get(PROPOSAL.SOURCE_URL)));
        }
        // Listeners hear the event once the change is committed, so both go in one transaction.
        transactions.executeWithoutResult(status -> taken(viewer, id, row, ref, teamId));
        return ref;
    }

    private void taken(Viewer viewer, long id, Record row, EditionRef ref, long teamId) {
        int changed = db.update(PROPOSAL).set(PROPOSAL.STATE, "taken").set(PROPOSAL.EDITION_ID, ref.editionId())
                .set(PROPOSAL.TAKEN_BY, viewer.accountId()).set(PROPOSAL.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(PROPOSAL.ID.eq(id), PROPOSAL.STATE.eq("open")).execute();
        if (changed == 1) {
            List<Long> voters = db.select(PROPOSAL_VOTE.ACCOUNT_ID).from(PROPOSAL_VOTE)
                    .where(PROPOSAL_VOTE.PROPOSAL_ID.eq(id), PROPOSAL_VOTE.ACCOUNT_ID.ne(viewer.accountId()))
                    .fetch(PROPOSAL_VOTE.ACCOUNT_ID);
            String handle = db.select(TEAM.HANDLE).from(TEAM).where(TEAM.ID.eq(teamId)).fetchSingle(TEAM.HANDLE);
            events.publishEvent(new ProposalTaken(id, row.get(PROPOSAL.TITLE), ref.novelSlug(), handle, viewer.accountId(), voters));
        }
    }

    private Record open(long id) {
        Record row = db.select(PROPOSAL.asterisk()).from(PROPOSAL).where(PROPOSAL.ID.eq(id)).fetchOptional()
                .orElseThrow(() -> UserFacingException.notFound("Такої пропозиції немає."));
        if (!"open".equals(row.get(PROPOSAL.STATE))) {
            throw UserFacingException.conflict("Цю новелу вже взяли перекладати.");
        }
        return row;
    }

    private Record mayChange(Viewer viewer, long id) {
        Record row = open(id);
        boolean staff = viewer.role() != SiteRole.READER;
        if (!staff && row.get(PROPOSAL.PROPOSED_BY) != viewer.accountId()) {
            throw new UserFacingException(HttpStatus.FORBIDDEN, "Змінювати пропозицію може лише той, хто її зробив.");
        }
        return row;
    }
}
