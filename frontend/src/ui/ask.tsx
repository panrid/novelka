import { useState, useSyncExternalStore, type FormEvent } from 'react';
import { Button } from './Button';
import { Sheet } from './Sheet';
import { TextInput } from './TextInput';
import styles from './ui.module.css';

/*
 * Questions in the page instead of window.prompt/confirm: those are blocked in some in-app
 * browsers and look foreign on a phone. Call askText/askConfirm anywhere; <AskHost/> in the
 * shell shows the question and the promise gets the answer.
 */

type TextQuestion = {
    title: string; label: string; hint?: string; optional?: boolean; multiline?: boolean;
    confirmLabel?: string; danger?: boolean;
};
type ConfirmQuestion = { title: string; text?: string; confirmLabel?: string; danger?: boolean };
type Pending = { id: number } & (
    | ({ kind: 'text'; resolve: (answer: string | null) => void } & TextQuestion)
    | ({ kind: 'confirm'; resolve: (answer: boolean) => void } & ConfirmQuestion));

let pending: Pending | null = null;
let asked = 0;
const listeners = new Set<() => void>();

function show(next: Omit<TextPending, 'id'> | Omit<ConfirmPending, 'id'> | null) {
    // A new question closes the one before as if it was cancelled.
    if (pending && next) {
        if (pending.kind === 'text') pending.resolve(null);
        else pending.resolve(false);
    }
    pending = next && ({ ...next, id: ++asked } as Pending);
    listeners.forEach((listener) => listener());
}

type TextPending = Extract<Pending, { kind: 'text' }>;
type ConfirmPending = Extract<Pending, { kind: 'confirm' }>;

/** Resolves with the trimmed answer, or null when the person cancels. */
export function askText(question: TextQuestion): Promise<string | null> {
    return new Promise((resolve) => show({ kind: 'text', ...question, resolve }));
}

export function askConfirm(question: ConfirmQuestion): Promise<boolean> {
    return new Promise((resolve) => show({ kind: 'confirm', ...question, resolve }));
}

const subscribe = (listener: () => void) => {
    listeners.add(listener);
    return () => void listeners.delete(listener);
};

export function AskHost() {
    const question = useSyncExternalStore(subscribe, () => pending);
    return question ? <Ask key={question.id} question={question} /> : null;
}

function Ask({ question }: { question: Pending }) {
    const [value, setValue] = useState('');

    const finish = (answer: string | null | boolean) => {
        pending = null;
        listeners.forEach((listener) => listener());
        if (question.kind === 'text') question.resolve(typeof answer === 'string' ? answer : null);
        else question.resolve(answer === true);
    };
    const cancel = () => finish(question.kind === 'text' ? null : false);
    const empty = question.kind === 'text' && !question.optional && !value.trim();
    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (!empty) finish(question.kind === 'text' ? value.trim() : true);
    };

    return (
        <Sheet open onClose={cancel} title={question.title}>
            <form onSubmit={submit} className={styles.askForm}>
                {question.kind === 'text' ? (
                    <TextInput label={question.label} hint={question.hint} value={value} onChange={setValue} autoFocus
                        multiline={question.multiline ?? false} />
                ) : question.text && <p className={styles.askText}>{question.text}</p>}
                <div className={styles.askActions}>
                    <Button variant="secondary" onPress={cancel}>Скасувати</Button>
                    <Button type="submit" variant={question.danger ? 'danger' : 'primary'} isDisabled={empty}>
                        {question.confirmLabel ?? 'Гаразд'}
                    </Button>
                </div>
            </form>
        </Sheet>
    );
}
