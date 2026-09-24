import { useMutation } from '@tanstack/react-query';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { useEffect, useRef } from 'react';
import type { Me } from '../../auth/me';
import { useSetMe } from '../../auth/me';
import { Notice } from '../../ui/Notice';
import styles from '../pages.module.css';

/**
 * Opens a link from a letter: sends its token once, signs the person in and moves on.
 * The ref survives React's development double-mount, so the one-time link is spent once.
 */
export function TokenPage({ title, action, then }: { title: string; action: (token: string) => Promise<Me>; then: string }) {
    const { token }: { token?: string } = useSearch({ strict: false });
    const navigate = useNavigate();
    const setMe = useSetMe();
    const sent = useRef(false);
    const open = useMutation({
        mutationFn: action,
        onSuccess: (me) => {
            setMe(me);
            void navigate({ to: then, replace: true });
        },
    });

    useEffect(() => {
        if (token && !sent.current) {
            sent.current = true;
            open.mutate(token);
        }
    }, [token, open]);

    return (
        <section className={styles.narrow}>
            <h1 className={styles.title}>{title}</h1>
            {!token && <Notice tone="error">У посиланні немає коду. Відкрийте його з листа ще раз.</Notice>}
            {open.isPending && <p className={styles.muted}>Хвилинку…</p>}
            {open.isError && (
                <>
                    <Notice tone="error">{open.error.message}</Notice>
                    <div className={styles.links}>
                        <Link to="/login">До входу</Link>
                    </div>
                </>
            )}
        </section>
    );
}
