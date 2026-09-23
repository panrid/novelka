import { useState } from 'react';
import { mutate } from '../api/client';
import { ActionNotice } from '../components/ActionNotice';
import { HelpTip } from '../components/HelpTip';
import { useAction } from '../hooks/useAction';
import { MACHINE_TRANSLATION } from '../lib/tags';

/** Adds a novel with a ready translation: no import and no AI pipeline. */
export function ManualNovelForm({ onCreated }: { onCreated: (id: string, title: string) => void }) {
    const action = useAction();
    const [machine, setMachine] = useState(false);
    return <section className="panel manual-novel" aria-labelledby="manual-novel-title">
        <h2 id="manual-novel-title">Створити новелу вручну<HelpTip label="Ручна новела">Для готового перекладу, який не потребує ШІ-конвеєра. Глави додаються в майстерні як чернетки й публікуються окремою дією. ШІ-переклад для такої новели не запускається.</HelpTip></h2>
        <form className="stack-form" onSubmit={event => {
            event.preventDefault();
            const values = new FormData(event.currentTarget);
            const titleUk = String(values.get('titleUk') ?? '');
            void action.run(async () => {
                const created = await mutate<{ id: string }>('/manage/novels', {
                    titleUk, title: values.get('title'), authorUk: values.get('authorUk'), author: values.get('author'),
                    descriptionUk: values.get('descriptionUk'), tags: machine ? [MACHINE_TRANSLATION] : [],
                });
                onCreated(created.id, titleUk);
            }, 'Новелу створено. Додайте першу главу у вкладці «Глави й експорт».');
        }}>
            <div className="form-grid">
                <label>Українська назва<input name="titleUk" required maxLength={500} /></label>
                <label>Назва в оригіналі<input name="title" maxLength={500} placeholder="Необов’язково" /></label>
                <label>Автор українською<input name="authorUk" maxLength={500} /></label>
                <label>Автор в оригіналі<input name="author" maxLength={500} placeholder="Необов’язково" /></label>
            </div>
            <label>Опис українською<textarea name="descriptionUk" rows={4} maxLength={10000} /></label>
            <span className="check-with-help"><label className="check-label"><input type="checkbox" checked={machine} onChange={event => setMachine(event.target.checked)} />Це машинний переклад</label>
                <HelpTip label="Машинний переклад">Позначте, якщо текст перекладено ШІ чи іншою машинною системою, навіть поза Novelka. Додасть стандартний тег «{MACHINE_TRANSLATION}»; його можна змінити пізніше разом з іншими тегами.</HelpTip></span>
            <button className="button" disabled={action.busy}>Створити новелу</button>
            <ActionNotice {...action} />
        </form>
    </section>;
}
