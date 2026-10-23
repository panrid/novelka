import { Link } from '@tanstack/react-router';
import { lazy, Suspense } from 'react';
import { Loading } from '../../ui/Loading';
import { SCOPES } from '../me/ApiSettingsPage';
import styles from './developers.module.css';

/** The reference renders the whole description: its own chunk, fetched only on this page. */
const ApiReference = lazy(() => import('./ApiReference'));

const SITE = typeof window === 'undefined' ? 'https://novelka.in.ua' : window.location.origin;
const BASE = `${SITE}/api/v1`;

const CURL = `# Каталог: пошук і фільтри, 5 перекладів на сторінку
curl --get "${BASE}/catalog" --data-urlencode "q=маг води" -d limit=5

# Новела з усіма перекладами — від імені власника токена
curl -H "Authorization: Bearer $NOVELKA_TOKEN" "${BASE}/novels/mag-vody"

# Глави перекладу команди zoria; наступна сторінка — after=next із попередньої відповіді
curl "${BASE}/novels/mag-vody/translations/zoria/chapters?limit=100"
curl "${BASE}/novels/mag-vody/translations/zoria/chapters?limit=100&after=$NEXT"

# Текст глави 12; з If-None-Match — 304, якщо вона не змінилася
curl -i -H 'If-None-Match: "3f9a1c…"' "${BASE}/novels/mag-vody/translations/zoria/chapters/12"`;

const TYPESCRIPT = `const BASE = '${BASE}';
const TOKEN = process.env.NOVELKA_TOKEN; // необовʼязково

type Page<T> = { items: T[]; next: string | null };
type ChapterEntry = { number: number; label: string | null; title: string | null; publishedAt: string | null };

async function get<T>(path: string): Promise<T> {
    const response = await fetch(BASE + path, { headers: TOKEN ? { Authorization: \`Bearer \${TOKEN}\` } : {} });
    if (response.status === 429) {
        // Забагато запитів: сервер каже, скільки секунд почекати.
        const seconds = Number(response.headers.get('Retry-After') ?? '1');
        await new Promise((resolve) => setTimeout(resolve, seconds * 1000));
        return get(path);
    }
    if (!response.ok) {
        const problem = await response.json(); // application/problem+json
        throw new Error(\`\${response.status}: \${problem.detail}\`);
    }
    return (await response.json()) as T;
}

/** Усі опубліковані глави перекладу, сторінка за сторінкою. */
async function* chapters(novel: string, team: string): AsyncGenerator<ChapterEntry> {
    let after: string | null = null;
    do {
        const query = new URLSearchParams({ limit: '100' });
        if (after) query.set('after', after);
        const page: Page<ChapterEntry> = await get(\`/novels/\${novel}/translations/\${team}/chapters?\${query}\`);
        yield* page.items;
        after = page.next;
    } while (after);
}

for await (const chapter of chapters('mag-vody', 'zoria')) {
    console.log(chapter.number, chapter.title);
}`;

const STUDIO = `# Новий твір з нуля — токен із правом studio.
# kind: human — свій переклад, original — свій твір; team — адреса команди (без неї — ваша).
curl -X POST "${BASE}/studio/novels" \\
  -H "Authorization: Bearer $NOVELKA_TOKEN" -H "Content-Type: application/json" \\
  -d '{"kind":"human","title":"Маг води","slug":"mag-vody","language":"ja",
       "tags":["Фентезі"],"adult":false,"team":"zoria","rightsConfirmed":true,
       "description":[{"type":"paragraph","content":[{"text":"Хлопець, що говорить із водою."}]}]}'

# Обкладинка — окремим запитом: JPEG, PNG чи WebP до 5 МБ, сервер обріже до 2:3
curl -X PUT "${BASE}/studio/novels/mag-vody/translations/zoria/cover" \\
  -H "Authorization: Bearer $NOVELKA_TOKEN" -F "file=@cover.jpg"

# Стан: ongoing, completed, paused (можна до дати), abandoned; поля, яких немає, не змінюються
curl -X PATCH "${BASE}/studio/novels/mag-vody/translations/zoria/settings" \\
  -H "Authorization: Bearer $NOVELKA_TOKEN" -H "Content-Type: application/json" \\
  -d '{"status":"paused","pausedUntil":"2027-01-15"}'`;

const TYPES = `npx openapi-typescript ${BASE}/openapi.json -o novelka-api.d.ts`;

/**
 * «Для розробників» (етап 22.4): how to use the public API — the token and its rights, limits,
 * keyed pages, ETag, errors, examples in curl and TypeScript — and the reference read from the
 * OpenAPI description the server itself generates.
 */
export function DevelopersPage() {
    return (
        <article className={styles.page}>
            <h1>Для розробників</h1>
            <p>
                У Новелки відкритий API: читалки, боти, каталоги й агенти ШІ отримують те саме, що бачать читачі на сайті, —
                новели, каталог, переклади, опубліковані глави, томи, теги й «Хто це»; а команди перекладу працюють через
                нього зі Студією. Адреса — <code>{BASE}</code>.
            </p>
            <p>
                Повний опис у форматі OpenAPI 3.1 — <a href="/api/v1/openapi.json">/api/v1/openapi.json</a>: з нього можна
                згенерувати клієнт чи типи будь-якою мовою. Нижче — коротко про головне й довідник за цим описом.
            </p>
            <nav aria-label="Зміст" className={styles.toc}>
                <a href="#token">Токен</a> · <a href="#scopes">Права</a> · <a href="#limits">Частота</a> ·{' '}
                <a href="#pages">Сторінки</a> · <a href="#cache">Кеш</a> · <a href="#errors">Помилки</a> ·{' '}
                <a href="#versions">Версії</a> · <a href="#examples">Приклади</a> · <a href="#reference">Довідник</a>
            </nav>

            <h2 id="token">Токен</h2>
            <p>
                Читати публічне можна й без токена. З токеном API відповідає так, як бачить сайт його власник (наприклад,
                показує 18+, якщо власник підтвердив вік), а межа частоти — вища.
            </p>
            <ol>
                <li>Увійдіть і відкрийте <Link to="/me/settings/api">«Я → Налаштування → API»</Link>.</li>
                <li>Дайте токену назву, виберіть права й строк дії — і натисніть «Створити токен».</li>
                <li>Скопіюйте токен <code>nvk_…</code> одразу: його покажуть лише раз. Зберігайте його як пароль.</li>
                <li>Передавайте його в кожному запиті: <code>Authorization: Bearer nvk_…</code>.</li>
            </ol>
            <p>Відкликати токен можна там само будь-коли; там же видно, коли й звідки ним користувалися.</p>

            <h2 id="scopes">Права</h2>
            <p>Токен ніколи не дає більше, ніж може сам його власник, і лише в межах вибраних прав.</p>
            <dl className={styles.fields}>
                {Object.entries(SCOPES).map(([code, scope]) => (
                    <div key={code}>
                        <dt><code>{code}</code> — {scope.label}</dt>
                        <dd>{scope.hint}</dd>
                    </div>
                ))}
            </dl>
            <p>Для читання (<code>/api/v1</code>) досить права <code>read</code> — або токена взагалі не треба.</p>
            <p>
                Токен можна обмежити одним перекладом — тоді його не страшно віддати ботові чи агентові ШІ. Він працює лише з адресами
                цього перекладу (<code>/api/v1/studio/novels/…/translations/…</code>) і його новели, «Мої переклади» показують лише
                його, «Мої картинки» лишаються доступні, каталог і теги — теж. Усе інше, зокрема інші переклади й новий твір, — 403 з
                поясненням. Права <code>library</code> і <code>admin</code> з обмеженням не поєднуються.
            </p>
            <p>
                API Студії (<code>/api/v1/studio</code>) — для команд перекладу: глави й чернетки, публікація, оригінал,
                автопереклад із кошторисом у шагах, словник, правки, картинки. Тут потрібен токен із правом, названим у кожній
                операції довідника: <code>studio</code>, для запусків автоперекладу — <code>translate</code>, щоб надіслати
                правки — <code>suggest</code>. Діють ті самі правила команди, що й на сайті.
            </p>

            <h2 id="limits">Обмеження частоти</h2>
            <ul>
                <li>Без токена — 120 запитів на хвилину з однієї адреси.</li>
                <li>З токеном — 600 запитів на хвилину на токен.</li>
                <li>Понад межу сервер відповідає <code>429</code> із заголовком <code>Retry-After</code> — через скільки секунд можна знову.</li>
            </ul>

            <h2 id="pages">Сторінки за ключем</h2>
            <p>
                Довгі списки (каталог, глави перекладу) приходять сторінками: <code>{'{ "items": [...], "next": "…" }'}</code>.
                Щоб отримати наступну сторінку, передайте <code>next</code> як параметр <code>after</code>; коли{' '}
                <code>next</code> — <code>null</code>, це остання. Розмір сторінки — <code>limit</code> (каталог — до 50, глави — до
                100). Ключ — непрозорий рядок: не складайте його самі, беріть із <code>next</code>.
            </p>

            <h2 id="cache">Кеш і ETag</h2>
            <p>
                Кожна відповідь читання має <code>ETag</code>. Надішліть його в <code>If-None-Match</code> — і, якщо нічого не змінилося,
                сервер відповість <code>304</code> без тіла, а запит обійдеться дешево. Відповідь можна тримати хвилину
                (<code>Cache-Control: max-age=60</code>): <code>public</code> — для всіх, <code>private</code>, коли вона залежить
                від того, хто питає.
            </p>

            <h2 id="errors">Помилки</h2>
            <p>
                Помилка приходить як <code>application/problem+json</code>; що сталося — у полі <code>detail</code>, українською,
                так, щоб це можна було показати людині.
            </p>
            <dl className={styles.fields}>
                <div><dt><code>400</code></dt><dd>Хибний запит: параметр поза межами чи застарілий ключ сторінки.</dd></div>
                <div><dt><code>401</code></dt><dd>Токен недійсний: відкликаний, строк минув або хибний.</dd></div>
                <div><dt><code>403</code></dt><dd>Токен не має потрібного права або обмежений іншим перекладом.</dd></div>
                <div><dt><code>404</code></dt><dd>Немає такої новели, перекладу чи глави — або вона прихована від того, хто питає.</dd></div>
                <div><dt><code>429</code></dt><dd>Забагато запитів; зачекайте <code>Retry-After</code> секунд.</dd></div>
            </dl>

            <h2 id="versions">Версії</h2>
            <p>
                Версія — в адресі: <code>/api/v1</code>. У ній лише додається нове: поля, адреси, необовʼязкові параметри.
                Тож пропускайте поля, яких не знаєте. Те, що зламало б старі програми, зʼявиться лише в новій версії, а стара
                ще працюватиме.
            </p>

            <h2 id="examples">Приклади</h2>
            <h3>curl</h3>
            <pre className={styles.code}><code>{CURL}</code></pre>
            <h3>TypeScript</h3>
            <pre className={styles.code}><code>{TYPESCRIPT}</code></pre>
            <h3>Новий твір з нуля</h3>
            <p>
                Створити переклад чи свій твір, дати йому обкладинку й змінити стан можна без сайту — він зʼявиться в Студії
                так само, як створений там. Права й перевірки ті самі: створюють власник і перекладачі команди, змінює
                власник.
            </p>
            <pre className={styles.code}><code>{STUDIO}</code></pre>
            <p>Типи для TypeScript можна згенерувати з опису:</p>
            <pre className={styles.code}><code>{TYPES}</code></pre>

            <h2 id="reference">Довідник</h2>
            <Suspense fallback={<Loading shape="lines" />}>
                <ApiReference />
            </Suspense>
        </article>
    );
}
