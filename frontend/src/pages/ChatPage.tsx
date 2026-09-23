import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { getJson, mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';
import { ErrorState, Loading } from '../components/Status';

interface Message { id: number; author_id: string; author: string; body: string; created_at: string; can_delete: boolean }
const POLL_MS = 4000;
const time = new Intl.DateTimeFormat('uk-UA', { dateStyle: 'short', timeStyle: 'short' });

/**
 * Community chat. Polling keeps it near-realtime without extra infrastructure: every few seconds while the tab is
 * visible the page asks for messages after the newest id it has and drops recently deleted ones.
 */
export function ChatPage() {
    const [messages, setMessages] = useState<Message[]>();
    const [cursor, setCursor] = useState(0);
    const [error, setError] = useState('');
    const [text, setText] = useState('');
    const [loadingOlder, setLoadingOlder] = useState(false);
    const action = useAction();
    const list = useRef<HTMLOListElement>(null);
    const stick = useRef(true);
    const newest = useRef(0);

    const merge = useCallback((incoming: Message[], deleted: number[] = []) => setMessages(current => {
        const known = new Map((current ?? []).map(item => [item.id, item]));
        incoming.forEach(item => known.set(item.id, item));
        deleted.forEach(id => known.delete(id));
        const next = [...known.values()].sort((a, b) => a.id - b.id);
        newest.current = Math.max(newest.current, ...next.map(item => item.id), 0);
        return next;
    }), []);

    const poll = useCallback(async () => {
        const update = await getJson<{ items: Message[]; deleted: number[] }>('/chat/updates?after=' + newest.current);
        merge(update.items, update.deleted);
    }, [merge]);

    useEffect(() => {
        getJson<{ items: Message[]; nextCursor: number }>('/chat')
            .then(page => { merge([...page.items].reverse()); setCursor(page.nextCursor); })
            .catch(failure => setError(failure instanceof Error ? failure.message : 'Чат недоступний.'));
    }, [merge]);

    useEffect(() => {
        const timer = window.setInterval(() => {
            if (document.visibilityState === 'visible' && messages) void poll().catch(() => {});
        }, POLL_MS);
        return () => window.clearInterval(timer);
    }, [poll, messages]);

    useLayoutEffect(() => {
        if (stick.current && list.current) list.current.scrollTop = list.current.scrollHeight;
    }, [messages]);

    const older = () => {
        setLoadingOlder(true);
        const element = list.current;
        const distance = element ? element.scrollHeight - element.scrollTop : 0;
        stick.current = false;
        getJson<{ items: Message[]; nextCursor: number }>('/chat?before=' + cursor)
            .then(page => {
                merge([...page.items].reverse()); setCursor(page.nextCursor);
                requestAnimationFrame(() => { if (element) element.scrollTop = element.scrollHeight - distance; });
            })
            .catch(failure => setError(failure instanceof Error ? failure.message : 'Не вдалося завантажити історію.'))
            .finally(() => setLoadingOlder(false));
    };
    const send = () => {
        if (!text.trim()) return;
        void action.run(async () => { await mutate('/chat', { body: text }); setText(''); stick.current = true; await poll(); });
    };

    if (error && !messages) return <div className="page workspace"><ErrorState message={error} retry={() => window.location.reload()} /></div>;
    return <div className="page workspace chat-page"><p className="eyebrow">Спільнота</p><h1>Загальний чат</h1>
        <p className="muted">Повідомлення бачать усі учасники, які увійшли. Нові з’являються автоматично кожні кілька секунд.</p>
        {!messages ? <Loading /> : <section className="chat-box" aria-label="Повідомлення чату">
            {!!cursor && <button type="button" className="chat-older" disabled={loadingOlder} onClick={older}>{loadingOlder ? 'Завантажуємо…' : 'Завантажити старіші'}</button>}
            <ol className="chat-messages" ref={list} aria-live="polite" onScroll={event => {
                const element = event.currentTarget;
                stick.current = element.scrollHeight - element.scrollTop - element.clientHeight < 40;
            }}>
                {messages.length ? messages.map(message => <li key={message.id} className="chat-message">
                    <div className="chat-meta"><strong>{message.author}</strong><time dateTime={message.created_at}>{time.format(new Date(message.created_at))}</time>
                        {message.can_delete && <button type="button" className="plain-button" aria-label={'Видалити повідомлення ' + message.author}
                            onClick={() => {
                                if (!window.confirm('Видалити це повідомлення?')) return;
                                void action.run(async () => { await mutate('/chat/' + message.id, undefined, 'DELETE'); merge([], [message.id]); });
                            }}>Видалити</button>}</div>
                    <p>{message.body}</p>
                </li>) : <li className="muted chat-empty">Поки тихо. Напишіть перше повідомлення.</li>}
            </ol>
            <form className="chat-form" onSubmit={event => { event.preventDefault(); send(); }}>
                <label className="sr-only" htmlFor="chat-message">Повідомлення</label>
                <textarea id="chat-message" rows={2} maxLength={1000} value={text} placeholder="Повідомлення… Enter — надіслати, Shift+Enter — новий рядок"
                    onChange={event => setText(event.target.value)}
                    onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); send(); } }} />
                <button className="button" disabled={action.busy || !text.trim()}>Надіслати</button>
            </form>
            <ActionNotice {...action} />
        </section>}
    </div>;
}
