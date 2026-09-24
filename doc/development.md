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
