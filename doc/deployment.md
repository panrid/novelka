# Деплой

Прод — VPS `62.72.33.247`, адреса `https://novelka.in.ua`. На сервері в
`/opt/novelka`: `.env.production` (секрети, лише на сервері), `app/` (JAR і Dockerfile),
`infra/` (`production.compose.yaml`, `Caddyfile`). Compose-проєкт `novelka`: PostgreSQL,
застосунок і Caddy (HTTPS, сертифікати сам).

## Адреси

DNS (у реєстратора `novelka.in.ua`): A-записи `@`, `www`, `dev` → `62.72.33.247`. Caddy
(`infra/Caddyfile`):

| Адреса | Що робить |
|---|---|
| `novelka.in.ua` | сайт; Студія — `/studio` |
| `www.novelka.in.ua`, `novelka.panrid.space` | 301 на `https://novelka.in.ua` з тим самим шляхом і параметрами |
| `dev.novelka.in.ua` | заглушка 503 з `noindex`, доки немає тестового сайту |

Усі посилання, які будує застосунок (листи, `sitemap.xml`, `robots.txt`, canonical і превʼю,
EPUB, Telegram, адреса повернення Google), беруться з `NOVELKA_PUBLIC_URL`; за замовчуванням у
`production.compose.yaml` — `https://novelka.in.ua`, у `.env.production` її не задаємо. Вебхук
Telegram застосунок ставить сам на `<адреса>/api/telegram/webhook` під час запуску.

Google Cloud Console, OAuth-клієнт: «Authorized JavaScript origins» — `https://novelka.in.ua`,
«Authorized redirect URIs» — `https://novelka.in.ua/login/oauth2/code/google`; у «OAuth consent
screen» серед дозволених доменів — `novelka.in.ua`.

Змінюючи адресу ще раз: спершу Caddy має отримати сертифікат нової (тимчасово обслуговувати
обидві, `caddy reload`), і лише потім вмикати 301 — інакше за старими посиланнями кілька секунд
буде помилка TLS. Сесії й те, що гості зберегли в браузері (місце читання, вигляд), прив'язані
до адреси: після переїзду всі входять знову.

## Як іде деплой

Push у `main` → `.github/workflows/deploy-production.yml`. Коміт лише з документами (`doc/**`,
`*.md`) конвеєра не запускає.

1. Паралельно тести бекенду (з PostgreSQL через Testcontainers), фронтенду (`npm run check`)
   і збірка `novelka.jar` із сайтом усередині. Перевіряється лише змінене від останнього
   успішного деплою (`.github/scripts/changes.sh`), а не від попереднього push: зміна, чиї тести
   впали, на сервер не потрапила й перевіряється знову.
   - Лише `frontend/` — без тестів бекенду (у журналі «Лише інтерфейс: тести бекенду пропущено»).
   - Лише `backend/` — без `npm run check` («Лише бекенд: перевірки інтерфейсу пропущено»).
   - Решта — обидві перевірки: зміни в обох, `frontend/index.html` (його заповнює сервер для
     пошуковиків і превʼю, `SearchPages`), `backend/build.gradle` (збирає сайт у JAR), `build.gradle`,
     `settings.gradle`, `gradle/`, `infra/`, `.github/`, корінь, а також запуск вручну, перший
     деплой і коли порівняти не вдалося. Документи в рахунок не йдуть.
   - Збірка JAR перевіряє, що в зібраному `index.html` є `<title>Новелка</title>` і
     `<div id="root"></div>`, куди сервер вставляє заголовок і текст сторінки.
2. Лише коли збірка й потрібні перевірки зелені (непотрібні — пропущені), цей JAR копіюється на сервер, і `infra/deploy.sh` міняє версії
   без перерви: нова запускається поруч зі старою, а стара зупиняється, лише коли нова відповідає на
   `/api/health`. Перед зупинкою стара дочікується відповіді моделі, яка вже йде (до 180 с),
   і автопереклад продовжує нова. Якщо нова так і не запрацювала, її прибирають, а сайт
   лишається на старій. Потім перевірка `/api/health` і головної.

Після злиття pull request тести й збірка не повторюються. `pull-request.yml` збирає JAR
паралельно з тестами (з тим самим вибором перевірок: від останнього деплою до коду, злитого з
`main`) й, коли все потрібне зелене, зберігає його на 7 днів як `novelka-<хеш дерева>` — дерева
вже злитого з `main` коду. Деплой спершу рахує хеш дерева нового `main` і шукає такий артефакт
з успішної перевірки pull request з гілки самого репозиторію (не форку); знайшов — одразу викладає
його (у журналі «Готовий JAR з PR #N»). Повний прогін — коли `main` змінився після перевірки,
за прямого push, pull request з форку й запуску вручну.

Обидві версії якийсь час працюють з однією базою, тож міграція не має ламати попередню
версію: додавати колонки й таблиці можна, а прибирати чи перейменовувати — у два деплої
(спершу код перестає ними користуватися, потім міграція їх прибирає).

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
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH`, `MAIL_STARTTLS`, `MAIL_FROM` | листи: свій сервер `mail` або запасний Resend (див. «Пошта») |
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

## Пошта

Листи сайт надсилає сам: сервіс `mail` (Postfix з DKIM, образ `boky/postfix`) лише відправляє,
порту назовні не має й приймає листи тільки з мережі Compose. Пошту на домен ми не приймаємо.

У `.env.production` для свого сервера:

```
MAIL_HOST=mail
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_AUTH=false
MAIL_STARTTLS=false
MAIL_FROM=Новелка <no-reply@novelka.in.ua>
```

Запасний шлях — Resend: `MAIL_HOST=smtp.resend.com`, ключ у `MAIL_PASSWORD`, `MAIL_AUTH=true`,
`MAIL_STARTTLS=true`. Перемикання — зміна `.env.production` і `sh infra/deploy.sh`.

DNS домену `novelka.in.ua` (NIC.UA):

| Ім'я | Тип | Значення |
|---|---|---|
| `@` | TXT | `v=spf1 ip4:62.72.33.247 -all` |
| `novelka._domainkey` | TXT | публічний ключ DKIM (нижче, як його взяти) |
| `_dmarc` | TXT | `v=DMARC1; p=none; rua=mailto:<пошта власника>` → згодом `p=quarantine` |
| `@` | MX | `0 .` (пошту не приймаємо) |

Зворотний запис IP (PTR) у панелі Hostinger — `novelka.in.ua`; так само звуть себе й Postfix.

Ключ DKIM створюється сам при першому запуску й лежить у томі `novelka_dkim`; його публічна частина:

```
ssh novelka@62.72.33.247 'docker run --rm -v novelka_dkim:/k alpine cat /k/novelka.in.ua.txt'
```

Перевірка: лист на адресу з https://www.mail-tester.com (має бути 10/10) і на Gmail, Outlook,
ukr.net; журнал — `docker logs novelka-mail-1`.

## Моніторинг

`.github/workflows/uptime.yml` кожні 15 хвилин перевіряє `/api/health` і головну.
Якщо сайт не відповідає, запуск падає, і GitHub надсилає лист власнику репозиторію.
