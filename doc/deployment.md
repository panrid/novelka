# Деплой

Прод — VPS `62.72.33.247`, адреса `https://novelka.panrid.space`. На сервері в
`/opt/novelka`: `.env.production` (секрети, лише на сервері), `app/` (JAR і Dockerfile),
`infra/` (`production.compose.yaml`, `Caddyfile`). Compose-проєкт `novelka`: PostgreSQL,
застосунок і Caddy (HTTPS, сертифікати сам).

## Як іде деплой

Push у `main` → `.github/workflows/deploy-production.yml`:

1. Паралельно тести бекенду (з PostgreSQL через Testcontainers) і фронтенду (`npm run check`).
2. Один раз збирається `novelka.jar` із сайтом усередині.
3. Цей JAR копіюється на сервер, `docker compose up -d --build`, потім перевірка
   `/api/health` і головної.

Новий push чекає, поки закінчиться попередній деплой. Відкат — «Re-run» workflow зі старого
коміту. Міграції вниз немає: перед ризиковими міграціями зробіть копію бази.

GitHub secrets: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, за бажання `DEPLOY_KNOWN_HOSTS`.

## Змінні в `.env.production`

Назви лишилися від v1; `production.compose.yaml` переводить їх у назви застосунку.

| Змінна | Для чого |
|---|---|
| `NOVELKA_DB_PASSWORD` | пароль PostgreSQL |
| `OPENROUTER_API_KEY` | автопереклад |
| `OPENROUTER_MANAGEMENT_KEY` | залишок на OpenRouter («Шаги» власника) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | листи (Resend) |
| `NOVELKA_OWNER_NICK`, `NOVELKA_OWNER_EMAIL`, `NOVELKA_OWNER_PASSWORD` | створити власника при першому запуску; після — прибрати |

Змінні `NOVELKA_*_MODEL` і `*_USD_M` від v1 новий сайт не читає: моделі й ціни власник
задає на сторінці «Шаги».

## Від v1

Контейнери v1 (Compose-проєкт `infra`) перший деплой видаляє. Том з базою v1
(`infra_novelka-data`) лишається на сервері як копія; видалити, коли вже не потрібен:
`docker volume rm infra_novelka-data infra_caddy-data infra_caddy-config`.

## Резервні копії

Сервіс `backup` раз на добу кладе в том `novelka_backups` копію бази
(`novelka-РРРР-ММ-ДД.dump`, 14 днів) і картинок (`media-РРРР-ММ-ДД.tgz`, 7 днів).
Копії лежать на тому самому сервері: від помилки чи поганої міграції вони рятують,
від утрати сервера — ні. Час від часу забирайте свіжу копію собі:

```sh
ssh novelka@62.72.33.247 'docker run --rm -v novelka_backups:/b alpine ls -l /b'
ssh novelka@62.72.33.247 'docker run --rm -v novelka_backups:/b alpine cat /b/novelka-РРРР-ММ-ДД.dump' > novelka.dump
```

Відновлення бази (сайт на цей час зупиняється):

```sh
cd /opt/novelka
docker compose --env-file .env.production -f infra/production.compose.yaml stop app
docker compose --env-file .env.production -f infra/production.compose.yaml exec -T postgres \
    pg_restore -U novelka -d novelka --clean --if-exists < novelka.dump
docker compose --env-file .env.production -f infra/production.compose.yaml start app
```

Картинки: `docker run --rm -v novelka_media:/m -v novelka_backups:/b alpine tar xzf /b/media-РРРР-ММ-ДД.tgz -C /m`.

## Моніторинг

`.github/workflows/uptime.yml` кожні 15 хвилин перевіряє `/api/health` і головну.
Якщо сайт не відповідає, запуск падає, і GitHub надсилає лист власнику репозиторію.
