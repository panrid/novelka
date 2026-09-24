import { RichText } from './RichText';
import type { Names } from '../lib/mentions';

export interface ReplySource { reply_to?: number | null; reply_author?: string | null; reply_body?: string | null; reply_hidden?: boolean; reply_deleted?: boolean }

/** The start of the answered message above a reply; clicking it scrolls to the original when it is loaded. */
export function ReplyPreview({ source, prefix, names }: { source: ReplySource; prefix: string; names: Names }) {
    if (!source.reply_to) return null;
    const text = source.reply_deleted ? 'Повідомлення видалено.' : source.reply_hidden ? 'Приховане модератором повідомлення.' : source.reply_body ?? '';
    return <button type="button" className="reply-preview" aria-label={'Перейти до повідомлення ' + (source.reply_author ?? '')}
        onClick={() => {
            const target = document.getElementById(prefix + source.reply_to);
            if (!target) return;
            target.scrollIntoView({ behavior: 'smooth', block: 'center' });
            target.classList.add('flash');
            window.setTimeout(() => target.classList.remove('flash'), 1600);
        }}>
        <span className="reply-author">↪ {source.reply_author}</span>
        {source.reply_deleted || source.reply_hidden ? <span className="muted">{text}</span>
            : <RichText body={text.length >= 200 ? text + '…' : text} names={names} className="reply-excerpt" />}
    </button>;
}
