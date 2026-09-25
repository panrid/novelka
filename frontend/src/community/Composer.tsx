import { useQuery } from '@tanstack/react-query';
import { ImagePlus, Send, X } from 'lucide-react';
import { useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Avatar } from '../ui/Avatar';
import { mentionApi } from './api';
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

/** The word being typed right after @ or $, if any. */
const TYPING = /(?:^|[^\p{L}\p{N}_@$-])([@$])([\p{L}\p{N}_-]{1,29})$/u;

const MARKS: readonly { marker: string; label: string; show: string }[] = [
    { marker: '**', label: 'Жирний', show: 'Ж' },
    { marker: '*', label: 'Курсив', show: 'К' },
    { marker: '++', label: 'Підкреслений', show: 'П' },
    { marker: '~~', label: 'Закреслений', show: 'З' },
    { marker: '||', label: 'Спойлер', show: '▒' },
];

/**
 * The box for writing a comment, a chat line or a message. Enter sends on a computer,
 * a new line on a phone keyboard; the button always sends. After @ or $ it suggests people
 * and teams; «Aa» shows buttons that put the markup in, so nobody has to remember it.
 */
export function Composer({ label, placeholder, reply, onCancelReply, onSend, uploadPicture, maxLength = 4000, initial = '', submitLabel = 'Надіслати' }: Props) {
    const [text, setText] = useState(initial);
    const [pictures, setPictures] = useState<{ id: number; url: string }[]>([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [formatting, setFormatting] = useState(false);
    const [typing, setTyping] = useState<{ kind: '@' | '$'; query: string; start: number; end: number } | null>(null);
    const [highlight, setHighlight] = useState(0);
    const files = useRef<HTMLInputElement>(null);
    const input = useRef<HTMLTextAreaElement>(null);

    const suggestions = useQuery({
        queryKey: ['mentions', typing?.kind, typing?.query.toLowerCase()],
        queryFn: () => mentionApi.search(typing!.kind, typing!.query),
        enabled: typing !== null,
        staleTime: 30_000,
    });
    const options = typing ? suggestions.data ?? [] : [];

    function look(value: string, caret: number) {
        const match = TYPING.exec(value.slice(0, caret));
        if (!match) {
            setTyping(null);
            return;
        }
        setTyping({ kind: match[1] as '@' | '$', query: match[2]!, start: caret - match[2]!.length - 1, end: caret });
        setHighlight(0);
    }

    // The caret moves right after the new text is drawn, before anyone can type more.
    const caretAt = useRef<number | null>(null);
    useLayoutEffect(() => {
        if (caretAt.current !== null) {
            input.current?.focus();
            input.current?.setSelectionRange(caretAt.current, caretAt.current);
            caretAt.current = null;
        }
    }, [text]);

    function place(value: string, caret: number) {
        caretAt.current = caret;
        setText(value);
    }

    function choose(name: string) {
        if (!typing) return;
        const inserted = `${typing.kind}${name} `;
        place(text.slice(0, typing.start) + inserted + text.slice(typing.end), typing.start + inserted.length);
        setTyping(null);
    }

    function mark(marker: string) {
        const area = input.current;
        const from = area?.selectionStart ?? text.length;
        const to = area?.selectionEnd ?? text.length;
        place(text.slice(0, from) + marker + text.slice(from, to) + marker + text.slice(to),
            to === from ? from + marker.length : to + marker.length * 2);
    }

    function quote() {
        const area = input.current;
        const from = area?.selectionStart ?? text.length;
        const to = area?.selectionEnd ?? text.length;
        const lineStart = text.lastIndexOf('\n', from - 1) + 1;
        const quoted = text.slice(lineStart, to).split('\n').map((line) => `> ${line}`).join('\n');
        place(text.slice(0, lineStart) + quoted + text.slice(to), lineStart + quoted.length);
    }

    async function send(event?: FormEvent) {
        event?.preventDefault();
        if (busy || (!text.trim() && pictures.length === 0)) return;
        setBusy(true);
        setError(null);
        try {
            await onSend(text.trim(), pictures.map((picture) => picture.id));
            setText('');
            setPictures([]);
            setTyping(null);
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
        if (options.length > 0) {
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                setHighlight((at) => (at + (event.key === 'ArrowDown' ? 1 : options.length - 1)) % options.length);
                return;
            }
            if (event.key === 'Enter' || event.key === 'Tab') {
                event.preventDefault();
                choose(options[highlight]!.name);
                return;
            }
            if (event.key === 'Escape') {
                event.preventDefault();
                setTyping(null);
                return;
            }
        }
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
            {options.length > 0 && (
                <ul className={styles.suggestions} role="listbox" aria-label={typing?.kind === '$' ? 'Команди' : 'Люди'}>
                    {options.map((option, index) => (
                        <li key={option.name} role="option" aria-selected={index === highlight}
                            className={index === highlight ? styles.suggestionOn : styles.suggestion}
                            onMouseDown={(event) => { event.preventDefault(); choose(option.name); }}>
                            {typing?.kind === '@' && <Avatar nick={option.name} url={option.avatarUrl} size={22} />}
                            <span>{typing?.kind}{option.name}</span>
                            {option.title && option.title !== option.name && <span className={styles.hint}>{option.title}</span>}
                        </li>
                    ))}
                </ul>
            )}
            {formatting && (
                <div className={styles.toolbar} role="toolbar" aria-label="Форматування">
                    {MARKS.map((item) => (
                        <button key={item.marker} type="button" aria-label={item.label} title={item.label}
                            onMouseDown={(event) => event.preventDefault()} onClick={() => mark(item.marker)}>{item.show}</button>
                    ))}
                    <button type="button" aria-label="Цитата" title="Цитата" onMouseDown={(event) => event.preventDefault()} onClick={quote}>❝</button>
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
                <button type="button" className={styles.icon} aria-label="Форматування" aria-pressed={formatting}
                    onClick={() => setFormatting(!formatting)}>Aa</button>
                <textarea ref={input} className={styles.input} aria-label={label} placeholder={placeholder ?? label} value={text} rows={1}
                    maxLength={maxLength} onKeyDown={keys}
                    onChange={(event) => { setText(event.target.value); look(event.target.value, event.target.selectionStart); }}
                    onBlur={() => setTyping(null)} />
                <button type="submit" className={styles.send} aria-label={submitLabel} disabled={busy || (!text.trim() && pictures.length === 0)}>
                    <Send size={20} aria-hidden />
                </button>
            </div>
        </form>
    );
}
