# Розробка

## Що потрібно
- JDK 25, Node.js 22.13+ з npm, Docker Desktop.

## Запуск локально

```sh
./gradlew :backend:bootRun          # API на http://127.0.0.1:8080
npm --prefix frontend run dev       # сайт на http://127.0.0.1:5173
```

`bootRun` сам піднімає `infra/compose.yaml`:
- **PostgreSQL** на `127.0.0.1:5433`. Порт і назва проєкту інші, ніж у v1, тож обидві
  бази можуть працювати поруч.
- **Mailpit** — ловить усі листи сайту: http://127.0.0.1:8025.

Vite пересилає `/api` на 8080.

## Перевірки

```sh
./gradlew check        # бекенд (JUnit + Testcontainers) і фронтенд (tsc, eslint, vitest)
./gradlew :backend:bootJar   # backend/build/libs/novelka.jar разом зі зібраним сайтом
```

Для тестів і генерації jOOQ потрібен запущений Docker.

### Тести бекенду
- Класи тестів ідуть у кількох JVM одночасно: половина ядер, не більше 4 (машина CI з 4 ядрами —
  2 потоки, ноутбук з 8 — 4). Кожна JVM піднімає свій контекст Spring і свій контейнер PostgreSQL,
  тож бази потоків окремі й тести один одному не заважають. Усередині JVM тести йдуть по черзі:
  спільні черга автоперекладу, поштова скринька й підроблені сервіси.
- Весь набір — ≈ 1 хв локально (8 ядер); до 2026-10-18 було ≈ 7 хв.
- Інтеграційні тести мають спільний контекст (`@IntegrationTest`); власні властивості чи
  `@MockitoBean` у класі створюють ще один контекст — додавайте лише коли без цього ніяк.
- Пауза тестових контекстів Spring вимкнена (`backend/src/test/resources/spring.properties`):
  інакше закриття контексту наприкінці чекало планувальник 150 с.

```sh
./gradlew :backend:test --tests '*AccountFlowTests'                                    # один клас
./gradlew :backend:test --tests '*AccountFlowTests.signInAcceptsNickOrEmailInAnyCase'  # один тест
./gradlew :backend:test -PtestForks=1      # слабка машина: одна JVM (або 2, 3…)
./gradlew :backend:test --rerun            # повторити, хоч нічого не змінилось
```

## База даних
- Міграції: `backend/src/main/resources/db/migration/V{N}__{опис}.sql`. Flyway
  застосовує їх під час старту.
- Класи jOOQ генеруються з міграцій. `generateJooq` запускає тимчасовий PostgreSQL,
  застосовує міграції й читає схему. Результат — `backend/build/generated-sources/jooq`,
  пакет `space.panrid.novelka.jooq`. Задача запускається сама перед компіляцією
  і кешується, доки міграції не змінились.
- JVM працює в UTC: драйвер PostgreSQL передає часовий пояс JVM, а `Europe/Kiev`
  PostgreSQL 17 не приймає.

### Новела для перевірки
Поки немає Студії (етап 3), адміністратор може завантажити новелу з `.txt` або `.md`
(формат — [рішення 23](plan.md#рішення-власника)):

```sh
curl -b cookies.txt -H "X-XSRF-TOKEN: $XSRF" -X POST http://127.0.0.1:8080/api/admin/novels \
  -F "file=@novel.md;type=text/markdown" -F "title=Назва" -F "author=Автор" \
  -F "description=Опис" -F "tags=Фентезі, Магія" -F "kind=human" -F "adult=false"
```

`kind` — `human`, `machine`, `mixed` або `original`. Файл має бути в UTF-8.

## Модулі
Кожен модуль бекенду — пакет `space.panrid.novelka.<модуль>` (див.
[architecture.md](architecture.md)). `ModularityTests` не дає модулю лізти
у внутрішні пакети іншого.

## Змінні середовища (production)

| Змінна | За замовчуванням |
|---|---|
| `NOVELKA_DB_URL` | `jdbc:postgresql://127.0.0.1:5433/novelka` |
| `NOVELKA_DB_USER` / `NOVELKA_DB_PASSWORD` | `novelka` / `novelka` |
| `NOVELKA_SERVER_ADDRESS` | `127.0.0.1` |
| `NOVELKA_SERVER_PORT` | `8080` |
| `NOVELKA_PUBLIC_URL` | `http://127.0.0.1:5173` — адреса сайту для посилань у листах |
| `NOVELKA_SECURE_COOKIES` | `false`; у production — `true` (лише HTTPS) |
| `NOVELKA_MEDIA_DIR` | `data/media` — завантажені картинки |
| `NOVELKA_MAIL_HOST` / `NOVELKA_MAIL_PORT` | `127.0.0.1` / `1025` (Mailpit) |
| `NOVELKA_MAIL_USER` / `NOVELKA_MAIL_PASSWORD` | порожні |
| `NOVELKA_MAIL_AUTH` / `NOVELKA_MAIL_STARTTLS` | `false` / `false` |
| `NOVELKA_MAIL_FROM` | `Новелка <no-reply@novelka.panrid.space>` |
| `NOVELKA_OWNER_NICK` / `NOVELKA_OWNER_EMAIL` / `NOVELKA_OWNER_PASSWORD` | порожні |
| `NOVELKA_OPENROUTER_KEY` | порожній; без нього автопереклад не запускається |
| `NOVELKA_OPENROUTER_MANAGEMENT_KEY` | порожній; ключ керування OpenRouter лише для залишку коштів («баланс» власника) |

### Власник сайту
Під час першого запуску, якщо власника ще немає й задано всі три `NOVELKA_OWNER_*`,
створюється підтверджений акаунт власника. Наявний акаунт ніколи не підвищується
автоматично: якщо нік чи пошта зайняті, запуск зупиниться з поясненням. Після
створення приберіть `NOVELKA_OWNER_*` з оточення.

### Тестовий акаунт локально
Листи не йдуть назовні, їх ловить Mailpit: http://127.0.0.1:8025. Зареєструйтеся на
http://127.0.0.1:5173/register і відкрийте посилання з листа в Mailpit.

## Розсилка про нові глави

Публікація глави лише ставить задачу в `chapter_news`; воркер (кожні 2 с) розсилає її
підписникам пачками по 500 і кладе повідомлення Telegram у `telegram_outbox`, звідки їх
надсилає окремий відправник (кожну секунду, ≤ 25 на секунду, повтори з паузами). Що застрягло —
видно в цих таблицях: `state`, `attempts`, `last_error`, `not_before`. У тестах розклад вимкнено
(`novelka.notifications.chapter-news`, `novelka.telegram.outbox`), тести викликають
`ChapterNewsQueue.drain()` і `TelegramQueue.drain()`.

## Автопереклад

Черга живе в PostgreSQL (`job`, `job_step`), воркер бере наступну главу кожні 3 секунди.
Кожен виклик моделі спершу записується в `ai_call` як `pending`:

- `complete` — відповідь збережено; той самий запит удруге не оплачується, а бере її;
- `failed` — точно не оплачено (не надіслано, 429, 502/503) або відмовлено (ключ, 402);
  неоплачене повторюється саме з паузою 30 с … 1 год;
- `uncertain` — відповідь загубилася й могла бути оплачена; завдання зупиняється,
  і лише «Продовжити» від власника дозволяє нову спробу.

Моделі, ціни, розмір частини глави й собівартість шагу власник змінює на сторінці
«Шаги» (`/me/wallet`); кожне завдання зберігає копію налаштувань на момент запуску.
У тестах Syosetu й модель підроблені (`FakeSyosetu`, `FakeModel`), платних викликів немає.

## Токени API

Токен створюється в «Налаштування → API» (`/me/settings/api`) і показується один раз. Запит із ним:

```sh
curl -H "Authorization: Bearer nvk_…" http://127.0.0.1:8080/api/me
```

CSRF для таких запитів не потрібен. Токен діє як його власник: не більше, ніж може сам власник, і лише
в межах прав. Права й дозволені адреси — `apitoken/internal/TokenScopes`. Адреса, якої там немає,
токеном недоступна: пароль, пошта, вхід, самі токени. Кожна зміна, зроблена токеном, пишеться в
`api_token_write`.

### Публічний API читання

`/api/v1` — без входу або з токеном із правом `read`: `catalog`, `tags`, `novels/{slug}`,
`novels/{slug}/translations/{team}/chapters`, `…/chapters/{n}`, `…/volumes`, `…/who`, `…/characters`.
Списки читаються за ключем: `next` з відповіді передається як `after`. Кожна відповідь має `ETag`;
на `If-None-Match` сервер відповідає 304. Відповідь можна кешувати хвилину.

Обмеження частоти рахуються в пам'яті сервера, у ковзному вікні в одну хвилину. Після перезапуску
лічильники починаються з нуля. Понад ліміт сервер відповідає 429 з `Retry-After`.

| Властивість | За замовчуванням |
|---|---|
| `novelka.api.token-requests-per-minute` | 600 — на один токен, будь-які адреси |
| `novelka.api.address-requests-per-minute` | 120 — на одну адресу, `/api/v1` без токена |

### API Студії й перекладу

`/api/v1/studio` — з токеном (`studio`, для запусків — `translate`, щоб надіслати правки — `suggest`)
або із сесією. Переклад — `novels/{slug}/translations/{team}`, як у публічному API. Права — ті самі,
що в Студії: чужий переклад — 403, оригінал бачать лише перекладачі команди, ШІ — лише з шагами.

```sh
T="Authorization: Bearer nvk_…"; B=http://127.0.0.1:8080/api/v1/studio/novels/mother-of-learning/translations/zorian
curl -H "$T" -H 'Content-Type: application/json' -X POST $B/originals \
  -d '{"language":"en","chapters":[{"title":"Chapter 1","text":"Zorian woke up…"}]}'
curl -H "$T" -H 'Content-Type: application/json' -X POST $B/runs/estimate -d '{"to":1}'
curl -H "$T" -H 'Content-Type: application/json' -X POST $B/runs -d '{"to":1,"publish":false}'
curl -H "$T" $B/runs/42/log
curl -H "$T" -X POST $B/chapters/1/publish -H 'Content-Type: application/json' -d '{}'   # моя чернетка
```

### Опис API (OpenAPI)

springdoc генерує OpenAPI 3.1 з контролерів `/api/v1` і віддає його на `/api/v1/openapi.json`. Внутрішній
API сайту в опис не потрапляє. Налаштування — `springdoc.*` в `application.yaml`. Спільні для всіх операцій
частини додає `platform/web/ApiDescription`: токен, відповіді з помилками, `ETag`. Описи операцій,
параметрів і полів пишемо українською в `@Operation`, `@Parameter` і `@Schema`. Приклади мають бути
незмінні: жодного «зараз», дати — сталі рядки.

Знімок опису лежить у `doc/api/openapi-v1.json`, і `ApiDescriptionTests` порівнює його з тим, що генерує
код. Кожен запуск пише свіжий опис у `backend/build/api/openapi-v1.json`, а якщо вони розходяться, тест
друкує новий опис повністю. Змінили API — оновіть знімок і закомітьте разом зі зміною:

```sh
./gradlew :backend:test --tests '*ApiDescriptionTests' -PupdateApiSnapshot
```

Можна й скопіювати `backend/build/api/openapi-v1.json` чи JSON із тексту помилки. Те, що ламає програми,
написані за знімком, тест не пропускає навіть з `-PupdateApiSnapshot`. Це зникла адреса чи операція,
зникле поле відповіді чи змінений тип, поле, яке перестало бути завжди, новий обов'язковий параметр.
У `/v1` API лише росте; ламке — тільки в новій версії. Нові адреси під `/api/v1` (наприклад,
`/api/v1/studio`) потрапляють в опис самі, треба лише оновити знімок.

Сторінка «Для розробників» (`/developers`) показує довідник за цим описом: `frontend/src/pages/developers`.

### Вебхуки

«Налаштування → API → Вебхуки»: https-адреса, події, переклади (усі доступні або вибрані). Секрет
`whsec_…` показується один раз. Керувати вебхуками можна лише з сесії, не токеном.

| Подія | Коли | Кому |
|---|---|---|
| `chapter.published` | читачі можуть відкрити нові глави | кожному, хто бачить переклад (приховане й 18+ — як на сайті) |
| `run.finished` | запуск автоперекладу переклав усі глави | лише команді перекладу |
| `run.stopped` | запуск зупинився: помилка (`failed`) або скасування (`cancelled`) | лише команді |
| `suggestion.created` | читач надіслав правки команді | лише команді |
| `source.chapters` | на сайті оригіналу зʼявилися глави для перекладу | лише команді |
| `ping` | «Надіслати пробну подію» | власнику вебхука |

Запит — `POST` із `Content-Type: application/json; charset=utf-8` і заголовками:

- `X-Novelka-Event` — код події;
- `X-Novelka-Delivery` — UUID доставки; той самий на кожній спробі й у «Повторити», тож повтор
  легко відкинути (доставка «щонайменше раз»);
- `X-Novelka-Timestamp` — секунди Unix у мить спроби;
- `X-Novelka-Signature` — `sha256=` і hex HMAC-SHA256 секретом від `<timestamp>.<тіло>`.

```json
{
  "id": "9f1c…", "event": "chapter.published", "createdAt": "2026-10-19T12:00:00Z",
  "data": {
    "translation": { "novel": "slug", "team": "handle", "title": "Назва", "url": "https://novelka.in.ua/n/slug?t=handle" },
    "first": 12, "last": 12,
    "chapters": [{ "number": 12, "label": null, "title": "Назва глави", "url": "https://novelka.in.ua/n/slug/12?t=handle" }]
  }
}
```

`data` інших подій: `run.*` — `translation`, `run` (номер запуску), `state` (`done`, `failed`, `cancelled`),
`first`, `last`, `error`; `suggestion.created` — `translation`, `author` (нік), `count`;
`source.chapters` — `translation`, `first`, `last`; `ping` — `message`. Внутрішніх id і тексту
оригіналу в подіях немає; поля лише додаються.

Перевірка на боці отримувача (Node.js):

```ts
const expected = 'sha256=' + createHmac('sha256', secret).update(`${timestamp}.${rawBody}`).digest('hex');
const ok = timingSafeEqual(Buffer.from(expected), Buffer.from(signature)) && Math.abs(Date.now() / 1000 - Number(timestamp)) < 300;
```

Доставка: подію кладе в `webhook_delivery` та сама транзакція, що її спричинила; воркер (кожні 2 с)
надсилає до 10 одночасно. Відповідь 2xx — доставлено; інше (і переадресації — їх не виконуємо) —
нова спроба через 10 с, 1 хв, 5 хв, 15 хв, 1 год, 3 год, 6 год, 12 год, далі «не доставлено». На
все — 10 с, разом зі зʼєднанням. Перед кожною спробою знову перевіряється, чи власник ще бачить
переклад (чи ще в команді). Адресу, що відмовляє 20 разів поспіль понад добу, вимкнено, власник
отримує сповіщення. Журнал (`/api/me/webhooks/{id}/deliveries`) — останні 50 доставок, місяць.

SSRF: лише `https`, порт 443 або від 1024, без імені й пароля в адресі; кожна адреса, у яку
розвʼязується назва, має бути публічною (не loopback, приватні мережі, link-local з
169.254.169.254, CGNAT, multicast, зарезервовані, IPv6 ULA/link-local і IPv6-обгортки IPv4).
Перевірка — при збереженні й перед кожною спробою, а зʼєднання йде саме на перевірені адреси
(`PublicAddresses`, `WebhookTransport`). У тестах `LocalWebhooks` пропускає `http://127.0.0.1` лише
для портів тестової заглушки; розклад вимкнено (`novelka.webhooks.outbox`), тести викликають
`WebhookOutbox.drain()`.
