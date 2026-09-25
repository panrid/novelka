package space.panrid.novelka.account.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;

import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.account.SiteRole;
import space.panrid.novelka.account.SiteRoles;
import space.panrid.novelka.platform.web.UserFacingException;

@Service
class SiteRoleService implements SiteRoles {

    private final DSLContext db;

    SiteRoleService(DSLContext db) {
        this.db = db;
    }

    @Override
    @Transactional
    public void setRole(long accountId, SiteRole role) {
        if (role == SiteRole.OWNER) {
            throw UserFacingException.badRequest("Власник сайту один; цю роль не передають тут.");
        }
        db.update(ACCOUNT).set(ACCOUNT.SITE_ROLE, role.code()).where(ACCOUNT.ID.eq(accountId)).execute();
    }
}
