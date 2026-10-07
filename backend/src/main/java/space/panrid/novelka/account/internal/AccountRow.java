package space.panrid.novelka.account.internal;

import java.time.OffsetDateTime;

import space.panrid.novelka.account.SiteRole;

record AccountRow(long id, String nick, String email, OffsetDateTime emailVerifiedAt, String passwordHash,
        SiteRole role, OffsetDateTime nickChangedAt, String googleSub, boolean passwordSet) {

    boolean emailVerified() {
        return emailVerifiedAt != null;
    }
}
