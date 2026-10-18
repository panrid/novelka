package space.panrid.novelka.studio.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;
import static space.panrid.novelka.jooq.Tables.DELETION_CODE;
import static space.panrid.novelka.jooq.Tables.EDITION;
import static space.panrid.novelka.jooq.Tables.JOB;
import static space.panrid.novelka.jooq.Tables.NOVEL;
import static space.panrid.novelka.jooq.Tables.TEAM;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.access.AccessPolicy;
import space.panrid.novelka.account.AccountMessenger;
import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.Viewer;
import space.panrid.novelka.platform.SiteProperties;
import space.panrid.novelka.platform.mail.Mail;
import space.panrid.novelka.platform.mail.Mailer;
import space.panrid.novelka.platform.tx.AfterCommit;
import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.text.Chapters;

/**
 * «Видалити переклад»: a code goes to the person's email and Telegram, and only with it the
 * translation goes to the bin — hidden from readers and from the Studio list, back with one tap
 * for 30 days. Then its chapters are erased; the history of шаги and runs stays.
 */
@Component
class EditionTrash {

    static final Duration KEPT = Duration.ofDays(30);
    static final Duration CODE_LIVES = Duration.ofMinutes(15);
    static final int ATTEMPTS = 5;
    /** The reason moderation shows for a translation hidden because its team deleted it. */
    static final String TRASH = "trash";

    private static final Logger log = LoggerFactory.getLogger(EditionTrash.class);

    private final DSLContext db;
    private final AccessPolicy access;
    private final Chapters chapters;
    private final Mailer mailer;
    private final ObjectProvider<AccountMessenger> messengers;
    private final SiteProperties site;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    EditionTrash(DSLContext db, AccessPolicy access, Chapters chapters, Mailer mailer, ObjectProvider<AccountMessenger> messengers,
            SiteProperties site, Clock clock) {
        this.db = db;
        this.access = access;
        this.chapters = chapters;
        this.mailer = mailer;
        this.messengers = messengers;
        this.site = site;
        this.clock = clock;
    }

    /** The team's owner, or the site's owner. */
    Viewer requireDeleter(long editionId) {
        Viewer viewer = access.requireSignedIn();
        if (viewer.role() == SiteRole.OWNER) {
            if (!db.fetchExists(EDITION, EDITION.ID.eq(editionId))) {
                throw UserFacingException.notFound("Такого перекладу немає.");
            }
            return viewer;
        }
        // Not requireEditionOwner: that one refuses a translation in the bin, and this brings it back.
        if (!db.fetchExists(EDITION, EDITION.ID.eq(editionId))) {
            throw UserFacingException.notFound("Такого перекладу немає.");
        }
        return access.editionRole(editionId).filter(role -> role.role().manages()).map(role -> role.viewer())
                .orElseThrow(() -> new UserFacingException(HttpStatus.FORBIDDEN, "Видалити переклад може лише власник команди."));
    }

    record Sent(String email, int minutes) {
    }

    /** A new code, the old one forgotten. */
    @Transactional
    Sent requestCode(long editionId) {
        Viewer viewer = requireDeleter(editionId);
        String title = title(editionId);
        String code = "%06d".formatted(random.nextInt(1_000_000));
        db.insertInto(DELETION_CODE).set(DELETION_CODE.ACCOUNT_ID, viewer.accountId()).set(DELETION_CODE.EDITION_ID, editionId)
                .set(DELETION_CODE.CODE_HASH, hash(code)).set(DELETION_CODE.EXPIRES_AT, now().plus(CODE_LIVES))
                .onConflict(DELETION_CODE.ACCOUNT_ID, DELETION_CODE.EDITION_ID).doUpdate()
                .set(DELETION_CODE.CODE_HASH, hash(code)).set(DELETION_CODE.EXPIRES_AT, now().plus(CODE_LIVES))
                .set(DELETION_CODE.ATTEMPTS, 0).execute();
        String email = db.select(ACCOUNT.EMAIL).from(ACCOUNT).where(ACCOUNT.ID.eq(viewer.accountId())).fetchOne(ACCOUNT.EMAIL);
        String text = """
                Код для видалення перекладу «%s»: %s

                Він діє %d хвилин. Переклад піде в кошик, і 30 днів його можна буде відновити в Студії.
                Якщо ви нічого не видаляли — просто не вводьте код і змініть пароль.""".formatted(title, code, CODE_LIVES.toMinutes());
        AfterCommit.run(() -> {
            if (email != null) {
                mailer.send(new Mail(email, "Код для видалення перекладу на Новелці", text));
            }
            messengers.orderedStream().forEach(messenger -> messenger.tell(viewer.accountId(), text, site.link("/studio/" + editionId)));
        });
        return new Sent(email == null ? null : masked(email), (int) CODE_LIVES.toMinutes());
    }

    /** With the right code the translation goes to the bin. */
    @Transactional
    void confirm(long editionId, String code) {
        Viewer viewer = requireDeleter(editionId);
        var row = db.selectFrom(DELETION_CODE)
                .where(DELETION_CODE.ACCOUNT_ID.eq(viewer.accountId()), DELETION_CODE.EDITION_ID.eq(editionId)).forUpdate().fetchOne();
        if (row == null || row.getExpiresAt().isBefore(now()) || row.getAttempts() >= ATTEMPTS) {
            throw UserFacingException.badRequest("Код застарів. Надішліть новий.");
        }
        if (code == null || !MessageDigest.isEqual(hash(code.strip()).getBytes(StandardCharsets.UTF_8), row.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            db.update(DELETION_CODE).set(DELETION_CODE.ATTEMPTS, DELETION_CODE.ATTEMPTS.plus(1))
                    .where(DELETION_CODE.ACCOUNT_ID.eq(viewer.accountId()), DELETION_CODE.EDITION_ID.eq(editionId)).execute();
            throw UserFacingException.badRequest("Код не той. Перевірте лист чи Telegram.");
        }
        db.deleteFrom(DELETION_CODE).where(DELETION_CODE.EDITION_ID.eq(editionId)).execute();
        OffsetDateTime now = now();
        db.update(EDITION).set(EDITION.DELETED_AT, now).set(EDITION.DELETED_BY, viewer.accountId())
                .where(EDITION.ID.eq(editionId)).execute();
        // Readers lose it as they lose a translation moderation hid; one hidden already stays as it was.
        db.update(EDITION).set(EDITION.HIDDEN_AT, now).set(EDITION.HIDDEN_BY, viewer.accountId()).set(EDITION.HIDDEN_REASON, TRASH)
                .where(EDITION.ID.eq(editionId), EDITION.HIDDEN_AT.isNull()).execute();
        // Runs of autotranslate stop after their chapter; what they hold of шаги is settled as for «Скасувати».
        db.update(JOB).set(JOB.STATE, "cancelled").where(JOB.EDITION_ID.eq(editionId), JOB.STATE.in("queued", "running")).execute();
    }

    record Trashed(long editionId, String title, String teamName, OffsetDateTime deletedAt, OffsetDateTime erasedAt) {
    }

    /** Translations of the teams the viewer owns in the bin (all of them for the site's owner). */
    List<Trashed> list() {
        Viewer viewer = access.requireSignedIn();
        var mine = TEAM.OWNER_ID.eq(viewer.accountId());
        return db.select(EDITION.ID, DSL.coalesce(EDITION.TITLE, NOVEL.TITLE), TEAM.NAME, EDITION.DELETED_AT).from(EDITION)
                .join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID)).join(TEAM).on(TEAM.ID.eq(EDITION.TEAM_ID))
                .where(EDITION.DELETED_AT.isNotNull(), EDITION.PURGED_AT.isNull(), viewer.role() == SiteRole.OWNER ? DSL.trueCondition() : mine)
                .orderBy(EDITION.DELETED_AT.desc())
                .fetch(r -> new Trashed(r.value1(), r.value2(), r.value3(), r.value4(), r.value4().plus(KEPT)));
    }

    @Transactional
    void restore(long editionId) {
        requireDeleter(editionId);
        int changed = db.update(EDITION).setNull(EDITION.DELETED_AT).setNull(EDITION.DELETED_BY)
                .where(EDITION.ID.eq(editionId), EDITION.DELETED_AT.isNotNull(), EDITION.PURGED_AT.isNull()).execute();
        if (changed == 0) {
            throw new UserFacingException(HttpStatus.CONFLICT, "Цього перекладу вже немає в кошику.", "not-in-trash");
        }
        db.update(EDITION).setNull(EDITION.HIDDEN_AT).setNull(EDITION.HIDDEN_BY).setNull(EDITION.HIDDEN_REASON)
                .where(EDITION.ID.eq(editionId), EDITION.HIDDEN_REASON.eq(TRASH)).execute();
    }

    /** Once an hour: what lay in the bin for 30 days is erased. */
    @Scheduled(initialDelay = 600_000, fixedDelay = 3_600_000)
    void purge() {
        OffsetDateTime before = now().minus(KEPT);
        int chaptersErased = chapters.purgeChapters(before);
        List<Long> editions = db.select(EDITION.ID).from(EDITION)
                .where(EDITION.DELETED_AT.lt(before), EDITION.PURGED_AT.isNull()).fetch(EDITION.ID);
        for (long editionId : editions) {
            chapters.purgeEdition(editionId);
            db.update(EDITION).set(EDITION.PURGED_AT, now()).where(EDITION.ID.eq(editionId)).execute();
        }
        if (chaptersErased > 0 || !editions.isEmpty()) {
            log.info("Bin emptied: {} chapters, {} translations", chaptersErased, editions.size());
        }
    }

    private String title(long editionId) {
        return db.select(DSL.coalesce(EDITION.TITLE, NOVEL.TITLE)).from(EDITION).join(NOVEL).on(NOVEL.ID.eq(EDITION.NOVEL_ID))
                .where(EDITION.ID.eq(editionId)).fetchOne(0, String.class);
    }

    /** d***@gmail.com: enough to know where to look. */
    static String masked(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? email : email.charAt(0) + "***" + email.substring(at);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
