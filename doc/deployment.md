# Деплой

Сервер — VPS `62.72.33.247`. На ньому два сайти: робочий `https://novelka.in.ua` і тестовий
`https://dev.novelka.in.ua` (див. «Тестовий сайт»). Compose-проєкти:

- `novelka` — робочий: PostgreSQL, застосунок `app`, пошта `mail`, копії `backup` і Caddy (HTTPS,
  сертифікати сам). PostgreSQL, Caddy й мережа `novelka_default` — спільні з тестовим.
- `novelka-dev` — тестовий: застосунок `dev-app` і ловець листів `dev-mail`.

У `/opt/novelka` на сервері:

| Що | Для чого |
|---|---|
| `.env.production`, `.env.dev` | секрети робочого й тестового сайту, лише на сервері |
| `builds/<коміт>/` | збірка: `app/` (JAR і Dockerfile) й `infra/` (Compose-файли й скрипти того самого коміту) |
| `infra/`, `infra-dev/` | файли, з якими зараз працює робочий і тестовий сайт (копія з `builds/<коміт>/infra`) |
| `prod.history`, `dev.history` | які коміти й коли викладено на кожен сайт (останній рядок — що працює зараз) |
| `dev-access/` | хто може зайти на тестовий сайт |

Образ `novelka:<коміт>` збирається з JAR один раз; сайти запускають його під мітками
`novelka:prod` і `novelka:dev`. Сервер тримає 10 найновіших збірок і по 5 останніх, викладених на
кожен сайт.

## Адреси

DNS (у реєстратора `novelka.in.ua`): A-записи `@`, `www`, `dev` → `62.72.33.247`. Caddy
(`infra/Caddyfile`):

| Адреса | Що робить |
|---|---|
| `novelka.in.ua` | сайт; Студія — `/studio` |
| `www.novelka.in.ua`, `novelka.panrid.space` | 301 на `https://novelka.in.ua` з тим самим шляхом і параметрами |
| `dev.novelka.in.ua` | тестовий сайт: лише команді (пароль), `noindex`; `/_mail/` — його листи; поки він не працює — 503 «Тестовий сайт Новелки зараз не працює» |

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

Push у `main` → `.github/workflows/deploy.yml`. Коміт лише з документами (`doc/**`,
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
2. Лише коли збірка й потрібні перевірки зелені (непотрібні — пропущені), цей JAR разом із
   `infra/` того самого коміту копіюється на сервер у `builds/<коміт>/`. Спершу збірка йде на
   тестовий сайт (`deploy.sh dev`, перевірка `https://dev.novelka.in.ua/api/health`), потім той
   самий образ — на робочий (`deploy.sh prod`). Доки тестовий сайт не налаштовано (змінна
   `DEV_SITE` не `on`, див. «Тестовий сайт»), крок тестового пропускається. `infra/deploy.sh` міняє версії
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
Змінна (Settings → Secrets and variables → Actions → Variables): `DEV_SITE` = `on` вмикає
тестовий сайт.

Перший деплой зі збірками в `builds/` (#185) сам переходить зі старого способу: образ більше не
збирається з `/opt/novelka/app`, Caddy один раз перезапускається (з'являється каталог
`dev-access`). Після нього старе можна прибрати: `rm -rf /opt/novelka/app` і
`docker image rm novelka-app`.

## Тестовий сайт

`https://dev.novelka.in.ua` — те саме, що робочий, але для команди: тут нова версія працює першою
(і першими проходять її міграції), тут пробуємо.

- **Окремо від робочого.** Свій Compose-проєкт `novelka-dev` (`infra/dev.compose.yaml`), своя база
  `novelka_dev` і свій користувач `novelka_dev` у тому самому PostgreSQL, свої картинки (том
  `novelka-dev_media`), свої секрети (`/opt/novelka/.env.dev`). Caddy один на обидва сайти.
- **Обмеження:** 1 ГБ пам'яті й пів ядра, щоб робочий сайт його не відчував.
- **Лише для команди:** Caddy питає ім'я й пароль (крім `/api/health`, вебхука Telegram і
  `robots.txt`); заголовок `X-Robots-Tag: noindex, nofollow` і `robots.txt` з `Disallow: /`.
- **Листи нікуди не йдуть:** усі листи тестового сайту ловить Mailpit — їх читають на
  `https://dev.novelka.in.ua/_mail/`.
- **ШІ** — окремий ключ OpenRouter з малим лімітом; **Telegram** — окремий бот (бот робочого
  сайту тут не можна: тестовий сайт перехопив би його вебхук).
- **Дані** — спершу порожня база з власником із `NOVELKA_OWNER_*`; приклад чи знеособлена копія
  робочої бази — окремою задачею (етап 22, пункт 7).

Без `.env.dev` чи без файлу в `dev-access/` `deploy.sh dev` відмовляється викладати тестовий сайт —
краще ніякого, ніж відкритого всім.

### Як увімкнути (один раз)

1. DNS: A-запис `dev` → `62.72.33.247` уже є; перевірити — `dig +short dev.novelka.in.ua`.
2. Зачекати, поки цей перехід викладеться на робочий (перший деплой після злиття, див. вище).
3. База й користувач на сервері (пароль — новий, не від робочої бази):

   ```sh
   ssh novelka@62.72.33.247
   docker exec -i novelka-postgres-1 psql -U novelka -d novelka \
       -c "CREATE ROLE novelka_dev LOGIN PASSWORD '<пароль тестової бази>'" \
       -c "CREATE DATABASE novelka_dev OWNER novelka_dev" \
       -c "REVOKE CONNECT ON DATABASE novelka FROM PUBLIC"
   ```

   Останній рядок не пускає `novelka_dev` (і будь-кого, крім власника бази) у робочу базу.
4. `/opt/novelka/.env.dev` (`chmod 600`), назви — як у `.env.production`:

   | Змінна | Що |
   |---|---|
   | `NOVELKA_DB_PASSWORD` | пароль `novelka_dev` з кроку 3 |
   | `OPENROUTER_API_KEY` | окремий ключ OpenRouter з лімітом (OpenRouter → Keys → Credit limit, напр. $2) |
   | `OPENROUTER_MANAGEMENT_KEY` | порожньо (або окремий) |
   | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME` | окремий бот з @BotFather (напр. «Новелка · тест») |
   | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | за бажання; тоді в OAuth-клієнті Google додати origin `https://dev.novelka.in.ua` і redirect `https://dev.novelka.in.ua/login/oauth2/code/google` |
   | `NOVELKA_OWNER_NICK`, `NOVELKA_OWNER_EMAIL`, `NOVELKA_OWNER_PASSWORD` | власник тестового сайту при першому запуску; після — прибрати |

   Пошту тестового сайту задавати не треба: вона завжди йде в Mailpit.
5. Хто заходить: хеш пароля —
   `docker run --rm caddy:2.10-alpine caddy hash-password --plaintext '<пароль>'`; файл
   `/opt/novelka/dev-access/team.caddy` (по рядку на людину):

   ```
   basic_auth @team {
       olek $2a$14$...
   }
   ```

   Потім `docker exec novelka-caddy-1 caddy reload --config /etc/caddy/Caddyfile`. Так само —
   щоб додати чи прибрати когось.
6. GitHub → Settings → Secrets and variables → Actions → Variables: `DEV_SITE` = `on`. Далі
   Actions → Deploy → Run workflow (або наступний push).

Вимкнути: `DEV_SITE` — будь-що, крім `on`, і на сервері
`docker compose -p novelka-dev --env-file /opt/novelka/.env.dev -f /opt/novelka/infra-dev/dev.compose.yaml down`.
Журнал: `docker logs novelka-dev-dev-app-1`.

Зміни `Caddyfile` (і для тестового сайту теж) застосовуються, коли викладається робочий сайт:
Caddy належить йому.

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

`.github/workflows/uptime.yml` кожні 5 хвилин (поза сервером) перевіряє `/api/health` (застосунок
і база) і головну; логіка — `.github/scripts/uptime.sh`. Кожен запит — з повторами (3 × по 20 с),
тож одиничний збій мережі тривоги не дає.

**Тривога в Telegram — лише при зміні стану.** Сайт перестав відповідати — одне повідомлення
(«🔴 Новелка не відповідає з 14:05 (Київ): /api/health — 502 (застосунок за проксі не відповідає)»
і посилання на запуск); поки лежить — тиша; піднявся — одне повідомлення («🟢 Новелка знову
працює, не відповідала ≈ 25 хв»). Час «з 14:05» — коли перевірка помітила, сайт міг лягти до 5 хв
раніше.

- **Запуск лишається зеленим, коли сайт лежить** — GitHub не шле лист щоп'ять хвилин. Стан сайту
  видно в підсумку запуску й у попередженні («Сайт не відповідає: …»). Червоний запуск означає, що
  зламалась сама перевірка: не прочитався збережений стан або Telegram не прийняв повідомлення
  (наприклад, немає секретів чи боту не натиснули «Start») — тоді лист від GitHub і є запасною
  тривогою.
- **Стан між запусками** — найновіший артефакт `uptime-state` (рядки `status=up|down`, `since=` —
  час переходу, `reason=`), живе 90 днів. Новий артефакт з'являється лише при зміні стану й лише
  після того, як Telegram відповів `ok`; не вдалося надіслати — стан старий, наступний запуск
  спробує ще раз. Артефакту немає (перший запуск чи 90 днів без змін) — вважається, що сайт
  працював. Запуски йдуть по одному в черзі (`concurrency: uptime`): пропущені, скасовані в черзі
  й ручні стан не ламають. Якщо запуск скасувати точно між надсиланням і збереженням, повідомлення
  прийде двічі — краще так, ніж жодного.
- **Бот** — окремий від бота сайту: `@novelka_watch_bot` («Новелка · нагляд»), лише `sendMessage`.
  Бот сайту тут не потрібен: у нього вебхук на сервері, а `getUpdates`, `deleteWebhook` чи
  `setWebhook` зламали б сповіщення на сайті.
- **Секрети** (GitHub → Settings → Secrets and variables → Actions):
  `UPTIME_TELEGRAM_TOKEN` — токен бота з @BotFather; `UPTIME_TELEGRAM_CHAT_ID` — id власника
  (з @userinfobot). Власник має один раз натиснути боту «Start», інакше бот не може йому писати.

**Як перевірити тривогу.** Actions → Uptime → Run workflow, у полі адреси — недоступна адреса
(наприклад, `https://127.0.0.1:9`); повідомлення з такою адресою позначені «🧪 Перевірка тривоги»,
а стан спільний зі справжнім. Перший такий запуск — одне «не відповідає», другий — тиша, потім
запуск із порожнім полем — одне «знову працює». Запускати одне за одним: якщо між ними встигне
запуск за розкладом, прийде зайва пара повідомлень. Локально, без надсилання:

```
STATE=/tmp/uptime-state DRY_RUN=1 ADDRESS=https://127.0.0.1:9 bash .github/scripts/uptime.sh
```

GitHub може затримувати чи пропускати запуски за розкладом, коли перевантажений, — тоді тривога
приходить на кілька хвилин пізніше.
