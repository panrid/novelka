package space.panrid.novelka.autotranslate.internal;

import static space.panrid.novelka.jooq.Tables.MY_MODEL_SET;

import java.util.List;
import java.util.Optional;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.web.UserFacingException;

/**
 * «Мої набори» (етап 19, п. 8): sets of models a person saved after changing the models of a
 * quality's steps. People pick models only from those the site's qualities use, so the site
 * owner keeps the say over which models run.
 */
@Component
class MySets {

    private static final int MAX = 20;

    private final DSLContext db;
    private final Presets presets;

    MySets(DSLContext db, Presets presets) {
        this.db = db;
        this.presets = presets;
    }

    /** @param basedOn the site's set it was changed from, or null */
    record MySet(long id, String name, Long basedOn, String analyze, String translate, String proofread) {

        Jobs.Models models() {
            return new Jobs.Models(analyze, translate, proofread, proofread != null);
        }
    }

    List<MySet> of(long accountId) {
        return db.selectFrom(MY_MODEL_SET).where(MY_MODEL_SET.ACCOUNT_ID.eq(accountId)).orderBy(MY_MODEL_SET.NAME)
                .fetch(r -> new MySet(r.getId(), r.getName(), r.getBasedOn(), r.getAnalyzeModel(), r.getTranslateModel(), r.getProofreadModel()));
    }

    Optional<MySet> find(long accountId, long id) {
        return of(accountId).stream().filter(set -> set.id() == id).findFirst();
    }

    /** The models people may pick for a step: those the site's qualities use. */
    List<String> peopleModels() {
        return presets.forPeople().stream()
                .flatMap(preset -> java.util.stream.Stream.of(preset.analyze(), preset.translate(), preset.proofread()))
                .filter(java.util.Objects::nonNull).distinct().sorted().toList();
    }

    /** Null when a person may run these models; otherwise why not. */
    String refusal(Jobs.Models models) {
        if (models == null) {
            return null;
        }
        List<String> allowed = peopleModels();
        for (String model : java.util.Arrays.asList(models.analyze(), models.translate(), models.proofread())) {
            if (model != null && !model.isBlank() && !allowed.contains(model.strip())) {
                return "Модель «%s» сайт людям не пропонує: оберіть з моделей якостей сайту.".formatted(model.strip());
            }
        }
        return null;
    }

    long create(long accountId, String name, Long basedOn, Jobs.Models models) {
        String title = name(name);
        if (models == null || blank(models.analyze()) || blank(models.translate())) {
            throw UserFacingException.badRequest("Набору потрібні моделі словника й перекладу.");
        }
        String refused = refusal(models);
        if (refused != null) {
            throw UserFacingException.badRequest(refused);
        }
        if (basedOn != null && presets.find(basedOn).filter(preset -> preset.quality() != null).isEmpty()) {
            throw UserFacingException.badRequest("Цей набір моделей сайт людям не пропонує.");
        }
        if (db.fetchCount(MY_MODEL_SET, MY_MODEL_SET.ACCOUNT_ID.eq(accountId)) >= MAX) {
            throw UserFacingException.badRequest("Своїх наборів може бути щонайбільше %d. Видаліть непотрібний.".formatted(MAX));
        }
        String proofread = blank(models.proofread()) || Boolean.FALSE.equals(models.proofreadEnabled()) ? null : models.proofread().strip();
        taken(accountId, title, null);
        return db.insertInto(MY_MODEL_SET)
                .set(MY_MODEL_SET.ACCOUNT_ID, accountId)
                .set(MY_MODEL_SET.NAME, title)
                .set(MY_MODEL_SET.BASED_ON, basedOn)
                .set(MY_MODEL_SET.ANALYZE_MODEL, models.analyze().strip())
                .set(MY_MODEL_SET.TRANSLATE_MODEL, models.translate().strip())
                .set(MY_MODEL_SET.PROOFREAD_MODEL, proofread)
                .returning(MY_MODEL_SET.ID).fetchOne(MY_MODEL_SET.ID);
    }

    private void taken(long accountId, String title, Long except) {
        if (db.fetchExists(MY_MODEL_SET, MY_MODEL_SET.ACCOUNT_ID.eq(accountId), MY_MODEL_SET.NAME.eq(title),
                except == null ? org.jooq.impl.DSL.noCondition() : MY_MODEL_SET.ID.ne(except))) {
            throw UserFacingException.badRequest("Набір «%s» у вас уже є.".formatted(title));
        }
    }

    void rename(long accountId, long id, String name) {
        String title = name(name);
        taken(accountId, title, id);
        int changed = db.update(MY_MODEL_SET).set(MY_MODEL_SET.NAME, title)
                .where(MY_MODEL_SET.ID.eq(id), MY_MODEL_SET.ACCOUNT_ID.eq(accountId)).execute();
        if (changed == 0) {
            throw UserFacingException.notFound("Такого набору немає.");
        }
    }

    void delete(long accountId, long id) {
        if (db.deleteFrom(MY_MODEL_SET).where(MY_MODEL_SET.ID.eq(id), MY_MODEL_SET.ACCOUNT_ID.eq(accountId)).execute() == 0) {
            throw UserFacingException.notFound("Такого набору немає.");
        }
    }

    private static String name(String name) {
        String title = name == null ? "" : name.strip();
        if (title.isEmpty() || title.length() > 60) {
            throw UserFacingException.badRequest("Назвіть набір (до 60 знаків).");
        }
        return title;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
