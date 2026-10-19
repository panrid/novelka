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

Два рівні: копії на самому сервері (рятують від помилки чи поганої міграції) і зашифровані копії
в iCloud власника, які забирає його Mac (рятують від утрати сервера).

### На сервері

Сервіс `backup` (`infra/backup.sh`) щогодини перевіряє, чи є копія за сьогодні (UTC), і якщо
немає — робить її: база — `novelka-РРРР-ММ-ДД.dump` (14 днів), картинки — `media-РРРР-ММ-ДД.tgz`
(7 днів), у томі `novelka_backups`. Отже, копія з'являється невдовзі після півночі UTC, а невдала
повторюється щогодини.

Копія пишеться в `….part`, перевіряється й лише потім перейменовується. Копія бази добра, коли
`pg_dump` завершився без помилки, файл не менший за `BACKUP_DB_MIN_BYTES` (1 МБ; зараз ≈ 40 МБ) і
`pg_restore --list` його читає; картинок — коли `tar` завершився без помилки й `gzip -t` читає
архів до кінця. **Невдала копія не мовчить** (як порожній `.part` від 2026-10-16): `.part`
видаляється (і всі залишки `.part` — під час запуску сервісу), у журнал контейнера йде рядок
`BACKUP FAILED: <що саме>` (stderr, `docker logs novelka-backup-1`; Loki його бачить), а у файлах
стану — час невдачі й `novelka_backup_last_ok{…} 0`.

### Стан для Grafana (п. 5 етапу 23)

У томі `novelka_backups`, тека `status/` — файли в текстовому форматі Prometheus (для textfile
collector у node_exporter: змонтувати том `novelka_backups` лише для читання, наприклад у
`/backups`, і `--collector.textfile.directory=/backups/status`). Кожен файл замінюється цілком
(запис у `.tmp` і перейменування). Часи — Unix-секунди за годинником сервера; 0 — «ще не було».

| Файл | Метрика | Що означає |
|---|---|---|
| `server.prom` | `novelka_backup_last_success_timestamp_seconds{part="db"\|"media"}` | остання добра копія на сервері |
| | `novelka_backup_last_failure_timestamp_seconds{part=…}` | остання невдала |
| | `novelka_backup_last_ok{part=…}` | 1 — остання спроба вдалася, 0 — ні |
| | `novelka_backup_last_size_bytes{part=…}` | розмір останньої доброї |
| `offsite.prom` | `novelka_backup_offsite_last_success_timestamp_seconds{target="icloud"}` | **позначка Mac**: коли копія дійшла до iCloud |
| | `novelka_backup_offsite_last_size_bytes{target="icloud"}` | скільки байт (база + картинки) забрано |

Ті самі числа — окремими файлами в `status/state/` (`db.success`, `media.failure`, `icloud.success`
…; `icloud.dump` — ім'я останньої копії, що дійшла до iCloud). `offsite.prom` з'являється після
першої копії в iCloud. Подивитися: `docker exec novelka-backup-1 sh /backup.sh status`.

Тривоги (Telegram):

- копія на сервері не вдалася: `novelka_backup_last_ok == 0`;
- копії на сервері немає понад добу: `time() - novelka_backup_last_success_timestamp_seconds > 30*3600`;
- копії в iCloud немає понад 2 доби: `time() - novelka_backup_offsite_last_success_timestamp_seconds{target="icloud"} > 2*86400`,
  а також `absent(novelka_backup_offsite_last_success_timestamp_seconds)`, коли файла немає;
- запасна, з журналу (Loki): рядок `BACKUP FAILED` у контейнері `novelka-backup-1`.

### Копія в iCloud (Mac власника)

У iCloud немає входу для сервера, тож копію забирає Mac: задача `launchd`
(`infra/mac/ua.in.novelka.icloud-backup.plist`) щогодини, під час входу й після пробудження
запускає `infra/mac/novelka-icloud-backup.sh`. Скрипт робить щось лише тоді, коли на сервері є
копія бази, якої ще немає в iCloud, — тобто раз на добу:

1. через SSH бере список копій, найсвіжішу копію бази й картинки за той самий день (немає — найсвіжіші);
2. перевіряє: копія бази не старша за 50 год (інакше на сервері копії не робляться — помилка),
   не менша за 1 МБ, починається з `PGDMP`; довжина й SHA-256 обох файлів збігаються з серверними;
   `gzip -t` для картинок;
3. шифрує `age` для публічного ключа власника (приватного на Mac немає) і кладе в
   `iCloud Drive/Novelka/копії` як `novelka-РРРР-ММ-ДД.dump.age` і `media-РРРР-ММ-ДД.tgz.age`
   (через прихований `.….partial` і перейменування); незашифровані файли — лише в тимчасовій теці;
4. ставить позначку на сервері (`mark-offsite`, див. вище); не вдалося — поставить наступного запуску;
5. видаляє копії, старші за 30 днів (за датою в імені, зокрема вивантажені з Mac `.….icloud`).

Журнал — `~/Library/Logs/novelka-icloud-backup.log`. Помилку видно там; якщо доброї копії немає
понад 26 год — ще й сповіщення macOS; через 2 доби — тривога Grafana.

**Ключ SSH лише для копій.** Ключ Mac на сервері може тільки те, що дозволяє
`infra/backup-export.sh`: `list`, `get <копія>`, `sha256 <копія>`, `mark-offsite icloud …`,
`status`. Ні оболонки, ні переадресацій, ні запису поза файлами стану. Рядок у
`~novelka/.ssh/authorized_keys` на сервері (одним рядком):

```
restrict,command="sh /opt/novelka/infra/backup-export.sh" ssh-ed25519 AAAA… novelka-icloud-backup
```

`infra/backup-export.sh` і `infra/backup.sh` копіює на сервер деплой.

**Встановлення** (раз, на Mac власника):

1. `brew install age`.
2. Ключ шифрування: `age-keygen -o novelka-backup-key.txt`. Рядок `Public key: age1…` — у
   налаштування (крок 5). Сам файл — **приватний ключ**: зберегти в менеджері паролів і/або
   надрукувати, з Mac і з iCloud Drive видалити. Без нього копії не розшифрувати.
3. Ключ SSH: `ssh-keygen -t ed25519 -N '' -C novelka-icloud-backup -f ~/.ssh/novelka_icloud_backup`;
   вміст `~/.ssh/novelka_icloud_backup.pub` дописати на сервері в `~novelka/.ssh/authorized_keys`
   рядком, як вище. Ключ сервера — у `known_hosts`: один раз
   `ssh -i ~/.ssh/novelka_icloud_backup novelka@62.72.33.247 list` і «yes»; у відповідь має прийти
   список копій, а не оболонка.
4. З теки репозиторію: `bash infra/mac/install.sh` — скрипт у `~/Library/Application Support/novelka-backup/`,
   задача в `~/Library/LaunchAgents/`, налаштування `~/.config/novelka-backup/config` (з `infra/mac/config.example`).
5. У `~/.config/novelka-backup/config` вписати `AGE_RECIPIENT=age1…`.
6. Перший запуск: `launchctl kickstart -p gui/$(id -u)/ua.in.novelka.icloud-backup`, потім
   `tail ~/Library/Logs/novelka-icloud-backup.log` — має бути «готово: …», а в Finder — файли в
   iCloud Drive → Novelka → копії. На сервері: `docker exec novelka-backup-1 sh /backup.sh status`.
   Якщо в журналі `Operation not permitted` для iCloud Drive — System Settings → Privacy & Security
   → Full Disk Access → додати `/bin/bash` і запустити знову.

Оновлення скрипта — знову `bash infra/mac/install.sh` (налаштування не чіпає). Видалення —
`bash infra/mac/uninstall.sh` (налаштування й копії в iCloud лишаються), а на сервері — прибрати
рядок з `authorized_keys`. Mac має бути ввімкнений і з інтернетом хоча б раз на 2 доби, інакше
прийде тривога — так задумано.

### Відновлення

**Розшифрувати копію з iCloud** (на Mac, з приватним ключем):

```sh
cd ~/Desktop
age -d -i novelka-backup-key.txt -o novelka.dump \
    ~/Library/Mobile\ Documents/com~apple~CloudDocs/Novelka/копії/novelka-РРРР-ММ-ДД.dump.age
age -d -i novelka-backup-key.txt -o media.tgz \
    ~/Library/Mobile\ Documents/com~apple~CloudDocs/Novelka/копії/media-РРРР-ММ-ДД.tgz.age
```

Файл, вивантажений з Mac (у Finder — хмаринка), спершу завантажити: відкрити теку в Finder або
`brctl download <файл>`. Залити на сервер звичайним ключем власника (не ключем копій):
`scp novelka.dump media.tgz novelka@62.72.33.247:/tmp/`. Новий сервер спершу піднімається звичайним
деплоєм (порожня база створиться сама), потім — відновлення, як нижче.

**Копія з самого сервера** — у томі `novelka_backups`:

```sh
ssh novelka@62.72.33.247 'docker run --rm -v novelka_backups:/b alpine ls -l /b'
ssh novelka@62.72.33.247 'docker run --rm -v novelka_backups:/b alpine cat /b/novelka-РРРР-ММ-ДД.dump' > novelka.dump
```

**Відновлення бази** (на сервері; сайт на цей час зупиняється):

```sh
cd /opt/novelka
docker compose --env-file .env.production -f infra/production.compose.yaml stop app
docker compose --env-file .env.production -f infra/production.compose.yaml exec -T postgres \
    pg_restore -U novelka -d novelka --clean --if-exists < /tmp/novelka.dump
docker compose --env-file .env.production -f infra/production.compose.yaml start app
rm /tmp/novelka.dump
```

**Картинки:** з тому копій — `docker run --rm -v novelka_media:/m -v novelka_backups:/b alpine tar xzf /b/media-РРРР-ММ-ДД.tgz -C /m`;
з розшифрованого файла — `docker run --rm -i -v novelka_media:/m alpine tar xzf - -C /m < /tmp/media.tgz`.

Час від часу (скажімо, раз на кілька місяців) варто розшифрувати свіжу копію з iCloud і перевірити
її: `pg_restore --list novelka.dump | head` — так видно, що і ключ, і копії справні.

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
