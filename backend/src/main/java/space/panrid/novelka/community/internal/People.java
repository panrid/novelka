package space.panrid.novelka.community.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import space.panrid.novelka.media.Images;
import space.panrid.novelka.media.StoredImage;

/** Nicks and avatars of authors, a whole page at a time. */
@Component
class People {

    record Person(String nick, String avatarUrl) {
    }

    private final DSLContext db;
    private final Images images;

    People(DSLContext db, Images images) {
        this.db = db;
        this.images = images;
    }

    Map<Long, Person> of(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        var rows = db.select(ACCOUNT.ID, ACCOUNT.NICK, ACCOUNT.AVATAR_IMAGE_ID).from(ACCOUNT).where(ACCOUNT.ID.in(accountIds)).fetch();
        Map<Long, StoredImage> avatars = images.findAll(rows.stream().map(r -> r.value3()).filter(java.util.Objects::nonNull).toList());
        Map<Long, Person> out = new HashMap<>();
        rows.forEach(r -> {
            StoredImage avatar = r.value3() == null ? null : avatars.get(r.value3());
            out.put(r.value1(), new Person(r.value2(), avatar == null ? null : avatar.url(96)));
        });
        return out;
    }
}
