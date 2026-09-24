# Архітектура: модулі й схема даних

Статус: пропозиція на погодження.

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
| `text` | Глави, ревізії, публікація, diff, повний редактор, внесок | catalog, access |
| `suggestion` | Правки читачів, заміни «всі входження», перевірка | text, access |
| `reading` | Бібліотека, прогрес читання між пристроями, оцінки перекладу | catalog |
| `source` | Джерела оригіналів: Syosetu, ручний текст; імпорт і блоки | — |
| `ai` | Клієнт OpenRouter, каталог моделей і цін, журнал викликів | platform |
| `autotranslate` | Кошторис, запуск «до глави N», конвеєр аналіз → переклад → вичитка, словник | source, text, ai, billing |
| `billing` | Книга записів у кроках, баланси, резерви, пакети, собівартість | account, team |
| `payment` | Адаптери платіжних сервісів (спершу — лише ручне нарахування) | billing |
| `illustration` | Завантаження й генерація картинок за фрагментом | ai, billing, text |
| `community` | Коментарі, чат, розмітка, згадки людей і команд, голоси | account, team |
| `notification` | Вхідні, розсилка подій одержувачам, SSE | усі через події |
| `admin` | Налаштування сайту, модерація, журнал дій | усі |

Модулі спілкуються викликами публічного API або подіями Spring (`ChapterPublished`,
`SuggestionReviewed`, `MentionCreated`…). Події пишуться в таблицю в тій самій
транзакції (outbox Spring Modulith), тож сповіщення не губляться.

## Ключові рішення

### Текст глави
- Глава — послідовність **блоків** зі стабільними ID: `heading`, `paragraph`, `preface`,
  `afterword`, `separator`, `image`. Зберігається в `jsonb` ревізії.
- **Ревізія не змінюється.** Кожне збереження редактора, прийнята правка чи результат
  автоперекладу створюють нову ревізію з `parent_id`.
- **Публікація — явна:** `chapter.published_revision_id`. Незавершений переклад і
  чернетка редактора читачам не видно.
- **Diff** рахується між двома ревізіями: спершу по блоках (за ID), усередині блока —
  по словах. Статистика змін (скільки блоків, хто) пишеться в ревізію при створенні.
  Звідси беремо «внесок кожного».
- **Повний редактор** працює з главою як з одним текстом: абзаци розділені порожнім
  рядком, картинка — окремий рядок-маркер. При збереженні абзаци знову
  зіставляються з блоками: незмінні зберігають ID, змінені отримують новий текст
  з тим самим ID, нові — новий ID. Правки, що чекають на перевірку, прив'язані до ID
  і точного тексту блока. Якщо текст блока змінився, правка стає `stale` і показується
  автору.
- Автозбереження чернетки редактора — окремий рядок `editor_draft`, а не ревізія.

### Автопереклад
- **Кошторис:** оригінали потрібних глав завантажуються безкоштовно. Ціна глави
  в кроках = ⌈знаків оригіналу / 10 000⌉ (межа — налаштування сайту). Користувач
  бачить точну суму: «6 глав · 7 кроків».
- **Запуск:** кроки резервуються на весь діапазон. Після публікації кожної глави
  списується її ціна. Якщо запуск скасовано або глава не вдалася, кроки неперекладених
  глав повертаються.
- **Собівартість** у доларах пишеться в `ai_call` і не впливає на ціну для користувача.
  Звіт «собівартість кроку» в адмінці показує, чи вистачає запасу.
- **Черга в Postgres:** `job` і `job_step` (глава × етап). Воркери беруть етапи через
  `FOR UPDATE SKIP LOCKED`. Кожен етап ідемпотентний. Журнал `ai_call` зі станами
  `pending/complete/failed/uncertain`: невідомий результат автоматично не повторюється,
  як у v1. Гарантовано неоплачені збої (помилка до відправки, 429, 5xx з відповіддю
  провайдера) повторюються самі з паузою. Людина потрібна лише для `uncertain`.
  В аудиті v1 на 10 глав пішло 8 запусків — такого бути не повинно.
- Переклад завершеної глави створює ревізію й одразу її публікує. Діапазон:
  від першої неперекладеної глави до вказаної. Уже перекладені глави не чіпаються.

### Кроки й гроші
- Облік — **книга записів** (подвійний запис) у **кроках**. Кожна операція —
  транзакція з кількох рядків, сума рядків = 0. Баланс рахунку = сума його рядків.
  Кешований `balance` на рахунку оновлюється в тій самій транзакції під блокуванням рядка.
- Рахунки: `user:{id}`, `team:{id}` і системні (`sold` — продані пакети,
  `granted` — ручні нарахування, `spent` — витрачені кроки, `holds` — резерви).
- Користувач бачить лише кроки. Гривні — тільки в ціні пакета під час оплати.
- Гроші окремо від кроків: `payment` зберігає суму в копійках і кількість нарахованих
  кроків. Собівартість — у мікродоларах в `ai_call`.
- Модуль `payment` має порт `PaymentProvider` (створити платіж, перевірити webhook).
  Перший адаптер — ручне нарахування власником. Paddle, Lemon Squeezy тощо
  додаються без змін у `billing`.

### Естафета перекладу
- `translation.status = abandoned` ставить власник. «Вільний для продовження» не
  зберігається, а обчислюється: `abandoned`, або власник неактивний довше
  `takeover.inactive_months`, або запит на продовження без відповіді довше 14 днів.
- Продовження — новий `translation` іншої команди з `continues_translation_id` і
  `first_number`. Читалка за цим зв'язком веде з останньої глави старого перекладу
  на наступну нового. Словник можна скопіювати при створенні.

### Розмітка коментарів і чату
Джерело — простий текст із маркерами `**жирний**`, `*курсив*`, `__підкреслений__`,
`~~закреслений~~`, `||спойлер||`, `> цитата`, згадки `<@u:id>` / `<@t:id>`.
Власний невеликий парсер (однаковий на бекенді й фронтенді за спільними тестами)
будує дерево, React рендерить його. HTML ніколи не вставляється.

## Схема даних

Скорочено: `id bigint generated always as identity`, `created_at timestamptz` —
скрізь, де не сказано інше. Кроки — `bigint`. Гривні — `bigint` копійок.
Долари — `bigint` мікродоларів.

### account
```
account          id, nick, nick_key unique, email unique, email_verified_at,
                 password_hash, site_role (reader|moderator|admin|owner),
                 bio, avatar_image_id, last_seen_at, created_at, deleted_at
nick_change      account_id, old_nick, new_nick, changed_at
email_token      token_hash pk, account_id, purpose (verify|reset), email, expires_at, used_at
spring_session*  таблиці Spring Session JDBC
```

### team
```
team             id, name (null → нік власника), slug unique, owner_id → account, created_at
team_member      team_id, account_id, role (translator|editor), added_by, added_at,
                 pk (team_id, account_id)
                 -- пропонувати правки може кожен; погоджують перекладачі й редактори
```

### catalog
```
novel            id, source (syosetu|manual), source_key unique null, source_url,
                 title_original, author_original, source_chapter_count, cover_image_id,
                 slug unique
novel_tag        novel_id, tag_id           tag: id, name, slug unique
translation      id, novel_id, team_id, title_uk, author_uk, description_uk,
                 kind (human|machine|mixed), status (ongoing|completed|paused|abandoned),
                 continues_translation_id null, first_number (1 або N+1 для естафети),
                 last_published_at, hidden_at, hidden_reason, created_at,
                 unique (novel_id, team_id)
takeover_request id, translation_id, team_id, requested_by, created_at,
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
chapter          id, translation_id, number, source_chapter_id null,
                 published_revision_id null, first_published_at, updated_at,
                 unique (translation_id, number)
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
                 find, replacement, scope (chapter|translation), note,
                 state (draft|pending|accepted|rejected|withdrawn|stale),
                 -- draft збирається в пакет; надсилається пакетом; прийнятий пакет = 1 ревізія на главу
                 reviewer_id, reviewed_at, review_note, applied_revision_id
```

### autotranslate і ai
```
glossary_entry   id, translation_id, key, japanese, reading, ukrainian, aliases jsonb,
                 kind, gender, facts jsonb, certainty, source_chapter, manual, revision
glossary_proposal id, translation_id, job_id, payload jsonb, fingerprint, state
job              id, translation_id, requested_by, kind (translate|proofread|illustrate),
                 first_number, last_number, state (queued|running|done|failed|cancelled),
                 quote_steps, hold_tx_id, charged_steps,
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
ledger_account   id, kind (user|team|system), owner_id null, code null, balance_steps,
                 unique (kind, owner_id), unique (code)
ledger_tx        id, kind (purchase|grant|hold|capture|release|transfer|refund|adjust),
                 actor_id, job_id null, payment_id null, memo, created_at
ledger_entry     tx_id, account_id, amount_steps   -- sum(amount_steps) по tx = 0
step_pack        id, steps, price_kop, active       -- пакети задає власник сайту
fx_rate          day pk, usd_uah, source            -- лише для звіту собівартості
payment          id, provider, external_id, account_id, pack_id, amount_kop, steps, state
                 (created|paid|failed|refunded), raw jsonb, created_at,
                 unique (provider, external_id)
```

### illustration
```
image            id, owner_account_id, team_id null, storage_key, mime, width, height,
                 kind (cover|avatar|illustration), prompt null, fragment null, job_id null
```
Картинку в главі задає блок `{type: "image", imageId}` ревізії.

### reading
```
library_entry    account_id, translation_id, list (reading|planned|done|paused|dropped),
                 updated_at, pk (account_id, translation_id)
reading_progress account_id, translation_id, chapter_number, position (0..1), updated_at
translation_rating account_id, translation_id, score (1..5)
```

### community
```
comment          id, target (translation|chapter), target_id, author_id, reply_to,
                 body, edited_at, deleted_at, hidden_at, hidden_by, hidden_reason
chat_message     id, author_id, reply_to, body, deleted_at, hidden_*
mention          source (comment|chat), source_id, account_id null, team_id null
vote             account_id, target (comment|translation), target_id, value (-1|1)
```

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
