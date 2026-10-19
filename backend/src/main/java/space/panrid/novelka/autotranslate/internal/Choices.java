package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.AUTOTRANSLATE_CHOICE;

import java.util.List;
import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.web.UserFacingException;

/**
 * «Запам'ятати для наступних» (етап 19): the set of models, the steps and «Одразу опублікувати» a
 * person picked, offered first in their next runs of any translation.
 */
@Component
class Choices {

    private static final List<String> STEPS = List.of("analyze", "translate", "proofread");

    private final DSLContext db;

    Choices(DSLContext db) {
        this.db = db;
    }

    /** @param preset null: the site's models, or the set that was picked is gone */
    record Choice(Long preset, List<String> steps, boolean publish) {
    }

    Optional<Choice> of(long accountId) {
        return db.selectFrom(AUTOTRANSLATE_CHOICE).where(AUTOTRANSLATE_CHOICE.ACCOUNT_ID.eq(accountId))
                .fetchOptional(r -> new Choice(r.getPresetId(), List.of(r.getSteps()), r.getPublish()));
    }

    void remember(long accountId, Choice choice) {
        List<String> steps = choice.steps() == null ? List.of() : choice.steps();
        if (steps.isEmpty() || !STEPS.containsAll(steps)) {
            throw UserFacingException.badRequest("Оберіть етапи: словник, переклад чи вичитку.");
        }
        String[] kept = STEPS.stream().filter(steps::contains).toArray(String[]::new);
        db.insertInto(AUTOTRANSLATE_CHOICE)
                .set(AUTOTRANSLATE_CHOICE.ACCOUNT_ID, accountId)
                .set(AUTOTRANSLATE_CHOICE.PRESET_ID, choice.preset())
                .set(AUTOTRANSLATE_CHOICE.STEPS, kept)
                .set(AUTOTRANSLATE_CHOICE.PUBLISH, choice.publish())
                .onConflict(AUTOTRANSLATE_CHOICE.ACCOUNT_ID).doUpdate()
                .set(AUTOTRANSLATE_CHOICE.PRESET_ID, choice.preset())
                .set(AUTOTRANSLATE_CHOICE.STEPS, kept)
                .set(AUTOTRANSLATE_CHOICE.PUBLISH, choice.publish())
                .execute();
    }
}
