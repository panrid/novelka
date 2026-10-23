package space.panrid.novelka.studio;

import java.util.Map;

/**
 * A work was created or its data changed in the Studio — on the site or through the API (етап 22.9).
 * Published in the change's transaction. Nobody records it yet; «Журнал дій» or anything else that
 * should know listens here, so the Studio's code stays as it is.
 *
 * @param editionId the translation (or own work) changed
 * @param actorId   who changed it
 * @param changes   what was set, by field: {@code title}, {@code slug}, {@code status}, {@code cover}…; a
 *                  removed value is null. For {@link Action#CREATED} — the work as created
 */
public record NovelChanged(long editionId, long actorId, Action action, Map<String, Object> changes) {

    public enum Action {
        CREATED, UPDATED, SLUG, COVER
    }
}
