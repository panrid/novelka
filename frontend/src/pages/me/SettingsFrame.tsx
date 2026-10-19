import { Link, useRouterState } from '@tanstack/react-router';
import type { ReactNode } from 'react';
import { useMe } from '../../auth/me';
import { useMyShahs } from '../../ledger/api';
import styles from './settings.module.css';

type Section = { to: string; label: string; signedIn: boolean };

const SECTIONS: readonly Section[] = [
    { to: '/me/settings', label: 'Профіль', signedIn: true },
    { to: '/me/settings/appearance', label: 'Вигляд сайту', signedIn: false },
    { to: '/me/settings/reader', label: 'Читання', signedIn: false },
    { to: '/me/settings/notifications', label: 'Сповіщення', signedIn: true },
    { to: '/me/settings/security', label: 'Вхід і безпека', signedIn: true },
    { to: '/me/settings/shahs', label: 'Шаги', signedIn: true },
];

/** Whether «Шаги» belongs in the settings: the owner always, others once шаги came their way. */
export function useHasShahs() {
    const me = useMe();
    const shahs = useMyShahs();
    return me?.role === 'owner'
        || Boolean(shahs.data && (shahs.data.available > 0 || shahs.data.reserved > 0 || shahs.data.history.length > 0));
}

/**
 * «Налаштування» as one place (етап 20, крок 6; `Settings.dc.html`): the sections on the left,
 * the chosen one in a panel beside them. A phone shows the sections as a row to scroll above it.
 * Guests choose only the look; it is kept in their browser.
 */
export function SettingsFrame({ title, children }: { title: string; children: ReactNode }) {
    const me = useMe();
    const hasShahs = useHasShahs();
    const path = useRouterState({ select: (state) => state.location.pathname.replace(/\/$/, '') });
    const shown = SECTIONS.filter((section) => (me || !section.signedIn) && (section.to !== '/me/settings/shahs' || hasShahs));
    return (
        <section className={styles.settings} data-wide>
            <nav className={styles.sections} aria-label="Розділи налаштувань">
                <span className={styles.heading}>Налаштування</span>
                <div className={styles.sectionList}>
                    {shown.map((section) => (
                        <Link key={section.to} to={section.to} className={`${styles.section} ${path === section.to ? styles.sectionOn : ''}`}
                            aria-current={path === section.to ? 'page' : undefined}>
                            {section.label}
                        </Link>
                    ))}
                </div>
            </nav>
            <div className={styles.panel}>
                <h1 className={styles.title}>{title}</h1>
                {children}
            </div>
        </section>
    );
}
