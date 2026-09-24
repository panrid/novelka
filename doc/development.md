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

### Власник сайту
Під час першого запуску, якщо власника ще немає й задано всі три `NOVELKA_OWNER_*`,
створюється підтверджений акаунт власника. Наявний акаунт ніколи не підвищується
автоматично: якщо нік чи пошта зайняті, запуск зупиниться з поясненням. Після
створення приберіть `NOVELKA_OWNER_*` з оточення.

### Тестовий акаунт локально
Листи не йдуть назовні, їх ловить Mailpit: http://127.0.0.1:8025. Зареєструйтеся на
http://127.0.0.1:5173/register і відкрийте посилання з листа в Mailpit.
