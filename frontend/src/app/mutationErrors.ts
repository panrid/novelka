import { MutationCache } from '@tanstack/react-query';
import { showError } from '../ui/toast';

declare module '@tanstack/react-query' {
    interface Register {
        mutationMeta: { errorToast?: boolean };
    }
}

/** Errors of mutations marked meta: { errorToast: true } go to the toast at the bottom of the screen. */
export function mutationCache() {
    return new MutationCache({
        onError: (error, _variables, _context, mutation) => {
            if (mutation.meta?.errorToast) showError(error.message);
        },
    });
}
