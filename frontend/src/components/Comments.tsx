import { useEffect, useRef, useState } from 'react';
import { getJson, mutate } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from './ActionNotice';
import { ErrorState, Loading } from './Status';
import { VoteControl, type VoteSummary } from './VoteControl';
import { HiddenContent } from './HiddenContent';
import { ModerationControls } from './ModerationControls';
import { MentionTextarea } from './MentionTextarea';
import { RichText } from './RichText';
import { ReplyPreview, type ReplySource } from './ReplyPreview';
import { append, editableText, quote, type Names } from '../lib/mentions';

interface Comment extends ReplySource {
    id: number; author_id: string; author: string; body: string; created_at: string; edited_at: string | null;
    rating: VoteSummary; can_edit: boolean; can_delete: boolean; can_moderate?: boolean; hidden?: boolean; hidden_reason?: string;
}
interface CommentPage { items: Comment[]; nextCursor: number; names?: Names }
const dateTime = new Intl.DateTimeFormat('uk-UA', { dateStyle: 'medium', timeStyle: 'short' });

/** The text selected inside one comment, if any: "Цитувати" then quotes just that part. */
function selectionIn(element: HTMLElement | null) {
    const selection = window.getSelection();
    if (!element || !selection || selection.isCollapsed || !element.contains(selection.anchorNode) || !element.contains(selection.focusNode)) return '';
    return selection.toString().trim();
}

/** Novel (chapter 0) or chapter discussion: newest first, older comments load on demand. */
export function Comments({ novel, chapter = 0, title }: { novel: string; chapter?: number; title: string }) {
    const { user } = useAuth();
    const base = '/novels/' + encodeURIComponent(novel) + '/comments?chapter=' + chapter;
    const [items, setItems] = useState<Comment[]>();
    const [names, setNames] = useState<Names>({});
    const [cursor, setCursor] = useState(0);
    const [error, setError] = useState('');
    const [loadingMore, setLoadingMore] = useState(false);
    const [body, setBody] = useState('');
    const [replyTo, setReplyTo] = useState<Comment | null>(null);
    const [version, setVersion] = useState(0);
    const composer = useRef<HTMLTextAreaElement>(null);
    const action = useAction();
    useEffect(() => {
        const controller = new AbortController();
        setItems(undefined); setError('');
        getJson<CommentPage>(base, controller.signal).then(page => { setItems(page.items); setNames(page.names ?? {}); setCursor(page.nextCursor); })
            .catch(failure => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : 'Не вдалося завантажити коментарі.'); });
        return () => controller.abort();
    }, [base, version, user?.id]);
    const older = () => {
        setLoadingMore(true);
        getJson<CommentPage>(base + '&before=' + cursor).then(page => {
            setItems(current => [...(current ?? []), ...page.items]); setNames(current => ({ ...current, ...page.names })); setCursor(page.nextCursor);
        })
            .catch(failure => setError(failure instanceof Error ? failure.message : 'Не вдалося завантажити коментарі.'))
            .finally(() => setLoadingMore(false));
    };
    const focusComposer = () => requestAnimationFrame(() => {
        composer.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        composer.current?.focus();
    });
    const headingId = 'comments-' + chapter;
    return <section className="comments" aria-labelledby={headingId}>
        <h2 id={headingId}>{title}</h2>
        {user ? <form className="comment-form" onSubmit={event => {
            event.preventDefault();
            void action.run(async () => {
                await mutate(base.replace('?chapter=' + chapter, ''), { chapter, body, replyTo: replyTo?.id ?? null });
                setBody(''); setReplyTo(null); setVersion(value => value + 1);
            }, 'Коментар додано.');
        }}>
            {replyTo && <p className="replying-to">Відповідь для <strong>{replyTo.author}</strong>
                <button type="button" className="plain-button" aria-label="Скасувати відповідь" onClick={() => setReplyTo(null)}>×</button></p>}
            <label className="sr-only" htmlFor={headingId + '-body'}>Ваш коментар</label>
            <MentionTextarea ref={composer} id={headingId + '-body'} rows={3} maxLength={5000} required
                placeholder="Поділіться враженнями… @нік — звернутися до людини" value={body} onChange={setBody} />
            <button className="button" disabled={action.busy || !body.trim()}>Опублікувати коментар</button>
            <ActionNotice {...action} />
        </form> : <p className="muted"><a href="#/login">Увійдіть</a>, щоб коментувати й голосувати.</p>}
        {error ? <ErrorState message={error} retry={() => setVersion(value => value + 1)} /> : !items ? <Loading />
            : items.length ? <ol className="comment-list">{items.map(item => <CommentItem key={item.id} comment={item} names={names}
                onChanged={() => setVersion(value => value + 1)}
                onReply={user ? () => { setReplyTo(item); focusComposer(); } : undefined}
                onQuote={user ? text => { setBody(current => append(current, quote(text, item.author, names))); focusComposer(); } : undefined} />)}</ol>
                : <p className="muted">Коментарів поки немає. Будьте першими.</p>}
        {!!cursor && <button type="button" disabled={loadingMore} onClick={older}>{loadingMore ? 'Завантажуємо…' : 'Показати старіші'}</button>}
    </section>;
}

function CommentItem({ comment, names, onChanged, onReply, onQuote }: {
    comment: Comment; names: Names; onChanged: () => void; onReply?: () => void; onQuote?: (text: string) => void;
}) {
    const [editing, setEditing] = useState(false);
    const [body, setBody] = useState(() => editableText(comment.body, names));
    const text = useRef<HTMLDivElement>(null);
    const action = useAction();
    return <li className="comment" id={'comment-' + comment.id}>
        <div className="comment-meta"><strong>{comment.author}</strong>
            <time dateTime={comment.created_at}>{dateTime.format(new Date(comment.created_at))}</time>
            {comment.edited_at && <span className="muted" title={'Змінено ' + dateTime.format(new Date(comment.edited_at))}>змінено</span>}</div>
        <ReplyPreview source={comment} prefix="comment-" names={names} />
        {editing ? <form className="comment-form" onSubmit={event => {
            event.preventDefault();
            void action.run(async () => { await mutate('/comments/' + comment.id, { body }); setEditing(false); onChanged(); });
        }}>
            <label className="sr-only" htmlFor={'comment-edit-' + comment.id}>Текст коментаря</label>
            <MentionTextarea id={'comment-edit-' + comment.id} rows={3} maxLength={5000} required value={body} onChange={setBody} />
            <div className="button-row"><button className="button" disabled={action.busy || !body.trim()}>Зберегти</button>
                <button type="button" onClick={() => { setEditing(false); setBody(editableText(comment.body, names)); }}>Скасувати</button></div>
        </form> : <HiddenContent hidden={!!comment.hidden} reason={comment.hidden_reason}>
            <div ref={text}><RichText body={comment.body} names={names} className="comment-body" /></div></HiddenContent>}
        <div className="comment-actions">
            <VoteControl type="comment" target={String(comment.id)} initial={comment.rating} label={'Рейтинг коментаря ' + comment.author} />
            {onReply && !editing && <button type="button" className="plain-button" aria-label={'Відповісти ' + comment.author} onClick={onReply}>Відповісти</button>}
            {onQuote && !editing && !comment.hidden && <button type="button" className="plain-button" aria-label={'Цитувати ' + comment.author}
                onPointerDown={event => event.preventDefault()}
                onClick={() => onQuote(selectionIn(text.current) || comment.body)}>Цитувати</button>}
            {comment.can_edit && !editing && <button type="button" className="plain-button" onClick={() => setEditing(true)}>Редагувати</button>}
            {comment.can_moderate && !comment.can_edit && <ModerationControls path={'/comments/' + comment.id} hidden={!!comment.hidden}
                author={comment.author} onChanged={onChanged} />}
            {comment.can_delete && <button type="button" className="plain-button" disabled={action.busy} onClick={() => {
                if (!window.confirm('Видалити ваш коментар?')) return;
                void action.run(async () => { await mutate('/comments/' + comment.id, undefined, 'DELETE'); onChanged(); });
            }}>Видалити</button>}
        </div>
        <ActionNotice {...action} />
    </li>;
}
