import { useMutation } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { Flag } from 'lucide-react';
import { useMe } from '../auth/me';
import { messagingApi } from '../inbox/api';
import { askText } from '../ui/ask';
import { showError, showInfo } from '../ui/toast';
import styles from './reportEdition.module.css';

/**
 * «Поскаржитися» on a translation: what it is, its text, its cover. From the reader the reason
 * names the chapter. Moderators see it in their queue; administrators may hide the translation.
 */
export function ReportEdition({ editionId, chapterLabel }: { editionId: number; chapterLabel?: string | undefined }) {
    const me = useMe();
    const navigate = useNavigate();
    const report = useMutation({
        mutationFn: (reason: string) => messagingApi.report('edition', editionId, chapterLabel ? `Глава ${chapterLabel}: ${reason}` : reason),
        onSuccess: () => showInfo('Скаргу надіслано. Модератори її переглянуть.'),
        onError: (error: Error) => showError(error.message),
    });
    const ask = () => {
        if (!me) {
            void navigate({ to: '/login', search: { next: window.location.pathname + window.location.search } });
            return;
        }
        void askText({
            title: chapterLabel ? `Поскаржитися на главу ${chapterLabel}` : 'Поскаржитися на переклад',
            label: 'Що не так?', hint: 'Причину побачать лише модератори.', multiline: true, confirmLabel: 'Надіслати скаргу',
        }).then((reason) => { if (reason) report.mutate(reason); });
    };
    return (
        <button type="button" className={styles.report} onClick={ask} disabled={report.isPending}>
            <Flag size={14} aria-hidden /> {chapterLabel ? 'Поскаржитися на главу' : 'Поскаржитися на переклад'}
        </button>
    );
}
