# Архітектура: модулі й схема даних

Статус: погоджено 2026-09-24.

## Стек
- **Бекенд:** Java 25, Spring Boot 4, Spring Security, Spring Session JDBC (сесії в базі),
  jOOQ (код генерується зі схеми після міграцій Flyway), Flyway, PostgreSQL 17.
  Межі модулів перевіряє Spring Modulith: модуль не може лізти в пакети іншого.
- **Фронтенд:** React + TypeScript + Vite, TanStack Router і Query, React Aria Components.
  Стилі — CSS-змінні (токени теми) і CSS Modules. Темна тема за замовчуванням.
- **Живі оновлення:** SSE (`/api/events`): сповіщення, чат, прогрес перекладу.
  Опитування сервера немає.
- **Файли** (обкладинки, ілюстрації) — S3-сумісне сховище або локальний диск за одним
  інтерфейсом. Картинки віддаються через CDN або nginx, а не через Java.
- **Тести:** JUnit + Testcontainers (справжній PostgreSQL), Playwright для ключових
  сценаріїв на телефоні й desktop. Платних викликів моделей у тестах немає.

Один Gradle-проєкт: `backend/` (один Spring Boot застосунок, модулі — пакети)
і `frontend/`. Окремих Gradle-модулів для кожної частини не робимо: Modulith дає межі
без зайвої збірки.

## Модулі бекенду

Пакет `space.panrid.novelka.<модуль>`. Кожен модуль має публічний API (сервіси, події)
і внутрішній пакет `internal`, недоступний іншим модулям.

| Модуль | Відповідає за | Залежить від |
|---|---|---|
| `platform` | Безпека, CSRF, помилки API, SSE-шина, годинник, черга робіт у Postgres | — |
| `account` | Реєстрація, вхід, сесії, email-листи, нік, профіль «про себе» | platform |
| `access` | `AccessPolicy`: єдине місце, де вирішується «чи можна» | account, team |
| `team` | Команди, учасники, ролі | account |
| `catalog` | Новели (оригінали), переклади, естафета, теги, каталог, стрічки головної | team |
| `text` | Глави, ревізії, публікація, diff, редактор з форматуванням, імпорт `.txt`/`.md`, внесок | catalog, access, media |
| `suggestion` | Правки читачів: абзац або заміна в межах глави, перевірка | text, access |
| `reading` | Бібліотека, прогрес читання між пристроями, оцінки перекладу | catalog |
| `source` | Джерела оригіналів: Syosetu, ручний текст; імпорт і блоки | — |
| `ai` | Клієнт OpenRouter, каталог моделей і цін, журнал викликів | platform |
| `autotranslate` | Кошторис, запуск «до глави N», конвеєр аналіз → переклад → вичитка, словник | source, text, ai, billing |
| `billing` | Книга записів у шагах, баланси, резерви, пакети, собівартість | account, team |
| `payment` | Адаптери платіжних сервісів (спершу — лише ручне нарахування) | billing |
| `media` | Сховище картинок: обкладинки, аватарки, ілюстрації; перевірка, розміри | platform |
| `illustration` | Генерація картинок за фрагментом глави | ai, billing, text, media |
| `monetization` | Донати, правила доступу до глав, покупки, ранній доступ (після запуску; на старті — лише поле доступу «безкоштовно») | billing, text, team |
| `community` | Коментарі, чат, розмітка, згадки людей і команд, голоси, скарги | account, team |
| `messaging` | Розмови: особисті, групи, чат команди; блокування, «хто може писати» | account, team, community (розмітка) |
| `notification` | Вхідні, розсилка подій одержувачам, SSE | усі через події |
| `admin` | Налаштування сайту, модерація, журнал дій | усі |

**Читання й запис.** Запити на читання (головна, каталог, сторінка новели) можуть
з'єднувати таблиці різних модулів через jOOQ: так головна будується одним-двома
запитами, а не десятком викликів API. Змінює таблицю лише сервіс модуля-власника.

Модулі спілкуються викликами публічного API або подіями Spring (`ChapterPublished`,
`SuggestionReviewed`, `MentionCreated`…). Події пишуться в таблицю в тій самій
транзакції (outbox Spring Modulith), тож сповіщення не губляться.

## Ключові рішення

### Текст глави
- Глава — послідовність **блоків** зі стабільними ID: `heading`, `paragraph`, `preface`,
  `afterword`, `separator`, `image`. Зберігається в `jsonb` ревізії. Текст блока —
  фрагменти з позначками `bold`, `italic`, `underline`, `strike`:
  `{"id":"p7","type":"paragraph","content":[{"text":"Рьо ","marks":[]},{"text":"прокинувся","marks":["italic"]}]}`.
  Посилань, кольорів, розмірів і довільного HTML немає.
- **Ревізія не змінюється.** Кожне збереження редактора, прийнята правка чи результат
  автоперекладу створюють нову ревізію з `parent_id`.
- **Публікація — явна:** `chapter.published_revision_id`. Незавершений переклад і
  чернетка редактора читачам не видно.
- **Diff** рахується між двома ревізіями: спершу по блоках (за ID), усередині блока —
  по словах. Статистика змін (скільки блоків, хто) пишеться в ревізію при створенні.
  Звідси беремо «внесок кожного».
- **Редактор** — TipTap (ProseMirror) з урізаною схемою: лише ці блоки й позначки.
  Вставлений з буфера текст (з Word, Google Docs чи сайту) очищується до цієї схеми. ID блока зберігається
  як атрибут вузла: незмінні абзаци зберігають ID, нові отримують новий.
  Сервер повторно перевіряє документ за схемою й не довіряє клієнту.
  Правки, що чекають на перевірку, прив'язані до ID і точного тексту блока. Якщо текст
  блока змінився, правка стає `stale` і показується автору.
- Той самий редактор і формат — для опису новели (`description` у `jsonb`).
- **Імпорт глав** (власний переклад або оригінальний твір) — лише `.txt` і `.md`,
  один файл на кілька глав або кілька файлів:
  - `.txt`: абзаци розділені порожнім рядком. Нова глава — рядок, що починається
    з «Глава N» / «Розділ N», або окремий файл.
  - `.md` (CommonMark, бібліотека commonmark-java). Береться лише те, що підтримує
    сайт:

    | Markdown | На сайті |
    |---|---|
    | `# Назва` | Нова глава з цією назвою |
    | `**жирний**`, `__жирний__` | Жирний |
    | `*курсив*`, `_курсив_` | Курсив |
    | `~~закреслений~~` | Закреслений |
    | `++підкреслений++` | Підкреслений |
    | `![опис](https://…)` | Картинка за посиланням (сервер зберігає копію) |
    | `---` | Розділювач сцен |

    Решта — заголовки `##`, списки, посилання, код, таблиці, інший HTML — стає
    звичайним текстом без розмітки. Перед публікацією видно розбиття на глави
    й попередження, що саме спрощено.
- **Картинка за посиланням:** сервер сам завантажує файл і зберігає копію.
  Захист від SSRF:
  - лише `https`;
  - заборонені приватні й локальні адреси, перевірка після DNS;
  - ліміти часу й розміру (5 МБ);
  - перевірка, що вміст — справжня картинка.

  Читачі завжди отримують картинку з нашого сховища.
- Автозбереження чернетки редактора — окремий рядок `editor_draft`, а не ревізія.

### Автопереклад
- **Кошторис:** оригінали потрібних глав завантажуються безкоштовно. Ціна глави
  в шагах = ⌈знаків оригіналу / 10 000⌉ (знаки без пробілів і фуріґани; межа —
  налаштування сайту). Користувач
  бачить точну суму: «6 глав · 7 шагів».
- **Запуск:** шаги резервуються на весь діапазон. Після публікації кожної глави
  списується її ціна. Якщо запуск скасовано або глава не вдалася, шаги неперекладених
  глав повертаються.
- **Собівартість** у доларах пишеться в `ai_call` і не впливає на ціну для користувача.
  Звіт «собівартість шагу» в адмінці показує, чи вистачає запасу.
- **Черга в Postgres:** `job` і `job_step` (глава × етап). Воркери беруть етапи через
  `FOR UPDATE SKIP LOCKED`. Кожен етап ідемпотентний. Журнал `ai_call` зі станами
  `pending/complete/failed/uncertain`: невідомий результат автоматично не повторюється,
  як у v1. Гарантовано неоплачені збої (помилка до відправки, 429, 5xx з відповіддю
  провайдера) повторюються самі з паузою. Людина потрібна лише для `uncertain`.
  В аудиті v1 на 10 глав пішло 8 запусків — такого бути не повинно.
- Переклад завершеної глави створює ревізію й одразу її публікує. Діапазон:
  від першої неперекладеної глави до вказаної. Уже перекладені глави не чіпаються.

### Шаги й гроші
- **На старті** шагами користується лише власник сайту, і книга записів для цього
  не потрібна. Його «баланс» — залишок OpenRouter (`GET /api/v1/credits` з ключем
  керування, кеш на кілька хвилин), поділений на собівартість шагу. Собівартість
  береться з налаштування (за замовчуванням $0,036), а коли є статистика — із середньої
  фактичної ціни одного шагу за 30 днів. Кошторис для власника рахується так само,
  як для всіх: сума шагів глав (⌈знаків / 10 000⌉), у доларах — × собівартість шагу.
  Якщо `show_shah = false`, усі суми показуються в доларах. Запуск власника
  записується в `job` з `funding = site`. Його вартість — сума `ai_call`, без записів
  у книзі.
- Решта нижче — після запуску (етапи 10–12). Схема створюється одразу, щоб потім
  не переробляти `job` і рахунки команд.
- Облік — **книга записів** (подвійний запис) у **шагах**. Кожна операція —
  транзакція з кількох рядків, сума рядків = 0. Баланс рахунку = сума його рядків.
  Кешований `balance` на рахунку оновлюється в тій самій транзакції під блокуванням рядка.
- Рахунки: `user:{id}`, `team:{id}` і системні (`sold` — продані пакети,
  `granted` — ручні нарахування, `spent` — витрачені шаги, `holds` — резерви).
- Користувач бачить лише шаги. Гривні — тільки в ціні пакета під час оплати.
- Гроші окремо від шагів: `payment` зберігає суму в копійках і кількість нарахованих
  шагів. Собівартість — у мікродоларах в `ai_call`.
- Модуль `payment` має порт `PaymentProvider` (створити платіж, перевірити webhook).
  Перший адаптер — ручне нарахування власником. Paddle, Lemon Squeezy тощо
  додаються без змін у `billing`.

### Донати й платні глави
- Донат — транзакція книги записів `donation`: з рахунку користувача на рахунок
  команди, з повідомленням і позначкою «анонімно».
- Правило доступу задається на рівні перекладу, глава може його перевизначити:
  `free`, `paid` (ціна в шагах за пакет із K глав), `early` (ціна за пакет, глава
  безкоштовна через D днів після публікації). Чи відкрита глава, вирішує `AccessPolicy`:
  команда, покупка або `now() >= first_published_at + D`.
- Покупка пакета — транзакція `purchase_chapters` з рахунку читача на рахунок команди.
  Відкриває K наступних закритих глав, які читач ще не купив, починаючи з поточної.
- Закрита глава: API віддає назву, перші 3 абзаци й ціну, решту тексту — ні.
  Коментарі й правки доступні лише тим, у кого глава відкрита.

### Оригінальні твори
- Той самий `edition` з `kind = original`, а `novel.source = original` без оригіналу
  й джерела. Автор — людина на сайті (`author_account_id`), її команда — співавтори
  й редактори. Автоматичного перекладу й естафети немає: твір належить автору.
- Правки читачів, редактор, коментарі, бібліотека, ілюстрації — як у перекладах.
- На сторінці замість «автор · перекладач» — «Автор @нік». У каталозі є фільтр:
  «Переклади», «Оригінальні твори».

### Естафета перекладу
- `edition.status = abandoned` ставить власник. «Вільний для продовження» не
  зберігається, а обчислюється: `abandoned`, або власник неактивний довше
  `takeover.inactive_months`, або запит на продовження без відповіді довше 14 днів.
- Продовження — новий `edition` іншої команди з `continues_edition_id` і
  `first_number`. Читалка за цим зв'язком веде з останньої глави старого перекладу
  на наступну нового. Словник можна скопіювати при створенні.

### Мова й технічні дані в інтерфейсі
- API для сайту ніколи не віддає японський текст, ID джерела, аліаси, UUID чи назви
  етапів. Зовнішні адреси — з `slug`. Внутрішні `id` лишаються в JSON для запитів,
  але не показуються.
- При імпорті з Syosetu назва, опис і назви глав одразу перекладаються ШІ, ім'я автора
  транслітерується за українськими правилами. Це входить у переклад, окремо не
  оплачується.
- Словник у Студії показує лише українську частину запису. Японська — поле для ШІ.
- Повідомлення про помилки — з людським текстом і дією («Спробувати ще раз»,
  «Шаги повернено»), без кодів і стек-трейсів.

### Розмітка коментарів, чату й повідомлень
Джерело — простий текст із маркерами. **Правила ті самі, що й для імпорту `.md`**,
щоб не запам'ятовувати два варіанти:

| Пишеш | Бачиш |
|---|---|
| `**жирний**` або `__жирний__` | жирний |
| `*курсив*` або `_курсив_` | курсив |
| `++підкреслений++` | підкреслений |
| `~~закреслений~~` | закреслений |
| `\|\|спойлер\|\|` | спойлер (лише коментарі, чат і повідомлення) |
| `> цитата` | цитата |

Кнопки форматування в полі вводу вставляють ці маркери самі, тож знати їх не обов'язково. Згадки зберігаються як `<@u:id>` (людина)
і `<$t:id>` (команда), а показуються як `@нік` і `$команда`. Поле вводу підказує людей
після `@` і команди після `$`.
Власний невеликий парсер (однаковий на бекенді й фронтенді за спільними тестами)
будує дерево, React рендерить його. HTML ніколи не вставляється.

## Схема даних

Скорочено: `id bigint generated always as identity`, `created_at timestamptz` —
скрізь, де не сказано інше. Шаги — `bigint`. Гривні — `bigint` копійок.
Долари — `bigint` мікродоларів.

### account
```
account          id, nick (3–30: латиниця або кирилиця, не впереміш), nick_key unique,
                 email, email_key unique, email_verified_at,
                 password_hash, site_role (reader|moderator|admin|owner),
                 bio, avatar_image_id, last_seen_at, dm_policy (everyone|nobody),
                 adult_confirmed_at null,               -- підтвердив 18+
                 show_shah boolean default true,        -- власник сайту може бачити долари  -- писати й додавати в групи
                 created_at, deleted_at
account_block    blocker_id, blocked_id, created_at, pk (blocker_id, blocked_id)
nick_change      account_id, old_nick, new_nick, changed_at
email_token      token_hash pk, account_id, purpose (verify|reset), email, expires_at, used_at
spring_session*  таблиці Spring Session JDBC
```

### team
```
team             id, name (null → нік власника), handle, handle_key unique (для $згадок і /team/{handle}),
                 owner_id → account, personal (особиста команда за замовчуванням), created_at
team_member      team_id, account_id, role (translator|editor), added_by, added_at,
                 pk (team_id, account_id)
                 -- пропонувати правки може кожен; погоджують перекладачі й редактори
```

### catalog
```
novel            id, source (syosetu|manual|original), source_key unique null, source_url null,
                 title_original null, author_original null, -- лише для ШІ, в інтерфейсі не показуються
                 author_account_id null,                -- для original: автор — людина на сайті
                 title_uk, author_uk, description jsonb, -- машинний переклад / транслітерація при імпорті
                 source_chapter_count, slug unique       -- slug з української назви: mag-vody
novel_tag        novel_id, tag_id           tag: id, name, slug unique
edition          id, novel_id, team_id,        -- переклад команди або оригінальний твір title_uk, author_uk, description jsonb (null → з novel),
                 access_mode (free|paid|early), access_price_shah, access_pack_size,
                 access_free_after_days null, free_first_chapters,
                 cover_image_id null,
                 kind (human|machine|mixed|original), status (ongoing|completed|paused|abandoned),
                 adult boolean,                          -- 18+
                 continues_edition_id null, first_number (1 або N+1 для естафети),
                 last_published_at, hidden_at, hidden_reason, created_at,
                 unique (novel_id, team_id)
takeover_request id, edition_id, team_id, requested_by, created_at,
                 state (open|declined|granted|expired), answered_at
```

### source
```
source_chapter   id, novel_id, number, title, blocks jsonb, source_hash, fetched_at,
                 unique (novel_id, number)
source_chapter_history   source_chapter_id, blocks, source_hash, replaced_at
```

### text
```
chapter          id, edition_id, number, source_chapter_id null,
                 access_override (free|paid|early) null,
                 published_revision_id null, first_published_at, updated_at,
                 unique (edition_id, number)
revision         id, chapter_id, parent_id null, title, blocks jsonb,
                 origin (ai|editor|suggestion|replace|import), author_id null,
                 job_id null, source_hash null, stats jsonb, created_at
editor_draft     chapter_id, account_id, base_revision_id, text, updated_at,
                 pk (chapter_id, account_id)
contribution     revision_id, account_id, blocks_changed, chars_changed
                 (одна ревізія може мати кількох авторів, напр. пакет правок)
```

### suggestion
```
suggestion       id, chapter_id, base_revision_id, author_id,
                 batch_id, kind (block|replace|chapter), block_id, original_text, proposed_text,
                 proposed_blocks jsonb null (для kind = chapter — повний редактор),
                 find, replacement,                 -- заміна лише в межах однієї глави
                 note,
                 state (draft|pending|accepted|rejected|withdrawn|stale),
                 -- draft збирається в пакет; надсилається пакетом; прийнятий пакет = 1 ревізія на главу
                 reviewer_id, reviewed_at, review_note, applied_revision_id
```

### autotranslate і ai
```
glossary_entry   id, edition_id, key, japanese, reading, ukrainian, aliases jsonb,
                 kind, gender, facts jsonb, certainty, source_chapter, manual, revision
glossary_proposal id, edition_id, job_id, payload jsonb, fingerprint, state
job              id, edition_id, requested_by, kind (translate|proofread|illustrate),
                 first_number, last_number, state (queued|running|done|failed|cancelled),
                 funding (site|team), quote_shah, hold_tx_id null, charged_shah,
                 settings jsonb (моделі й ціни на момент запуску), error, created_at, finished_at
job_step         id, job_id, chapter_number, stage (fetch|analyze|translate|proofread|publish),
                 segment, state, attempts, locked_until, result jsonb
ai_call          id, job_step_id, model, provider, request jsonb, response jsonb,
                 state (pending|complete|failed|uncertain), cost_estimated_musd,
                 cost_actual_musd, tokens_in, tokens_out, created_at, finished_at
model_catalog    model, prices jsonb, capabilities jsonb, fetched_at
```

### billing і payment
```
ledger_account   id, kind (user|team|system), owner_id null, code null, balance_shah,
                 unique (kind, owner_id), unique (code)
ledger_tx        id, kind (purchase|grant|hold|capture|release|transfer|refund|adjust|
                 donation|purchase_chapters),
                 actor_id, job_id null, payment_id null, memo, created_at
ledger_entry     tx_id, account_id, amount_shah   -- sum(amount_shah) по tx = 0
shah_pack        id, shah, price_kop, active       -- пакети задає власник сайту
fx_rate          day pk, usd_uah, source            -- лише для звіту собівартості
payment          id, provider, external_id, account_id, pack_id, amount_kop, shah, state
                 (created|paid|failed|refunded), raw jsonb, created_at,
                 unique (provider, external_id)
```

### monetization
```
donation         ledger_tx_id pk, from_account_id, team_id, edition_id null, amount_shah,
                 message null, anonymous, created_at
chapter_unlock   account_id, chapter_id, ledger_tx_id, created_at, pk (account_id, chapter_id)
```

### illustration
```
image            id, owner_account_id, team_id null,
                 kind (cover|avatar|group_avatar|illustration|message),
                 source_url null,                   -- якщо додано за посиланням
                 storage_key, variants jsonb (розміри: 96, 320, 640…), mime, width, height,
                 sha256, prompt null, fragment null, job_id null,
                 hidden_at, hidden_by, hidden_reason
```
Завантаження: перевірка вмісту файлу (не лише розширення), до 5 МБ, обрізання на клієнті
(обкладинка 2:3, аватарка 1:1), на сервері — повторне обрізання, кілька розмірів і перекодування
в JPEG (або PNG, якщо є прозорість). Приймаються JPEG, PNG і WebP. Метадані EXIF не переносяться.
Віддавати WebP не вийшло: у Java немає вбудованого кодувальника WebP. Якщо трафік картинок стане
проблемою, перекодування можна перенести в окремий інструмент.
Картинку в главі задає блок `{type: "image", imageId}` ревізії.

### reading
```
library_entry    account_id, edition_id, list (reading|planned|done|paused|dropped),
                 updated_at, pk (account_id, edition_id)
reading_progress account_id, edition_id, chapter_number, position (0..1), updated_at
edition_rating   account_id, edition_id, score (1..5)
```

### community
```
comment          id, edition_id, chapter_number null (null — про переклад загалом), author_id,
                 reply_to (завжди коментар верхнього рівня), body, score,
                 edited_at, deleted_at, hidden_at, hidden_by, hidden_reason
chat_message     id, author_id, reply_to, body, deleted_at, hidden_*
mention          source (comment|chat|message), source_id, account_id null, team_id null
comment_vote     comment_id, account_id, value (-1|1)
edition_rating   edition_id, account_id, score (1..5)
report           id, reporter_id, target (comment|chat|dm|image), target_id, reason,
                 state (open|resolved|dismissed), resolved_by, created_at
```

### messaging
```
conversation     id, kind (direct|group|team), title null, avatar_image_id null,
                 direct_key unique null     -- "min:max" id двох людей, лише для direct
                 team_id unique null        -- лише для team
                 created_by, created_at, last_message_at
conversation_member conversation_id, account_id, role (admin|member), joined_at,
                 added_by, left_at null, last_read_message_id, muted,
                 pk (conversation_id, account_id)
message          id, conversation_id, author_id null, kind (text|system),
                 reply_to, body, created_at, edited_at, deleted_at
message_image    message_id, image_id, position     -- до 10 картинок
                 -- system: «mika додала oleh_k», «назву змінено»
```
Чат команди створюється в тій самій транзакції, що й команда. Склад синхронізує
обробник подій `TeamMemberAdded/Removed`. Для групи діють `dm_policy` і блокування
кожного, кого додають.

### notification і admin
```
notification     id, recipient_id, kind, payload jsonb, created_at, read_at
                 -- окремий рядок на кожного одержувача
event_publication  таблиця outbox Spring Modulith
site_setting     key pk, value jsonb, version, updated_by, updated_at
audit_log        id, actor_id, action, target_type, target_id, details jsonb, created_at
```

Сповіщення про нову главу отримують лише ті, в кого переклад є в бібліотеці
(«Читаю» або «В планах»), а не всі користувачі, як у v1.
