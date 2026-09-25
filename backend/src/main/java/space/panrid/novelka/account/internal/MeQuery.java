package space.panrid.novelka.account.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.media.Images;

@Component
class MeQuery {

    /** What anyone may see about a person. */
    record PublicProfile(String nick, String avatarUrl, String bio, LocalDate memberSince) {
    }

    private final DSLContext db;
    private final Images images;

    MeQuery(DSLContext db, Images images) {
        this.db = db;
        this.images = images;
    }

    Optional<Me> find(long id) {
        return db.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(id)).fetchOptional(r -> new Me(
                r.getId(), r.getNick(), r.getEmail(), r.getEmailVerifiedAt() != null, r.getSiteRole(), r.getBio(),
                avatar(r.getAvatarImageId()), r.getDmPolicy(), r.getShowReading(), r.getAdultConfirmedAt() != null,
                // Shags-or-dollars only matters to the site owner (рішення 21).
                SiteRole.fromCode(r.getSiteRole()) == SiteRole.OWNER ? r.getShowShah() : true, r.getStudioInMenu()));
    }

    Optional<PublicProfile> publicProfile(long id) {
        return db.selectFrom(ACCOUNT).where(ACCOUNT.ID.eq(id)).fetchOptional(r -> new PublicProfile(
                r.getNick(), avatar(r.getAvatarImageId()), r.getBio(),
                r.getCreatedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate()));
    }

    private String avatar(Long imageId) {
        return imageId == null ? null : images.find(imageId).map(image -> image.url(256)).orElse(null);
    }
}
