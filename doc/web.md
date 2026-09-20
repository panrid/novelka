# Вебкаталог і читалка

[До змісту](README.md) · [Архітектура](architecture.md) · [CLI](cli.md)

## Що вже працює

- Каталог із пошуком за назвою, автором, ID та аліасами.
- Фільтр новел із готовими главами.
- Сторінка новели зі змістом і кількістю доступних глав.
- Читалка: заголовки, абзаци, передмова, післямова, розділювачі сцен.
- Переходи між доступними главами, навіть якщо номери йдуть із пропусками.
- Розмір тексту 16–28 px, світла й темна теми; налаштування зберігаються локально.
- Продовження з останньої відкритої глави в цьому браузері.
- Мобільна верстка, клавіатурна навігація, завантаження, порожні стани та повтор після помилки.

Каталог показує назви й авторів із метаданих джерела; переклад назви всієї новели
окремо ще не зберігається. Заголовки глав беруться з перекладу.
Обкладинки — декоративне оформлення CSS, не зображення із Syosetu і не згенеровані ілюстрації.

Імпорт, переклад, вичитка, словник та витрати поки керуються через CLI.
Акаунтів, синхронізації прогресу між пристроями та редакторських правок немає.
Зберігається номер відкритої глави, а не позиція прокрутки або факт прочитання.

## Запуск однією командою

Передумови: JDK 25, Node.js 22.12+ з npm, PostgreSQL із параметрами у .env.local.

```sh
# Якщо PostgreSQL ще не запущено:
docker compose --env-file .env.local -f infra/compose.yaml up -d --wait

# З кореня репозиторію:
./novelka-web
```

Відкрити [http://127.0.0.1:8080](http://127.0.0.1:8080), зупинити Ctrl+C.
OPENROUTER_API_KEY для роботи читалки не потрібен.

Launcher читає .env.local за тими самими правилами, що й novelka.
NOVELKA_ENV_FILE задає інший файл; відносний шлях рахується від поточного каталогу.
Невдале збирання зупиняє запуск, тому старий JAR випадково не стартує.

```sh
NOVELKA_ENV_FILE=.env.translation.local ./novelka-web
./novelka-web --server.port=8081
```

Gradle-задача :server:bootJar встановлює npm-залежності через npm ci, перевіряє
TypeScript, збирає Vite та включає frontend/dist у BOOT-INF/classes/static.
Результат — server/build/libs/novelka-server.jar. Повторні незмінені задачі пропускаються.
:cli:installDist не запускає npm і не пакує сайт.

## Розробка з автоматичним оновленням UI

У першому терміналі з кореня завантаж конфігурацію БД і запусти API:

```sh
set -a
. ./.env.local
set +a
./gradlew :server:bootRun
```

У другому:

```sh
cd frontend
npm ci
npm run dev
```

Відкрити [http://127.0.0.1:5173](http://127.0.0.1:5173).
Vite пересилає /api на 127.0.0.1:8080; у цьому режимі порт API має лишатися 8080,
або потрібно змінити proxy у vite.config.ts.
bootRun запускає API; зібраний frontend додається саме у bootJar.
npm run preview показує production-збірку на 4173 із таким самим proxy.

## Конфігурація і межі

| Параметр | Значення за замовчуванням |
|---|---|
| NOVELKA_DB_URL | jdbc:postgresql://localhost:5432/novelka |
| NOVELKA_DB_USER | novelka |
| NOVELKA_DB_PASSWORD | novelka |
| NOVELKA_SERVER_PORT | 8080 |
| server.address | 127.0.0.1 |

Це локальний сервер без автентифікації; він слухає loopback. Публічна публікація
потребує окремого налаштування доступу, HTTPS та експлуатації.
Frontend не читає кореневі env-файли й не містить API-ключів.
Spring не повертає stack trace або повідомлення внутрішніх винятків через API.

## API

| Метод і шлях | Результат |
|---|---|
| GET /api/novels | NovelCard[]: id, title, author, chapterCount, readyChapters, aliases |
| GET /api/novels/{idOrAlias} | NovelDetail: метадані та ChapterSummary[] |
| GET /api/novels/{idOrAlias}/chapters/{number} | ReaderChapter: novelId, number, revision, title, blocks |

ChapterSummary містить number, title, revision. Block містить id, kind, text.
Невідома новела/недоступна глава — 404; нечисловий або непозитивний номер — 400.
Операцій запису чи викликів OpenRouter у server немає.

Глава доступна, тільки якщо **остання ревізія** має state=complete і sourceHash
відповідає поточному оригіналу. Старі завершені ревізії не підставляються замість
нової незавершеної; needs-review також прихований.
Читалка отримує лише revised-блоки, без чернеток, оригіналу й контексту AI.

ReaderRepository формує проєкції: каталог і зміст не передають повні тексти глав.
ReaderService відкриває новий JdbcSession на HTTP-запит і закриває його через
try-with-resources. Міграції виконуються один раз у ReaderDatabase при запуску.
Схема PostgreSQL лишилась V2; окремої копії бази для фронтенду немає.

## Навігація у коді

| Каталог | Відповідальність |
|---|---|
| frontend/src/pages | CatalogPage, NovelPage, ReaderPage |
| frontend/src/components | Спільні loading/error стани |
| frontend/src/api | TypeScript-контракти та HTTP-клієнт |
| frontend/src/hooks | useResource: завантаження, скасування застарілих запитів, retry |
| frontend/src/lib | URL-маршрути та localStorage з обробкою відмови сховища |
| frontend/src/App.tsx | Оболонка, hash-маршрути, header/footer |
| frontend/src/styles.css | Адаптивна верстка і тема |
| server/controller, service, dto, config | HTTP, прикладне читання, response-моделі, JDBC |
| core/repository/ReaderRepository.java | SQL доступних для читання ревізій |

Адреси виглядають як /#/novels/n0022gd/chapters/1: оновлення вкладеної сторінки
не потребує серверних правил перенаправлення. API завжди викликається з того самого origin.
Ключі localStorage мають префікс novelka:; прогрес використовує канонічний ID.

## Перевірки

```sh
./gradlew test :core:integrationTest :server:integrationTest
./gradlew :server:bootJar
sh -n novelka novelka-web

cd frontend
npm ci
npm run build
npx playwright install chromium
npm test
```

ReaderIntegrationTest піднімає тимчасову PostgreSQL і справжній Spring HTTP-сервер:
перевіряє аліаси, каталог, блоки, приховування нових незавершених/застарілих ревізій
та помилки API. WebLauncherTest перевіряє шляхи з пробілами, env-файли та збій збірки.

Playwright виконує сценарії для desktop і mobile Chromium. API у браузерних тестах
підмінений фікстурами; реальний API перевіряється окремо інтеграційними тестами.
Перевіряються пошук, фільтр, навігація, прямі посилання, теми, localStorage,
помилки та відображення HTML-подібного тексту без його виконання.
Ці перевірки не оцінюють літературну якість перекладів.

Для росту каталогу знадобляться серверна пагінація та пошук, для вебперекладу —
черга завдань і контроль доступу. Поточна версія призначена для особистого локального читання.

## Технологічні джерела

- [Spring Boot: сумісність Java та Gradle](https://docs.spring.io/spring-boot/system-requirements.html).
- [Vite: вимоги до Node.js та запуск](https://vite.dev/guide/).
