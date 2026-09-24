import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { getJson, mutate } from '../api/client';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from '../components/ActionNotice';
import { ErrorState, Loading } from '../components/Status';
import { HiddenContent } from '../components/HiddenContent';
import { ModerationControls } from '../components/ModerationControls';
import { MentionTextarea } from '../components/MentionTextarea';
import { RichText } from '../components/RichText';
import { ReplyPreview, type ReplySource } from '../components/ReplyPreview';
import { append, quote, type Names } from '../lib/mentions';

interface Message extends ReplySource {
    id: number; author_id: string; author: string; body: string; created_at: string; can_delete: boolean;
    can_moderate?: boolean; hidden?: boolean; hidden_reason?: string;
}
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
    const [names, setNames] = useState<Names>({});
    const [replyTo, setReplyTo] = useState<Message | null>(null);
    const composer = useRef<HTMLTextAreaElement>(null);
    const learn = useCallback((more?: Names) => { if (more) setNames(current => ({ ...current, ...more })); }, []);
    const action = useAction();
    const list = useRef<HTMLOListElement>(null);
    const stick = useRef(true);
    const newest = useRef(0);
    const messagesRef = useRef<Message[] | undefined>(undefined);
    messagesRef.current = messages;

    const merge = useCallback((incoming: Message[], deleted: number[] = []) => setMessages(current => {
        const known = new Map((current ?? []).map(item => [item.id, item]));
        incoming.forEach(item => known.set(item.id, item));
        deleted.forEach(id => known.delete(id));
        const next = [...known.values()].sort((a, b) => a.id - b.id);
        newest.current = Math.max(newest.current, ...next.map(item => item.id), 0);
        return next;
    }), []);

    const poll = useCallback(async () => {
        const update = await getJson<{ items: Message[]; deleted: number[]; moderated?: Message[]; names?: Names }>('/chat/updates?after=' + newest.current);
        learn(update.names);
        // Hidden or restored messages replace the copies already on screen; unknown older ones are ignored.
        const shown = new Set((messagesRef.current ?? []).map(item => item.id));
        merge([...update.items, ...(update.moderated ?? []).filter(item => shown.has(item.id))], update.deleted);
    }, [merge, learn]);

    useEffect(() => {
        getJson<{ items: Message[]; nextCursor: number; names?: Names }>('/chat')
            .then(page => { learn(page.names); merge([...page.items].reverse()); setCursor(page.nextCursor); })
            .catch(failure => setError(failure instanceof Error ? failure.message : 'Чат недоступний.'));
    }, [merge, learn]);

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
        getJson<{ items: Message[]; nextCursor: number; names?: Names }>('/chat?before=' + cursor)
            .then(page => {
                learn(page.names);
                merge([...page.items].reverse()); setCursor(page.nextCursor);
                requestAnimationFrame(() => { if (element) element.scrollTop = element.scrollHeight - distance; });
            })
            .catch(failure => setError(failure instanceof Error ? failure.message : 'Не вдалося завантажити історію.'))
            .finally(() => setLoadingOlder(false));
    };
    const send = () => {
        if (!text.trim()) return;
        void action.run(async () => {
            await mutate('/chat', { body: text, replyTo: replyTo?.id ?? null });
            setText(''); setReplyTo(null); stick.current = true; await poll();
        });
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
                {messages.length ? messages.map(message => <li key={message.id} id={'chat-' + message.id} className="chat-message">
                    <div className="chat-meta"><strong>{message.author}</strong><time dateTime={message.created_at}>{time.format(new Date(message.created_at))}</time>
                        {message.can_delete && <button type="button" className="plain-button" aria-label={'Видалити повідомлення ' + message.author}
                            onClick={() => {
                                if (!window.confirm('Видалити це повідомлення?')) return;
                                void action.run(async () => { await mutate('/chat/' + message.id, undefined, 'DELETE'); merge([], [message.id]); });
                            }}>Видалити</button>}
                        {message.can_moderate && !message.can_delete && <ModerationControls path={'/chat/' + message.id} hidden={!!message.hidden}
                            author={message.author} onChanged={() => void poll().catch(() => {})} />}
                        <button type="button" className="plain-button" aria-label={'Відповісти ' + message.author}
                            onClick={() => { setReplyTo(message); composer.current?.focus(); }}>Відповісти</button>
                        {!message.hidden && <button type="button" className="plain-button" aria-label={'Цитувати ' + message.author}
                            onPointerDown={event => event.preventDefault()} onClick={event => {
                                const element = event.currentTarget.closest('li')?.querySelector('.rich-text') as HTMLElement | null;
                                const selection = window.getSelection();
                                const part = element && selection && !selection.isCollapsed && element.contains(selection.anchorNode) ? selection.toString().trim() : '';
                                setText(current => append(current, quote(part || message.body, message.author, names)));
                                composer.current?.focus();
                            }}>Цитувати</button>}</div>
                    <ReplyPreview source={message} prefix="chat-" names={names} />
                    <HiddenContent hidden={!!message.hidden} reason={message.hidden_reason}><RichText body={message.body} names={names} /></HiddenContent>
                </li>) : <li className="muted chat-empty">Поки тихо. Напишіть перше повідомлення.</li>}
            </ol>
            <form className="chat-form" onSubmit={event => { event.preventDefault(); send(); }}>
                {replyTo && <p className="replying-to">Відповідь для <strong>{replyTo.author}</strong>
                    <button type="button" className="plain-button" aria-label="Скасувати відповідь" onClick={() => setReplyTo(null)}>×</button></p>}
                <label className="sr-only" htmlFor="chat-message">Повідомлення</label>
                <MentionTextarea ref={composer} id="chat-message" rows={2} maxLength={1000} value={text}
                    placeholder="Повідомлення… Enter — надіслати, Shift+Enter — новий рядок, @нік — звернутися" onChange={setText}
                    onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); send(); } }} />
                <button className="button" disabled={action.busy || !text.trim()}>Надіслати</button>
            </form>
            <ActionNotice {...action} />
        </section>}
    </div>;
}
