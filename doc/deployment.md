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

Рішення власника 2026-10-19, #192: **Google Drive із сервера замість iCloud із Mac**.
Ноутбук не бере участі в добовому копіюванні. Серверні копії лишаються в
`novelka_backups`: `novelka-РРРР-ММ-ДД.dump` (PostgreSQL custom format, 14 днів),
`media-РРРР-ММ-ДД.tgz` (7 днів). Приватний ключ розшифрування на сервер не передається.

### Як працює

`infra/backup.sh` у сервісі `backup` перевіряє дату UTC щогодини, а також відразу після
перезапуску. Нову пару створює раз на добу. Спершу пише в прихований тимчасовий каталог,
перевіряє коди `pg_dump`/`tar`, непорожність, `pg_restore --list` та `gzip -t`.
Останнім публікує `novelka-РРРР-ММ-ДД.sha256` з контрольними сумами **обох** файлів.
Лише цей manifest означає готову пару. Помилка дає `BACKUP FAILED`, ненульовий код
режиму `once` і метрику невдачі; цикл повторює спробу через годину. Старі копії після
невдачі не видаляються. Уже опублікована пошкоджена пара потребує розслідування,
її не замінюємо мовчки. Старі `.part` не вважаються копіями й не експортуються.

Окремий Compose-проєкт `novelka-offsite` (`infra/offsite/compose.yaml`) має власний цикл
раз на годину, негайну перевірку після перезапуску та системне блокування `flock`.
Він читає `novelka_backups` **лише для читання**, без доступу до живої бази, Docker socket
чи секретів застосунку. Бере найновішу завершену пару однієї дати, не старшу 48 годин
від початку її доби UTC. Пропущені дні не відтворює: зберігає свіжу доступну пару.

Формат у `Google Drive/Novelka/копії` — `novelka-drive-v1-РРРР-ММ-ДД.tar.age`:
всередині `novelka-РРРР-ММ-ДД.dump`, `media-РРРР-ММ-ДД.tgz`, `SHA256SUMS`.
Незашифровані тимчасові файли — лише в томі `novelka-offsite_work`; прибираються
після пакування, а залишені аварійним завершенням — перед наступною спробою.
Додаткове стиснення всього tar не потрібне: dump і media вже стиснуті.

У Drive спершу потрапляє **зашифрований** `.tar.age.upload`. Перевіряються розмір і
MD5 від Drive, потім файл завантажується назад і перевіряється SHA-256. Після перейменування
остаточний файл також завантажується й перевіряється. Це підтвердження фактичного файла
в хмарі, а не запису в локальну синхронізовану папку. Журнал `pending.json` зберігає
контрольні суми шифротексту до завантаження: обрив після upload/rename не створює дубль.
Повторна перевірка вже збереженого дня звіряє віддалені розмір і MD5 та не оновлює час
первісного підтвердження. При помилці завантаження зашифрований файл лишається для повтору.

Після успіху видаляються лише файли цього формату старше 30 днів, для яких є локальна
квитанція успіху й збігаються віддалені розмір/MD5. Видалення остаточне, щоб корзина
Drive не займала квоту; чужі файли й корзина не очищуються. Втрата тому стану не дає права
видаляти чи перезаписувати наявні хмарні архіви — спершу відновити журнал або вручну
перевірити архіви. Розбіжності/дублікати імен зупиняють операцію.

### Перше встановлення — лише після дозволу власника

На сервері вже потрібні Docker і Compose. `age`, `rclone`, Python та CA-сертифікати
встановлюються **всередині** образу worker. На Mac для первісної авторизації та контрольного
відновлення (без запуску постійного сервісу PostgreSQL):

```sh
brew install age rclone postgresql@17
install -d -m 700 "$HOME/.config/novelka-backup"
umask 077
age-keygen -o "$HOME/.config/novelka-backup/identity.txt"
age-keygen -y "$HOME/.config/novelka-backup/identity.txt" > "$HOME/.config/novelka-backup/recipient.txt"
```

`identity.txt` — **приватний ключ**, режим `600`. Зберегти запасний примірник у менеджері
паролів із доступом після втрати Mac або на окремому захищеному носії; перевірити
розшифрування саме запасним примірником. Не додавати ключ до Git, Google Drive, архіву
копій чи повідомлення в чаті. `recipient.txt` — публічний ключ для сервера.

Google Drive: створити **окремий** проєкт Google Cloud, увімкнути Drive API та OAuth client
типу Desktop app (не змінювати Google-вхід сайту). Для особистого облікового запису —
OAuth користувача, не service account. Consent screen перевести з Testing у Production,
інакше тестова авторизація спливатиме за сім днів. Власний client ID потрібен, оскільки
спільний клієнт rclone виводиться з використання у 2026 році.
[Офіційна інструкція](https://rclone.org/drive/#making-your-own-client-id).

```sh
rclone --config "$HOME/.config/novelka-backup/rclone.conf" config
```

Створити remote `gdrive`, тип `drive`, власні client ID/secret, scope **`drive.file`**,
звичайний My Drive. Дозволити доступ у браузері. Цей scope дозволяє працювати лише з
файлами й папками, створеними цим OAuth-застосунком. Папку `Novelka/копії` створить worker;
якщо така папка вже є від іншого застосунку, обрати окрему назву й змінити `DRIVE_ROOT`.
Не використовувати повний scope `drive` без окремого погодження.
[Права](https://rclone.org/drive/#scopes), [авторизація сервера](https://rclone.org/remote_setup/).
Пароль Google на сервер не передається, але OAuth refresh token дає доступ до копій,
включно з видаленням. `rclone.conf` — секрет, поза Git і резервними архівами.

Після погодження, із worktree #192 на Mac:

```sh
ssh novelka@62.72.33.247 'install -d -m 700 /opt/novelka/secrets/drive-backup /opt/novelka/config/drive-backup /opt/novelka/infra/offsite'
scp infra/offsite/Dockerfile infra/offsite/compose.yaml infra/offsite/backup.py novelka@62.72.33.247:/opt/novelka/infra/offsite/
scp "$HOME/.config/novelka-backup/rclone.conf" novelka@62.72.33.247:/opt/novelka/secrets/drive-backup/rclone.conf
scp "$HOME/.config/novelka-backup/recipient.txt" novelka@62.72.33.247:/opt/novelka/config/drive-backup/recipient.txt
ssh novelka@62.72.33.247 'chmod 600 /opt/novelka/secrets/drive-backup/rclone.conf /opt/novelka/config/drive-backup/recipient.txt'
```

`backup.sh` і оновлений production Compose доставляє звичайний деплой після злиття.
Перед першим запуском перевірити, що сервіс backup уже використовує `/backup.sh`.
Перший запуск нової версії створює й перевіряє пару з manifest, навіть якщо за цю дату
лишилися файли старої версії. Приватний ключ серед файлів для `scp` **відсутній**.

На сервері після деплою та погодження:

```sh
cd /opt/novelka
docker compose --env-file .env.production -f infra/production.compose.yaml exec -T backup bash /backup.sh once
# Цикл уже тримає блокування; для готовності перевірити manifest, а не лише код команди:
docker compose --env-file .env.production -f infra/production.compose.yaml exec -T backup sh -c 'test -s "/backups/novelka-$(date -u +%F).sha256"'
docker compose -f infra/offsite/compose.yaml build
docker compose -f infra/offsite/compose.yaml run --rm drive-backup once
docker compose -f infra/offsite/compose.yaml up -d
```

Каталог credentials монтується для запису: rclone оновлює OAuth-токен атомарною заміною
конфігурації. Каталог із публічним ключем — лише для читання. У worker немає порту назовні.
Не запускати другу інсталяцію worker з іншим томом state для тієї самої папки Drive.

### Ручний запуск, журнали й видалення

На сервері:

```sh
cd /opt/novelka
docker compose -f infra/offsite/compose.yaml exec -T drive-backup python3 /app/backup.py once
docker compose -f infra/offsite/compose.yaml logs --tail 100 drive-backup
docker compose --env-file .env.production -f infra/production.compose.yaml logs --tail 100 backup
docker compose -f infra/offsite/compose.yaml exec -T drive-backup cat /state/drive.prom
```

Журнали worker — Docker json-file, максимум 3 × 10 МБ. `once` повертає помилку при
невдачі; при зайнятому блокуванні пише, що запуск пропущено. У постійному циклі помилка
пише метрику `0` й повторюється через годину. Після вимкнення сервера перевірка відбувається
відразу після запуску контейнера; залежності від сну Mac немає.

Стан: том `novelka-offsite_state` (`receipts/*.json`, `pending.json`, `last-success.json`,
`drive.prom`); тимчасові дані: `novelka-offsite_work`. Не видаляти state під час оновлення.
Оновлення worker: `docker compose -f infra/offsite/compose.yaml up -d --build`.

Видалення автоматичного копіювання:

```sh
cd /opt/novelka
docker compose -f infra/offsite/compose.yaml down
```

Без `-v`: стан і копії зберігаються. Окремо відкликати OAuth-доступ у Google Account,
видалити серверний `rclone.conf`, якщо копіювання більше не потрібне. Приватний ключ
зберігати, доки потрібні архіви. launchd не встановлюється, змін SSH-доступів немає.

### Відновлення на чистій машині

Потрібні приватний age-ключ із запасного сховища, доступ до Google Drive, `age`,
PostgreSQL **17 або новіший**. Один архів можна завантажити через браузер — rclone і
старий OAuth-токен для відновлення не обов'язкові. Розшифрований dump містить приватні
дані: робоча папка має бути поза хмарними каталогами з правами `700`.

Приклад на Mac, змінити лише `archive` на завантажений файл:

```sh
umask 077
archive="$HOME/Downloads/novelka-drive-v1-РРРР-ММ-ДД.tar.age"
restore_dir=$(mktemp -d /private/tmp/novelka-restore.XXXXXX)
age -d -i "$HOME/.config/novelka-backup/identity.txt" -o "$restore_dir/pair.tar" "$archive"
tar -tf "$restore_dir/pair.tar"  # лише dump, media та SHA256SUMS, без абсолютних шляхів
tar -xf "$restore_dir/pair.tar" -C "$restore_dir"
(cd "$restore_dir" && shasum -a 256 -c SHA256SUMS)
pg_bin="$(brew --prefix postgresql@17)/bin"
"$pg_bin/initdb" -D "$restore_dir/pgdata" -U restore_owner -A trust > "$restore_dir/initdb.log"
# Лише приватний Unix socket; жодного TCP-порту чи робочої бази.
"$pg_bin/pg_ctl" -D "$restore_dir/pgdata" -l "$restore_dir/postgres.log" \
  -o "-k $restore_dir -c listen_addresses=''" -w start
"$pg_bin/createdb" -h "$restore_dir" -U restore_owner novelka_restore
"$pg_bin/pg_restore" -h "$restore_dir" -U restore_owner -d novelka_restore \
  --exit-on-error --no-owner --no-privileges "$restore_dir"/novelka-*.dump
"$pg_bin/psql" -h "$restore_dir" -U restore_owner -d novelka_restore -v ON_ERROR_STOP=1 \
  -c "SELECT count(*) AS tables FROM information_schema.tables WHERE table_schema='public';"
mkdir "$restore_dir/media"
tar -xzf "$restore_dir"/media-*.tgz -C "$restore_dir/media"
"$pg_bin/pg_ctl" -D "$restore_dir/pgdata" -m fast -w stop
```

Додатково перевірити кількість рядків ключових таблиць, читабельність картинок та їхні
контрольні суми проти вмісту архіву; не публікувати персональні дані. Після перевірки
прибрати **саме створений** `restore_dir` і завантажений файл за рішенням власника.
Не виконувати ці команди проти production. При збої відновлення все одно зупинити
тимчасовий PostgreSQL командою `pg_ctl` вище.

### Метрики й тривоги (#188)

Серверна пара: `novelka_backups/status/server.prom`:
`novelka_backup_last_attempt_ok`, `novelka_backup_last_attempt_timestamp_seconds`,
`novelka_backup_last_success_timestamp_seconds`.
Google Drive: `novelka-offsite_state/drive.prom`:
`novelka_backup_drive_last_attempt_ok`, `novelka_backup_drive_last_attempt_timestamp_seconds`,
`novelka_backup_drive_last_verified_timestamp_seconds`, `novelka_backup_drive_source_timestamp_seconds`.
Остання метрика — дата **даних**, щоб повторне завантаження старої пари не приховувало застій.

У #188 підключити ці `.prom` до textfile collector із доступом читання. Файли замінюються
атомарно; `.tmp` не читати. Зараз вони закриті правами `600`, тому при встановленні
експортера явно надати йому доступ через користувача/групу або підготовлений збирач;
не робити весь том з базою загальнодоступним. Тривоги: latest attempt = 0; відсутня метрика;
`time() - ...last_verified_timestamp_seconds > 172800`; застаріла дата даних; зупинений
worker (вік last_attempt), аналогічно для серверної пари.

Telegram — бот #189 `@novelka_watch_bot`, окремі правила для backup: одне повідомлення
про перехід у збій, одне про відновлення, без щогодинних повторів. Налаштування контактної
точки й усунення повторів — #188. **До #188 автоматичних Telegram-повідомлень про backup
немає**, є явні журнали й метрики. `uptime.yml` не змінюється, його перевірка доступності
не є перевіркою резервних копій.

### Перевірки коду та межі готовності

```sh
shellcheck infra/backup.sh infra/tests/integration-backup.sh
python3 -m unittest discover -s infra/tests -v
bash infra/tests/integration-backup.sh  # потрібен Docker; лише одноразові тестові дані
```

Unit-тести підміняють Google Drive/age та помилки pg_dump/tar. На macOS shell-тест
`flock` пропускається (Python-блокування перевіряється реально); Linux CI перевіряє і його.
Інтеграційний тест запускає справжні PostgreSQL 17, age і rclone з **локальним** сховищем,
розшифровує й відновлює тестову базу. Це не перевірка OAuth/Google Drive чи production-даних.

Для закриття #192 окремо потрібні погоджене встановлення, справжній архів у Google Drive,
зворотне завантаження, розшифрування приватним ключем власника й локальне відновлення
справжньої бази/картинок. Наступний добовий запуск та тривоги #188 звітуються окремо.

## Пошта

Листи сайт надсилає сам, від `Новелка <no-reply@novelka.in.ua>` (з 2026-10-18; до того — через
Resend). Шлях листа: застосунок → сервіс `mail` (Postfix з OpenDKIM, образ `boky/postfix`) на
порту 587 усередині мережі Compose, без пароля → Postfix підписує лист ключем DKIM і сам
доставляє його на поштовий сервер одержувача (порт 25, TLS, коли той його має; лише IPv4).
Назовні в `mail` порту немає, листи він приймає тільки з приватних мереж (Compose). Пошту на
домен ми не приймаємо. Розсилок листами поки немає — усі листи разові; коли з'являться, вони йдуть
із `List-Unsubscribe` і відпискою в один клік (`doc/plan.md`, етап 23, п. 9).

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

### Назад на Resend

Якщо поштовики почнуть різати листи з нашого IP, повертаємось на Resend — лише змінними, без
зміни коду. У `.env.production` замінити блок вище на

```
MAIL_HOST=smtp.resend.com
MAIL_PORT=587
MAIL_USERNAME=resend
MAIL_PASSWORD=<ключ API Resend>
MAIL_AUTH=true
MAIL_STARTTLS=true
MAIL_FROM=Новелка <no-reply@novelka.panrid.space>
```

і виконати погоджений деплой через `sh infra/deploy.sh` на сервері. Адреса відправника — на
`novelka.panrid.space`, бо в Resend підтверджено саме цей домен; щоб слати з `novelka.in.ua`,
його спершу треба додати в Resend і внести його записи в DNS. Ключ — у кабінеті Resend (у
`.env.production` його зараз немає). Сервіс `mail` при цьому може працювати далі — він нікому
не заважає. Назад на свій сервер — той самий блок, що вище.

### DNS і PTR

DNS домену `novelka.in.ua` (NIC.UA):

| Ім'я | Тип | Значення |
|---|---|---|
| `@` | TXT | `v=spf1 ip4:62.72.33.247 -all` |
| `novelka._domainkey` | TXT | публічний ключ DKIM (нижче, як його взяти) |
| `_dmarc` | TXT | `v=DMARC1; p=none; rua=mailto:<пошта власника>` → згодом `p=quarantine` |

Станом на 2026-10-19 MX відсутній: після спроби додати `0 .` у NIC.UA запис не з’явився
на авторитетних NS. Причину ще потрібно з’ясувати з підтримкою. Коректний MX і шлях повернень
листів, зокрема придатність Null MX для домену відправника, перевіряємо в #284. Запис `_dmarc` має бути один — два
записи поштовики вважають помилкою й не зважають на обидва.

Зворотний запис IP (PTR) у панелі Hostinger (VPS → Налаштування → IP-адреса) — `novelka.in.ua`
для IPv4; так само звуть себе й Postfix (`POSTFIX_myhostname`). IPv6 мережа Compose не має, тож
його PTR не потрібен.

Ключ DKIM створюється сам при першому запуску й лежить у томі `novelka_dkim`; приватна частина
не виходить із сервера. Публічна частина:

```
ssh novelka@62.72.33.247 'docker exec novelka-mail-1 cat /etc/opendkim/keys/novelka.in.ua.txt'
```

Якщо том пропаде, ключ створиться новий — його публічну частину треба знову внести в DNS, інакше
листи не пройдуть DKIM.

### Перевірка

Записи: `dig +short TXT novelka.in.ua`, `dig +short TXT novelka._domainkey.novelka.in.ua`,
`dig +short TXT _dmarc.novelka.in.ua`, `dig +short -x 62.72.33.247`. Лист: адресу з
https://www.mail-tester.com (має бути 10/10) вписати як нову пошту в налаштуваннях акаунта —
сайт надішле справжній лист «підтвердіть пошту», а сама пошта не зміниться, доки не підтвердити;
так само — на Gmail, Outlook, ukr.net. Журнал — `docker logs novelka-mail-1`: `status=sent` —
прийнято наступним поштовим сервером (це не гарантує потрапляння у вхідні), `deferred` —
відкладено, `bounced` — остаточна відмова; причина в тому ж рядку.

Перевірено 2026-10-19: справжній лист сайту отримав **7/10**, SPF, DKIM, DMARC і PTR/HELO
успішні; −3 за відсутній MX. Досягнення 10/10 і перевірки скриньок перенесено в #284 за
рішенням власника; #224 завершує перехід на власну відправку. Для DMARC-звітів на зовнішній
домен потрібна його авторизація; наявність `rua` ще не гарантує отримання звітів. Перехід
на `p=quarantine` — після аналізу накопичених звітів, окремим рішенням власника (#284).

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
