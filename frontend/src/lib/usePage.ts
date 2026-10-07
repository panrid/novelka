import { useNavigate, useSearch } from '@tanstack/react-router';

/** Lists show 20 rows to a page (owner's rule, 2026-10-07); a few exceptions say so where they are. */
export const PAGE_SIZE = 20;

/**
 * The list's page, kept in the address (?page=3): «Назад» comes back to the same page and a
 * link can point at it. Page 1 leaves the address clean. The key lets two lists on one
 * page keep their own (?page= and ?chapters=).
 */
export function usePage(key = 'page'): [number, (page: number) => void] {
    const search = useSearch({ strict: false }) as Record<string, unknown>;
    const navigate = useNavigate();
    const raw = Number(search[key]);
    const page = Number.isInteger(raw) && raw >= 1 ? raw : 1;
    const setPage = (next: number) => {
        void navigate({
            to: '.',
            search: (previous: Record<string, unknown>) => ({ ...previous, [key]: next > 1 ? next : undefined }),
        } as never);
        window.scrollTo?.(0, 0);
    };
    return [page, setPage];
}

/**
 * A short list the server sends whole, shown 20 to a page here: members of a team, a person's
 * works. The page outside the list (a list that shrank) falls back to its last page.
 */
export function usePaged<T>(items: readonly T[] | undefined, key = 'page') {
    const [page, setPage] = usePage(key);
    const total = items?.length ?? 0;
    const at = Math.min(page, Math.max(1, Math.ceil(total / PAGE_SIZE)));
    return { shown: items?.slice((at - 1) * PAGE_SIZE, at * PAGE_SIZE) ?? [], page: at, total, setPage };
}
