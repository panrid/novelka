/**
 * Guests keep their place in this browser; signed-in readers sync it through the server.
 * Keys are per novel and team, so two translations of one novel do not mix.
 */
/** {@code label}: the number readers see (see chapterHeading); absent means the position. */
type Saved = { number: number; position: number; label?: string | null };

const key = (slug: string, team: string) => `novelka:progress:${slug}:${team}`;

export function localProgress(slug: string, team: string): Saved | null {
    try {
        const raw = localStorage.getItem(key(slug, team));
        return raw ? (JSON.parse(raw) as Saved) : null;
    } catch {
        return null;
    }
}

export function saveLocalProgress(slug: string, team: string, saved: Saved) {
    try {
        localStorage.setItem(key(slug, team), JSON.stringify(saved));
    } catch {
        // Private mode or full storage: reading still works, the place is just not remembered.
    }
}
