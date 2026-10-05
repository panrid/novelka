package space.panrid.novelka.achievement.internal;

import static space.panrid.novelka.jooq.Tables.ACCOUNT;

import java.util.List;

import org.jooq.DSLContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import space.panrid.novelka.account.CurrentUser;
import space.panrid.novelka.platform.web.UserFacingException;

@RestController
class AchievementController {

    private final Achievements achievements;
    private final CurrentUser currentUser;
    private final DSLContext db;

    AchievementController(Achievements achievements, CurrentUser currentUser, DSLContext db) {
        this.achievements = achievements;
        this.currentUser = currentUser;
        this.db = db;
    }

    /** The level and badges on a profile; the person's own are counted afresh when they look. */
    @GetMapping("/api/users/{nick}/achievements")
    Achievements.Profile profile(@PathVariable String nick) {
        Long accountId = db.select(ACCOUNT.ID).from(ACCOUNT)
                .where(ACCOUNT.NICK_KEY.eq(nick.strip().toLowerCase(java.util.Locale.ROOT))).fetchOne(ACCOUNT.ID);
        if (accountId == null) {
            throw UserFacingException.notFound("Такої людини немає.");
        }
        if (currentUser.viewer().filter(viewer -> viewer.accountId() == accountId).isPresent()) {
            achievements.sweep(List.of(accountId));
        }
        return achievements.profile(accountId);
    }
}
