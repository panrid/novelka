import { TagPicker } from '../../studio/TagPicker';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from '@tanstack/react-router';
import { useRef, useState, type FormEvent } from 'react';
import { adminApi } from '../../admin/api';
import { meQuery, useMe } from '../../auth/me';
import { studioApi, teamApi, type StudioBlock } from '../../studio/api';
import { autotranslateApi } from '../../studio/autotranslate';
import { TextEditor, type EditorHandle } from '../../studio/TextEditor';
import { Button } from '../../ui/Button';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import { Toggle } from '../../ui/Toggle';
import studio from './studio.module.css';
import styles from './newTranslation.module.css';
import { useCanRun, useMaySite } from '../../ledger/api';

/**
 * How the person will translate (етап 20, рішення 19): {@code pasted} — they paste the original and
 * AI translates it (without AI for those it is hidden from: the original stands beside their own
 * translation); {@code syosetu} — the original's site, with the site owner's leave; {@code human} —
 * by hand; {@code original} — their own work.
 */
type Kind = 'pasted' | 'syosetu' | 'human' | 'original';

const LANGUAGES = [['en', 'англійська'], ['ja', 'японська'], ['zh', 'китайська'], ['ko', 'корейська']] as const;
type Language = (typeof LANGUAGES)[number][0];

/** «Що буде далі» beside the form, as the mockup tells it (NewTranslation.dc.html). */
function nextSteps(kind: Kind, ai: boolean, maySite: boolean): string[] {
    switch (kind) {
        case 'pasted':
            return ai
                ? ['Вставляєте першу главу — заголовок і текст окремими полями.',
                    '«Перекласти з ШІ»: ШІ складає словник імен, перекладає й вичитує. Видно наживо в «Ході перекладу».',
                    'Глава стає чернеткою: виправляєте в робочому місці.',
                    '«Опублікувати» — і читачі бачать главу. Далі — наступна.']
                : ['Вставляєте першу главу — заголовок і текст окремими полями.',
                    'Перекладаєте в робочому місці: оригінал стоїть поруч, абзац до абзацу.',
                    'Публікуєте, коли готово. Далі — наступна глава.'];
        case 'syosetu':
            return maySite
                ? ['Вставляєте посилання — сайт знаходить новелу й глави.', 'Вибираєте глави й якість, бачите ціну.',
                    'Новелка бере глави й перекладає; хід перекладу видно наживо.', 'Нові глави в оригіналі — щотижня, зі сповіщенням.']
                : ['Натисніть «Попросити доступ» — власник сайту отримає запит.',
                    'Поки чекаєте, можна почати з «Вставлю оригінал»: переклад той самий.',
                    'Коли доступ відкриють, у «Новому перекладі» з’явиться «Автопереклад із сайту».'];
        case 'human':
            return ['Створюєте переклад.', 'Додаєте главу: пишете в робочому місці або вставляєте готовий переклад.',
                'Оригінал можна вставити поруч — словник і підказки працюватимуть.', 'Публікуєте, коли готово.'];
        case 'original':
            return ['Створюєте твір.', 'Пишете главу в редакторі.', 'Публікуєте — читачі бачать «Автор @нік».'];
    }
}

/**
 * «Новий переклад» (етап 20, NewTranslation.dc.html): first «Як ви перекладатимете?», then the novel,
 * then its first chapter. «Автопереклад із сайту» without the owner's leave stays visible with a lock and
 * «Попросити доступ»; nothing with AI for people it is hidden from (етап 19, п. 7).
 */
export function NewPublication() {
    const canRun = useCanRun();
    const maySite = useMaySite();
    const me = useMe();
    const client = useQueryClient();
    const navigate = useNavigate();
    const teams = useQuery({ queryKey: ['my-teams'], queryFn: teamApi.mine });
    const [kind, setKind] = useState<Kind>('pasted');
    const [title, setTitle] = useState('');
    const [titleOriginal, setTitleOriginal] = useState('');
    const [author, setAuthor] = useState('');
    const [language, setLanguage] = useState<Language>('en');
    const [more, setMore] = useState(false);
    const [description, setDescription] = useState<StudioBlock[]>([]);
    const [tags, setTags] = useState<string[]>([]);
    const [adult, setAdult] = useState(false);
    const [team, setTeam] = useState('');
    // «Маю право перекладати цей текст» (етап 19): every translation; an own work needs none.
    const [rights, setRights] = useState(false);
    const own = kind === 'original';
    const site = kind === 'syosetu';
    const publishing = (teams.data ?? []).filter((t) => t.role !== 'editor');

    const [link, setLink] = useState('');
    const chosenTeam = team || publishing[0]?.handle || '';
    const prepare = useMutation({
        mutationFn: () => autotranslateApi.prepare(link.trim(), chosenTeam),
        onSuccess: ({ editionId }) => void navigate({ to: '/studio/$editionId/translate', params: { editionId: String(editionId) } }),
    });
    const editor = useRef<EditorHandle>(null);
    const create = useMutation({
        mutationFn: () => studioApi.create({
            kind: own ? 'original' : 'human', title: title.trim(), author: author.trim(),
            description: editor.current?.read() ?? description,
            tags, adult, team: chosenTeam, rightsConfirmed: !own && rights,
            ...(own ? {} : { titleOriginal: titleOriginal.trim(), language }),
        }),
        onSuccess: ({ editionId }) => void navigate(kind === 'pasted'
            ? { to: '/studio/$editionId/originals', params: { editionId: String(editionId) } }
            : { to: '/studio/$editionId', params: { editionId: String(editionId) } }),
    });
    const ask = useMutation({
        mutationFn: adminApi.requestAccess,
        onSuccess: () => void client.invalidateQueries({ queryKey: meQuery.queryKey }),
    });

    function submit(event: FormEvent) {
        event.preventDefault();
        if (site) prepare.mutate();
        else create.mutate();
    }

    const forAll = { chip: 'Для всіх', tone: 'gray' as const, locked: false };
    const ways: { id: Kind; title: string; text: string; chip: string; tone: 'gray' | 'warn'; locked: boolean }[] = [
        canRun
            ? { id: 'pasted', title: 'Вставлю оригінал — перекладе ШІ', ...forAll,
                text: 'Вставляєте главу мовою автора, ШІ перекладає її з урахуванням словника. Ви виправляєте й публікуєте.' }
            : { id: 'pasted', title: 'Вставлю оригінал', ...forAll,
                text: 'Вставляєте главу мовою автора — вона стоїть поруч, поки ви перекладаєте.' },
        ...(canRun ? [{
            id: 'syosetu' as const, title: 'Автопереклад із сайту', tone: 'warn' as const, locked: !maySite,
            chip: maySite ? 'Вам відкрито' : 'З дозволу',
            text: 'Посилання на новелу на сайті оригіналу — Новелка сама бере глави, шукає нові й перекладає.',
        }] : []),
        { id: 'human', title: 'Перекладаю сам', ...forAll,
            text: `${canRun ? 'Без ШІ: пишете' : 'Пишете'} переклад у робочому місці, оригінал — поруч, якщо вставите.` },
        { id: 'original', title: 'Свій твір', ...forAll, text: 'Ваша власна історія українською. Ви — автор, без оригіналу й естафети.' },
    ];
    const step2 = site ? 'Посилання' : 'Про новелу';
    const step3 = own || kind === 'human' ? 'Перша глава' : site ? 'Глави й якість' : 'Оригінал першої глави';

    return (
        <section className={styles.page}>
            <div className={styles.head}>
                <div>
                    <nav aria-label="Шлях" className={styles.path}><Link to="/studio">Мої переклади</Link> / Новий переклад</nav>
                    <h1 className={styles.title}>Новий переклад</h1>
                </div>
                <ol aria-label="Кроки" className={styles.steps}>
                    <li className={styles.stepNow} aria-current="step">1 · Як перекладаєте</li>
                    <li>2 · {step2}</li>
                    <li>3 · {step3}</li>
                </ol>
            </div>

            <fieldset className={styles.ways}>
                <legend className={styles.legend}>Як ви перекладатимете?</legend>
                <div className={styles.wayGrid}>
                    {ways.map((way) => (
                        <label key={way.id} className={`${styles.way} ${kind === way.id ? styles.wayOn : ''}`}>
                            <span className={styles.wayTop}>
                                <input type="radio" name="way" checked={kind === way.id} onChange={() => setKind(way.id)} />
                                <span className={`${styles.chip} ${way.tone === 'warn' ? styles.chipWarn : ''}`}>
                                    {way.locked && <Lock />}{way.chip}
                                </span>
                            </span>
                            <b className={styles.wayTitle}>{way.title}</b>
                            <span className={styles.muted}>{way.text}</span>
                        </label>
                    ))}
                </div>
            </fieldset>

            <div className={styles.columns}>
                <section aria-label="Про новелу" className={styles.panel}>
                    {site && !maySite ? (
                        <div className={styles.locked}>
                            <b className={styles.panelTitle}>Автопереклад із сайту — з дозволу власника сайту</b>
                            <p>
                                Тексти з інших сайтів захищені авторським правом, тому Новелка бере їх сама лише для тих, кому власник
                                сайту відкрив цю можливість. Без дозволу ви можете перекладати так само швидко: вставляйте оригінал
                                глави — ШІ перекладе його з тим самим словником.
                            </p>
                            {ask.isError && <Notice tone="error">{ask.error.message}</Notice>}
                            <div className={studio.actions}>
                                {me?.autotranslateRequested
                                    ? <span role="status" className={styles.sent}>Запит надіслано. Відповідь прийде у «Вхідні».</span>
                                    : <Button pending={ask.isPending} pendingLabel="Надсилаємо…" onPress={() => ask.mutate()}>Попросити доступ</Button>}
                                <Button variant="secondary" onPress={() => setKind('pasted')}>Вставлю оригінал сам</Button>
                            </div>
                        </div>
                    ) : (
                        <form className={studio.form} onSubmit={submit}>
                            {site ? (
                                <TextInput label="Посилання на новелу" value={link} onChange={setLink} isRequired inputMode="url"
                                    placeholder="https://ncode.syosetu.com/…"
                                    hint="Сторінка новели на сайті оригіналу. Назву, автора, опис і назви глав перекладемо українською одразу." />
                            ) : (
                                <div className={styles.fields}>
                                    <TextInput label="Назва українською" value={title} onChange={setTitle} isRequired />
                                    {!own && <TextInput label="Назва в оригіналі · за бажанням" value={titleOriginal} onChange={setTitleOriginal} />}
                                    {!own && <TextInput label="Автор" value={author} onChange={setAuthor} placeholder="Як підписано в оригіналі" />}
                                    {!own && (
                                        <label>
                                            <div className={studio.label}>Мова оригіналу</div>
                                            <select className={studio.select} value={language} onChange={(event) => setLanguage(event.target.value as Language)}>
                                                {LANGUAGES.map(([code, name]) => <option key={code} value={code}>{name}</option>)}
                                            </select>
                                        </label>
                                    )}
                                    {own && <p className={styles.muted}>Автор: {me?.nick}</p>}
                                </div>
                            )}
                            {publishing.length > 1 && (
                                <label>
                                    <div className={studio.label}>Команда</div>
                                    <select className={studio.select} value={team || publishing[0]!.handle} onChange={(event) => setTeam(event.target.value)}>
                                        {publishing.map((t) => <option key={t.handle} value={t.handle}>{t.name} (${t.handle})</option>)}
                                    </select>
                                </label>
                            )}
                            {!site && (
                                <div>
                                    <button type="button" className={studio.disclosure} aria-expanded={more} onClick={() => setMore(!more)}>
                                        {more ? '▾' : '▸'} Опис і теги
                                    </button>
                                    {more && (
                                        <div className={styles.more}>
                                            <div>
                                                <div className={studio.label}>Опис</div>
                                                <TextEditor handle={editor} mode="description" blocks={description} onChange={setDescription} label="Опис"
                                                    placeholder="Про що історія?" />
                                            </div>
                                            <TagPicker value={tags} onChange={setTags} />
                                        </div>
                                    )}
                                </div>
                            )}
                            {!site && <Toggle label="Для дорослих (18+)" isSelected={adult} onChange={setAdult} />}
                            {!own && (
                                <div>
                                    <Toggle label="Я маю право перекладати цей текст і викладати переклад (дозвіл автора або вільна ліцензія)."
                                        isSelected={rights} onChange={setRights} />
                                    <p className={styles.muted}><Link to="/rules">Правила сайту</Link></p>
                                </div>
                            )}
                            {create.isError && <Notice tone="error">{create.error.message}</Notice>}
                            {prepare.isError && <Notice tone="error">{prepare.error.message}</Notice>}
                            <div className={styles.submit}>
                                {site ? (
                                    <Button type="submit" pending={prepare.isPending} pendingLabel="Готуємо: читаємо й перекладаємо опис…"
                                        isDisabled={!link.trim() || !rights}>
                                        Створити й вибрати глави →
                                    </Button>
                                ) : (
                                    <Button type="submit" pending={create.isPending} pendingLabel="Створюємо…" isDisabled={!title.trim() || !own && !rights}>
                                        {kind === 'pasted' ? 'Далі: оригінал першої глави →' : 'Далі: перша глава →'}
                                    </Button>
                                )}
                                {!site && <span className={styles.muted}>Обкладинку, опис і теги можна додати пізніше.</span>}
                            </div>
                        </form>
                    )}
                </section>

                <aside aria-label="Як це працює" className={styles.aside}>
                    <b className={styles.panelTitle}>Що буде далі</b>
                    <ol className={styles.how}>
                        {nextSteps(kind, canRun, maySite).map((line) => <li key={line}>{line}</li>)}
                    </ol>
                </aside>
            </div>
        </section>
    );
}

/** The lock on «З дозволу» (the site's icon sets have none). */
function Lock() {
    return (
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden>
            <rect x="5" y="11" width="14" height="10" rx="2" />
            <path d="M8 11V8a4 4 0 018 0v3" />
        </svg>
    );
}
