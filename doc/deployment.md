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

## Метрики, журнали й тривоги

План, етап 23, п. 5. Усе на нашому сервері, окремим Compose-проєктом `novelka-monitoring`
(`infra/monitoring/compose.yaml`): деплой сайту його не чіпає (`--remove-orphans` діє лише в
межах проєкту `novelka`), а він не чіпає сайт.

| Що | Для чого | Пам'ять (межа) |
|---|---|---|
| Prometheus | метрики, 15 днів (і не більше 3 ГБ) | 400 МБ |
| Loki + Alloy | журнали всіх контейнерів, 15 днів | 400 + 200 МБ |
| Grafana | панелі «Сайт», «Автопереклад», «Сервер» і тривоги в Telegram | 300 МБ |
| node-exporter | процесор, пам'ять, диск, мережа; файли стану копій | 64 МБ |
| postgres-exporter | PostgreSQL | 64 МБ |

**Звідки метрики.** Застосунок віддає `/actuator/prometheus` на окремому порту **8081**
(Spring Boot Actuator + Micrometer). Caddy пересилає лише 8080, тож ззовні метрик не видно;
Prometheus читає `app:8081` через мережу `novelka_default` (обидві копії під час деплою). Свої
метрики: `novelka_autotranslate_jobs{state}`, `novelka_autotranslate_steps{state="ready|waiting|running"}`,
`novelka_autotranslate_chapters_done_last_hour`, `novelka_autotranslate_idle_seconds` (скільки нічого не
рухається, коли є робота), `novelka_ai_spent_today_usd` (з півночі за Києвом; загублені відповіді — за
оцінкою), `novelka_ai_calls_last_hour{state}`, `novelka_mail_sent_total{result}`. Вони читаються з бази
в момент запиту Prometheus, тому однакові в обох копіях і не губляться при перезапуску. Копії бази —
файли стану `status/*.prom` у томі `novelka_backups` (див. «Резервні копії»), node-exporter читає їх
лише для читання; поки їх немає, тривоги копій мовчать.

**Журнали.** Alloy читає журнали контейнерів через Docker (сокет лише для читання) і шле в Loki з
мітками `compose_project`, `compose_service`, `container`; у рядків застосунку ще `level`, а стек
помилки лишається з її рядком. Помилки з браузерів — рядки `client-errors`.

**Grafana лише власнику.** Слухає тільки `127.0.0.1:3000` на сервері, назовні порту немає.
Відкрити:

```sh
ssh -N -L 3000:127.0.0.1:3000 novelka@62.72.33.247
```

і http://localhost:3000, вхід `admin` з паролем з `.env.monitoring`. Панелі — у теці «Новелка»;
змінювати їх треба в `infra/monitoring/grafana/dashboards/*.json` (зміни в самій Grafana наступний
деплой скасує).

**Тривоги** (`infra/monitoring/grafana/provisioning/alerting/rules.yaml`) — в Telegram тим самим
ботом `@novelka_watch_bot`, що й перевірка доступності: «🔴 …» коли почалось, «🟢 Минуло: …» коли
скінчилось, поки триває — нагадування раз на 4 год.

| Тривога | Коли |
|---|---|
| Помилки 5xx | понад 5 відповідей 5xx за 5 хв |
| Повільні відповіді | 95 % відповідей довші за 3 с протягом 10 хв (без `/api/events`, від 20 запитів) |
| Немає метрик застосунку | Prometheus 5 хв не може прочитати `app:8081` |
| Помилки сервера в журналі | хоч один рядок `ERROR` застосунку за 5 хв |
| Сплеск помилок у браузерах | понад 30 рядків `client-errors` за 15 хв |
| Автопереклад стоїть | глави чекають чи йдуть, а нічого не рухається понад 30 хв |
| Витрати на ШІ за день | понад `ALERT_AI_USD_PER_DAY` (за замовчуванням 5 USD) з півночі за Києвом |
| Диск заповнений | понад 80 % протягом 10 хв |
| Резервна копія не вдалася | `novelka_backup_last_ok == 0` |
| Резервна копія не зробилась | остання добра копія бази чи картинок старша за 30 год |
| Копії немає в iCloud | остання копія на Mac старша за 2 доби, або її ще не було жодного разу |
| BACKUP FAILED у журналі | такий рядок у журналі контейнера `backup` за 10 хв |

Сам сервер не скаже, що впав, — це лишається за перевіркою доступності з GitHub Actions
(«Моніторинг»).

### Увімкнути (один раз, власник)

Поки на сервері немає `/opt/novelka/.env.monitoring`, деплой пропускає цей крок (у журналі
деплою «Monitoring: … skipped») і працює як раніше.

1. На сервері створити `/opt/novelka/.env.monitoring` (права `600`, лише на сервері):

   ```
   GRAFANA_ADMIN_PASSWORD=<довгий випадковий пароль>
   WATCH_TELEGRAM_TOKEN=<той самий токен, що UPTIME_TELEGRAM_TOKEN у секретах GitHub>
   WATCH_TELEGRAM_CHAT_ID=<той самий id, що UPTIME_TELEGRAM_CHAT_ID>
   ALERT_AI_USD_PER_DAY=5
   ```

2. Запустити деплой (Actions → Deploy production → Run workflow) або на сервері
   `sh /opt/novelka/infra/monitoring/up.sh`. Перевірка: `docker compose -p novelka-monitoring ps` —
   шість контейнерів `running`; у Grafana (тунель вище) в Connections → Data sources обидва
   джерела «working», на панелі «Сайт» є запити.
3. Перевірити Telegram: Alerting → Contact points → Telegram → Test — має прийти повідомлення.

Команди на сервері — з `/opt/novelka`, з `m="docker compose --env-file .env.production --env-file
.env.monitoring -f infra/monitoring/compose.yaml"`. Пароль `GRAFANA_ADMIN_PASSWORD` діє лише при
першому запуску Grafana; змінити пізніше: `$m exec grafana grafana cli admin reset-admin-password <новий>`.
Пороги тривог — числа в `rules.yaml` (крім витрат на ШІ — у `.env.monitoring`), після зміни —
деплой. Вимкнути все: `$m down` і прибрати `.env.monitoring`
(дані лишаються в томах `novelka-monitoring_*`).
