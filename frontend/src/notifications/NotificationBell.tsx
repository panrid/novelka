import { useEffect, useRef, useState } from 'react';
import { getJson, mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';

interface Notice {
    id: number; kind: string; novel_id: string | null; novel_title: string | null;
    chapter: number | null; task_id: string | null; entry_count: number | null; created_at: string | number; read: boolean;
    task_operation?: string | null; task_first?: number | null; task_last?: number | null; task_job_chapter?: number | null;
}
interface Feed { items: Notice[]; unread: number; latestId: number; nextCursor: number }

// Accusative for "… завершено/перервано", nominative with a matching verb for failures.
const taskPhrases: Record<string, { done: string; failed: string }> = {
    import: { done: 'Імпорт', failed: 'Імпорт зупинився' },
    translate: { done: 'Переклад', failed: 'Переклад зупинився' },
    proofread: { done: 'Вичитку', failed: 'Вичитка зупинилася' },
    resume: { done: 'Відновлення перекладу', failed: 'Відновлення перекладу зупинилося' },
};

function taskChapters(item: Notice) {
    if (item.task_operation === 'resume') return item.task_job_chapter ? `глава ${item.task_job_chapter}` : '';
    const first = item.task_first, last = item.task_last ?? first;
    if (first == null || last == null) return '';
    if (item.task_operation === 'import' && first === 0 && last === 0) return 'лише опис новели';
    return first === last ? `глава ${first}` : `глави ${first}–${last}`;
}

function taskTitle(item: Notice) {
    const phrase = taskPhrases[item.task_operation ?? ''];
    if (!phrase) return {
        task_complete: 'Завдання успішно завершено', task_failed: 'Завдання зупинилось через помилку',
        task_interrupted: 'Завдання перервано після перезапуску сервера',
    }[item.kind] ?? 'Сповіщення';
    const chapters = taskChapters(item);
    const text = item.kind === 'task_complete' ? `${phrase.done} завершено`
        : item.kind === 'task_failed' ? `${phrase.failed} через помилку` : `${phrase.done} перервано після перезапуску сервера`;
    return chapters ? `${text}: ${chapters}` : text;
}

function title(item: Notice) {
    switch (item.kind) {
        case 'chapter_published': return `Нова глава ${item.chapter}`;
        case 'glossary_added': return `Нові записи словника: ${item.entry_count}`;
        case 'task_complete': case 'task_failed': case 'task_interrupted': return taskTitle(item);
        default: return 'Сповіщення';
    }
}

function destination(item: Notice) {
    const novel = encodeURIComponent(item.novel_id ?? '');
    if (item.kind === 'chapter_published') return `/novels/${novel}/chapters/${item.chapter}`;
    if (item.kind === 'glossary_added') return `/manage?novel=${novel}&tab=glossary`;
    return `/manage?novel=${novel}&task=${encodeURIComponent(item.task_id ?? '')}`;
}

export function NotificationBell() {
    const [open, setOpen] = useState(false);
    const [data, setData] = useState<Feed>();
    const [error, setError] = useState('');
    const [cursors, setCursors] = useState<number[]>([0]);
    const before = cursors[cursors.length - 1];
    const [version, setVersion] = useState(0);
    const [toast, setToast] = useState<Notice>();
    const latest = useRef<number | undefined>(undefined);
    const container = useRef<HTMLDivElement>(null);
    const toggle = useRef<HTMLButtonElement>(null);
    const action = useAction();

    useEffect(() => {
        const controller = new AbortController();
        let fetching = false;
        const refresh = async () => {
            if (fetching || document.hidden) return;
            fetching = true;
            try {
                const feed = await getJson<Feed>('/notifications?before=' + before, controller.signal);
                if (controller.signal.aborted) return;
                if (latest.current !== undefined && feed.latestId > latest.current) {
                    const newest = feed.items.find(item => item.id > latest.current! && !item.read);
                    if (newest) setToast(newest);
                }
                latest.current = Math.max(latest.current ?? 0, feed.latestId);
                setData(feed); setError('');
            } catch (failure) {
                if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : 'Не вдалося отримати сповіщення.');
            } finally { fetching = false; }
        };
        void refresh();
        const timer = window.setInterval(() => { void refresh(); }, 15000);
        document.addEventListener('visibilitychange', refresh);
        return () => { controller.abort(); window.clearInterval(timer); document.removeEventListener('visibilitychange', refresh); };
    }, [before, version]);

    useEffect(() => {
        const dismiss = (event: PointerEvent) => { if (!container.current?.contains(event.target as Node)) setOpen(false); };
        const navigate = () => { setOpen(false); setToast(undefined); };
        document.addEventListener('pointerdown', dismiss);
        window.addEventListener('hashchange', navigate);
        return () => { document.removeEventListener('pointerdown', dismiss); window.removeEventListener('hashchange', navigate); };
    }, []);

    const visit = (item: Notice) => {
        void action.run(async () => {
            if (!item.read) await mutate(`/notifications/${item.id}/read`);
            setVersion(value => value + 1); setOpen(false); setToast(undefined);
            window.location.hash = destination(item);
        });
    };
    return <div className="notification-bell" ref={container} onKeyDown={event => {
        if (event.key === 'Escape') { setOpen(false); toggle.current?.focus(); }
    }}>
        <button type="button" ref={toggle} className="notification-toggle" aria-label={`Сповіщення${data?.unread ? `: ${data.unread} непрочитаних` : ''}`}
            aria-expanded={open} aria-controls={open ? 'notification-feed' : undefined} onClick={() => { setOpen(value => !value); setVersion(value => value + 1); setToast(undefined); }}>
            <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true"><path d="M5 17h14l-2-3V9a5 5 0 0 0-10 0v5l-2 3ZM10 20h4" /></svg>
            {!!data?.unread && <span className="notification-count" aria-hidden="true">{data.unread > 99 ? '99+' : data.unread}</span>}
        </button>
        {open && <section id="notification-feed" className="notification-feed" aria-label="Сповіщення">
            <div className="notification-heading"><h2>Сповіщення</h2><button type="button" aria-label="Закрити сповіщення" onClick={() => { setOpen(false); toggle.current?.focus(); }}>×</button></div>
            {error && <p role="alert">{error} <button onClick={() => setVersion(value => value + 1)}>Повторити</button></p>}
            <ActionNotice {...action} />
            {!data && !error && <p>Завантажуємо…</p>}
            {data && <><div className="notification-tools"><span>{data.unread} непрочитаних</span>
                <button disabled={!data.unread || action.busy} onClick={() => { void action.run(async () => {
                    await mutate('/notifications/read-all?through=' + data.latestId); setVersion(value => value + 1);
                }); }}>Прочитати всі</button></div>
                {!data.items.length && <p>Сповіщень поки немає.</p>}
                <ul>{data.items.map(item => <li key={item.id} className={item.read ? '' : 'unread'}>
                    <a href={'#' + destination(item)} onClick={event => { event.preventDefault(); if (!action.busy) visit(item); }}>
                        <strong>{title(item)}</strong><span>{item.novel_title}</span><small>{new Date(item.created_at).toLocaleString('uk-UA')}</small>
                    </a>
                    {!item.read && <button disabled={action.busy} aria-label={'Позначити прочитаним: ' + title(item)} onClick={() => { void action.run(async () => {
                        await mutate(`/notifications/${item.id}/read`); setVersion(value => value + 1);
                    }); }}>✓</button>}
                </li>)}</ul>
                {(cursors.length > 1 || data.nextCursor > 0) && <div className="notification-tools"><button disabled={cursors.length === 1} onClick={() => { setData(undefined); setCursors(value => value.slice(0, -1)); }}>Новіші</button>
                    <button disabled={!data.nextCursor} onClick={() => { setData(undefined); setCursors(value => [...value, data.nextCursor]); }}>Старіші</button></div>}
            </>}
        </section>}
        {toast && !open && <div className="notification-toast" role="status"><button type="button" onClick={() => { setOpen(true); setCursors([0]); setToast(undefined); }}>
            <strong>{title(toast)}</strong><span>{toast.novel_title}</span></button><button type="button" aria-label="Приховати сповіщення" onClick={() => setToast(undefined)}>×</button></div>}
    </div>;
}
