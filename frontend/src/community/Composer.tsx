import { ImagePlus, Send, X } from 'lucide-react';
import { useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import styles from './composer.module.css';

export type ReplyTarget = { id: number; who: string; excerpt: string };

type Props = {
    label: string;
    placeholder?: string;
    reply?: ReplyTarget | null;
    onCancelReply?: () => void;
    /** Resolves when sent; the text is cleared only then, so nothing typed is lost on an error. */
    onSend: (text: string, pictures: number[]) => Promise<unknown>;
    /** Pictures (messages only): upload a file, get its id. */
    uploadPicture?: (file: File) => Promise<{ id: number; url: string }>;
    maxLength?: number;
    initial?: string;
    submitLabel?: string;
};

/**
 * The box for writing a comment, a chat line or a message. Enter sends on a computer,
 * a new line on a phone keyboard; the button always sends.
 */
export function Composer({ label, placeholder, reply, onCancelReply, onSend, uploadPicture, maxLength = 4000, initial = '', submitLabel = 'Надіслати' }: Props) {
    const [text, setText] = useState(initial);
    const [pictures, setPictures] = useState<{ id: number; url: string }[]>([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const files = useRef<HTMLInputElement>(null);

    async function send(event?: FormEvent) {
        event?.preventDefault();
        if (busy || (!text.trim() && pictures.length === 0)) return;
        setBusy(true);
        setError(null);
        try {
            await onSend(text.trim(), pictures.map((picture) => picture.id));
            setText('');
            setPictures([]);
        } catch (failure) {
            setError(failure instanceof Error ? failure.message : 'Не вдалося надіслати.');
        } finally {
            setBusy(false);
        }
    }

    async function pick(list: FileList | null) {
        if (!list || !uploadPicture) return;
        setError(null);
        for (const file of [...list].slice(0, 10 - pictures.length)) {
            try {
                const stored = await uploadPicture(file);
                setPictures((current) => [...current, stored]);
            } catch (failure) {
                setError(failure instanceof Error ? failure.message : 'Картинку не вдалося завантажити.');
            }
        }
    }

    function keys(event: KeyboardEvent<HTMLTextAreaElement>) {
        const coarse = typeof window !== 'undefined' && window.matchMedia?.('(pointer: coarse)').matches;
        if (event.key === 'Enter' && !event.shiftKey && !coarse) {
            event.preventDefault();
            void send();
        }
    }

    return (
        <form className={styles.composer} onSubmit={send}>
            {reply && (
                <div className={styles.reply}>
                    <span>Відповідь {reply.who}: {reply.excerpt}</span>
                    <button type="button" aria-label="Не відповідати" onClick={onCancelReply}><X size={16} aria-hidden /></button>
                </div>
            )}
            {pictures.length > 0 && (
                <div className={styles.pictures}>
                    {pictures.map((picture) => (
                        <span key={picture.id} className={styles.picture}>
                            <img src={picture.url} alt="" />
                            <button type="button" aria-label="Прибрати картинку"
                                onClick={() => setPictures((current) => current.filter((p) => p.id !== picture.id))}><X size={14} aria-hidden /></button>
                        </span>
                    ))}
                </div>
            )}
            {error && <div className={styles.error} role="alert">{error}</div>}
            <div className={styles.row}>
                {uploadPicture && (
                    <>
                        <button type="button" className={styles.icon} aria-label="Додати картинки" onClick={() => files.current?.click()}
                            disabled={pictures.length >= 10}><ImagePlus size={22} aria-hidden /></button>
                        <input ref={files} type="file" accept="image/*" multiple hidden
                            onChange={(event) => { void pick(event.target.files); event.target.value = ''; }} />
                    </>
                )}
                <textarea className={styles.input} aria-label={label} placeholder={placeholder ?? label} value={text} rows={1}
                    maxLength={maxLength} onChange={(event) => setText(event.target.value)} onKeyDown={keys} />
                <button type="submit" className={styles.send} aria-label={submitLabel} disabled={busy || (!text.trim() && pictures.length === 0)}>
                    <Send size={20} aria-hidden />
                </button>
            </div>
        </form>
    );
}
