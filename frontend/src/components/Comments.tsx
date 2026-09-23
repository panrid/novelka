import { useEffect, useState } from 'react';
import { getJson, mutate } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useAction } from '../hooks/useAction';
import { ActionNotice } from './ActionNotice';
import { ErrorState, Loading } from './Status';
import { VoteControl, type VoteSummary } from './VoteControl';

interface Comment {
    id: number; author_id: string; author: string; body: string; created_at: string; edited_at: string | null;
    rating: VoteSummary; can_edit: boolean; can_delete: boolean;
}
interface CommentPage { items: Comment[]; nextCursor: number }
const dateTime = new Intl.DateTimeFormat('uk-UA', { dateStyle: 'medium', timeStyle: 'short' });

/** Novel (chapter 0) or chapter discussion: newest first, older comments load on demand. */
export function Comments({ novel, chapter = 0, title }: { novel: string; chapter?: number; title: string }) {
    const { user } = useAuth();
    const base = '/novels/' + encodeURIComponent(novel) + '/comments?chapter=' + chapter;
    const [items, setItems] = useState<Comment[]>();
    const [cursor, setCursor] = useState(0);
    const [error, setError] = useState('');
    const [loadingMore, setLoadingMore] = useState(false);
    const [body, setBody] = useState('');
    const [version, setVersion] = useState(0);
    const action = useAction();
    useEffect(() => {
        const controller = new AbortController();
        setItems(undefined); setError('');
        getJson<CommentPage>(base, controller.signal).then(page => { setItems(page.items); setCursor(page.nextCursor); })
            .catch(failure => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : 'Не вдалося завантажити коментарі.'); });
        return () => controller.abort();
    }, [base, version, user?.id]);
    const older = () => {
        setLoadingMore(true);
        getJson<CommentPage>(base + '&before=' + cursor).then(page => { setItems(current => [...(current ?? []), ...page.items]); setCursor(page.nextCursor); })
            .catch(failure => setError(failure instanceof Error ? failure.message : 'Не вдалося завантажити коментарі.'))
            .finally(() => setLoadingMore(false));
    };
    const headingId = 'comments-' + chapter;
    return <section className="comments" aria-labelledby={headingId}>
        <h2 id={headingId}>{title}</h2>
        {user ? <form className="comment-form" onSubmit={event => {
            event.preventDefault();
            void action.run(async () => { await mutate(base.replace('?chapter=' + chapter, ''), { chapter, body }); setBody(''); setVersion(value => value + 1); }, 'Коментар додано.');
        }}>
            <label className="sr-only" htmlFor={headingId + '-body'}>Ваш коментар</label>
            <textarea id={headingId + '-body'} rows={3} maxLength={5000} required placeholder="Поділіться враженнями…" value={body} onChange={event => setBody(event.target.value)} />
            <button className="button" disabled={action.busy || !body.trim()}>Опублікувати коментар</button>
            <ActionNotice {...action} />
        </form> : <p className="muted"><a href="#/login">Увійдіть</a>, щоб коментувати й голосувати.</p>}
        {error ? <ErrorState message={error} retry={() => setVersion(value => value + 1)} /> : !items ? <Loading />
            : items.length ? <ol className="comment-list">{items.map(item => <CommentItem key={item.id} comment={item}
                onChanged={() => setVersion(value => value + 1)} />)}</ol>
                : <p className="muted">Коментарів поки немає. Будьте першими.</p>}
        {!!cursor && <button type="button" disabled={loadingMore} onClick={older}>{loadingMore ? 'Завантажуємо…' : 'Показати старіші'}</button>}
    </section>;
}

function CommentItem({ comment, onChanged }: { comment: Comment; onChanged: () => void }) {
    const [editing, setEditing] = useState(false);
    const [body, setBody] = useState(comment.body);
    const action = useAction();
    return <li className="comment">
        <div className="comment-meta"><strong>{comment.author}</strong>
            <time dateTime={comment.created_at}>{dateTime.format(new Date(comment.created_at))}</time>
            {comment.edited_at && <span className="muted" title={'Змінено ' + dateTime.format(new Date(comment.edited_at))}>змінено</span>}</div>
        {editing ? <form className="comment-form" onSubmit={event => {
            event.preventDefault();
            void action.run(async () => { await mutate('/comments/' + comment.id, { body }); setEditing(false); onChanged(); });
        }}>
            <label className="sr-only" htmlFor={'comment-edit-' + comment.id}>Текст коментаря</label>
            <textarea id={'comment-edit-' + comment.id} rows={3} maxLength={5000} required value={body} onChange={event => setBody(event.target.value)} />
            <div className="button-row"><button className="button" disabled={action.busy || !body.trim()}>Зберегти</button>
                <button type="button" onClick={() => { setEditing(false); setBody(comment.body); }}>Скасувати</button></div>
        </form> : <p className="comment-body">{comment.body}</p>}
        <div className="comment-actions">
            <VoteControl type="comment" target={String(comment.id)} initial={comment.rating} label={'Рейтинг коментаря ' + comment.author} />
            {comment.can_edit && !editing && <button type="button" className="plain-button" onClick={() => setEditing(true)}>Редагувати</button>}
            {comment.can_delete && <button type="button" className="plain-button" disabled={action.busy} onClick={() => {
                if (!window.confirm(comment.can_edit ? 'Видалити ваш коментар?' : 'Видалити коментар користувача ' + comment.author + '?')) return;
                void action.run(async () => { await mutate('/comments/' + comment.id, undefined, 'DELETE'); onChanged(); });
            }}>Видалити</button>}
        </div>
        <ActionNotice {...action} />
    </li>;
}
