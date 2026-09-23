# Архітектура і карта коду

[До змісту](README.md) · [Процес і дані](pipeline-and-data.md) · [Розробка](development.md)

## Межі проєкту

Novelka зараз має локальний CLI для одного оператора та вебкаталог із читалкою. PostgreSQL зберігає оригінали,
результати та журнал витрат, OpenRouter виконує аналіз, переклад і вичитку.
Облікові записи, бібліотеки, редакторська модерація, оплати й ілюстрації — майбутні етапи.

settings.gradle підключає cli → core та server → core. Усі використовують Java 25.
Frontend — React + TypeScript із Vite; server — Spring Boot із JDBC-репозиторіями ядра.
SQL ізольований у repository/persistence; оркестрація імпорту та діапазону глав належить CLI.
Core не залежить від Picocli, хоча ще друкує деякі повідомлення у stdout/stderr.

## Карта пакетів

Всі пакети мають префікс `panrid.space.novelka`.

| Пакет | Що шукати |
|---|---|
| `cli` | `Novelka`: реєстрація команд, main, загальний обробник помилок |
| `cli.command` | Окремий клас на кожну команду; параметри та виконання |
| `cli.command.alias` | Група alias, add/list/remove |
| `cli.config` | `ApplicationContext`: JDBC-конфігурація та створення AI-клієнтів |
| `cli.support` | `ChapterRange`: перевірка діапазону; `Output`: JSON |
| `core.model` | Вісім top-level record-моделей; структура збереженого JSON |
| `core.service.translation` | Pipeline та Segments: процес перекладу і робота з сегментами |
| `core.service.glossary` | Dictionary: добір контексту; GlossaryService: зміни словника та інвалідація |
| `core.repository` | NovelRepository, ChapterRepository, JobRepository, GlossaryRepository, AiCallRepository; ReaderRepository — проєкції для читання |
| `core.persistence` | JdbcSession, MigrationRunner, DatabaseSession: з'єднання, міграції, складання залежностей |
| `core.integration.ai` | AiClient та OpenRouter |
| `core.integration.source` | NovelSource; реалізації в syosetu та text |
| `core.export` | BookExporter: HTML та EPUB |
| `core.support` | Json, Hashes, Tokens |
| `server.controller` | ReaderController: GET endpoints |
| `server.service` | ReaderService: DTO каталогу, змісту та глави |
| `server.config` | ReaderDatabase: міграції при старті, нове з’єднання для кожного запиту |
| `server.dto` | Окремі response-records; внутрішній Work не виходить у браузер |

Файли Java лежать у `<module>/src/main/java`; тести — в `src/test/java`.
Тести згруповані за відповідними пакетами; інтеграційні сценарії репозиторіїв лежать у core.repository.

## Шлях виконання команди

1. Кореневий shell-скрипт `novelka` читає конфігурацію та викликає інкрементальну збірку.
2. Зібраний application launcher запускає `Novelka.main` із JVM timezone UTC.
3. Picocli перевіряє синтаксис і обов'язкові параметри; help не відкриває базу.
4. `DatabaseCommand.call` через `ApplicationContext.openDatabase` створює DatabaseSession.
   Той відкриває JdbcSession, викликає MigrationRunner та складає репозиторії й GlossaryService.
5. Команда розпізнає ID/аліас. Команди зміни перекладу беруть lock за канонічним ID.
6. Команда викликає ядро й друкує результат; try-with-resources закриває DatabaseSession і його JDBC-з'єднання.

Успішне виконання повертає exit code 0, помилка виконання — 1,
помилка параметрів Picocli зазвичай — 2. Launcher повертає помилку збірки без запуску старого CLI.

## Шлях вебзапиту

novelka-web → Gradle bootJar → npm ci / Vite build → JAR із React → Spring Boot.
У браузері hash-маршрути зберігають прямі посилання без серверного SPA fallback.

React → /api/novels → ReaderController → ReaderService → ReaderDatabase.open →
ReaderRepository / NovelRepository → PostgreSQL. З’єднання закривається після запиту.
Міграції застосовуються на старті server; адміністративний DatabaseSession
також ідемпотентно перевіряє їх. Спільного singleton Connection немає.
Frontend показує revised-блоки як текст, без вставляння довільного HTML.

ReaderRepository використовує ту саму умову доступності, що й експорт: остання
ревізія complete та sourceHash поточного оригіналу. Це перевіряється і для змісту,
і для прямого запиту глави. Докладніше про структуру UI й API — [web.md](web.md).

## Відповідальності ядра

| Клас | Контракт і залежності |
|---|---|
| `NovelSource` | inspect(URL) → Novel; fetch(Novel, number) → Chapter |
| `Syosetu` | Реалізація джерела: API метаданих і HTML епізодів, jsoup |
| `PlainText` | Локальний UTF-8 текст → ті ж блоки Chapter |
| `Segments` | Поділ між блоками; перевірка кількості, порядку, ID, kind і непорожнього тексту |
| `Pipeline` | create/run/proofread, переходи станів, контрольні точки, пропозиції словника |
| `AiClient` | generate(work, segment, stage, glossary, payload, budget) → JsonNode |
| `OpenRouter` | HTTP, промпти, JSON Schema, dictionary_search, резерв і журнал викликів |
| `Dictionary` | Валідація, пошук сутностей, обмежена добірка контексту |
| `JdbcSession` | Один Connection, параметризовані SQL-операції, транзакції та advisory locks |
| `DatabaseSession` | Життєвий цикл з'єднання і складання залежностей; сам SQL не містить |
| `MigrationRunner` | Версії SQL-схеми, блокування та атомарне застосування міграцій |
| `NovelRepository` | Метадані, розпізнавання ID/аліасів, конфлікти і спільний простір імен |
| `ChapterRepository` | Поточні оригінали, історія оригіналів, список глав |
| `JobRepository` | Ревізії Work, стани, метрики, вибір актуальних завершених глав |
| `GlossaryRepository` | Поточний словник, історія, пропозиції; без бізнес-правил інвалідації |
| `AiCallRepository` | Журнал запитів і відповідей, невідомі результати, повтори, витрати |
| `GlossaryService` | Транзакція зміни словника та інвалідації Work, що використали змінені факти |
| `BookExporter` | HTML/EPUB із переданих завершених Work; не виконує AI-запитів |
| `Json`, `Hashes`, `Tokens` | Серіалізація, SHA-256 серіалізованого значення, o200k_base |

`ApplicationContext.pipeline` створює три OpenRouter-клієнти, по одному для analyze,
translate і proofread. Вибір моделі та тарифу відбувається для кожного етапу.
Це ручне складання залежностей без DI-фреймворку. Pipeline отримує DatabaseSession,
а кожен OpenRouter — лише AiCallRepository. У CLI, Pipeline та OpenRouter немає SQL.

DatabaseSession не є перейменованим універсальним сховищем: він не містить запитів
і не дублює методи CRUD репозиторіїв. Проте Pipeline поки залежить від цього конкретного
набору залежностей; це свідомий проміжний крок, а не повністю незалежне від БД ядро.
Причини відкласти Spring Boot/Hibernate описано в [окремому рішенні](persistence-decision.md).

## Моделі

- `Novel`: канонічний ID джерела, оригінальні назва й автор, українські назва,
  автор і опис, URL, кількість епізодів. Локалізовані поля зберігаються разом з
  метаданими джерела, тому повторний імпорт не стирає їх.
- `Chapter`: номер, URL, заголовок, блоки й rawHtml; для локального імпорту rawHtml порожній.
- `Block`: стабільний у межах глави ID, kind і текст. ID не є глобальним.
- `Glossary` / `Entry`: ревізія словника та відомості про сутності.
- `Work` / `Segment`: версія перекладу й стан обробки кожного сегмента.
- `AiCall`: дані початку HTTP-спроби; фінальна ціна та відповідь дописуються в SQL.

Records не мають вкладених типів. Незмінність record-полів не робить переданий List
автоматично незмінним; при збереженні контрольних точок pipeline використовує копії списків.
JSON не містить імен Java-пакетів, тому перенесення моделей з Domain не вимагало перезапису БД.

## Куди вносити типові зміни

| Задача | Почати з |
|---|---|
| Нова CLI-команда | cli.command + реєстрація в Novelka |
| Аліаси | NovelRepository.resolveNovel/saveAlias + cli.command.alias |
| Зміна джерела | NovelSource, Syosetu, ImportCommand, TranslateCommand |
| Контекст, гендер, сталі імена | Dictionary, Entry, prompts/analyze.txt, Pipeline.applyAnalysis |
| Стиль перекладу | prompts/translate.txt та prompts/proofread.txt |
| Повтор після збою | Pipeline.run і OpenRouter.generate/invoke |
| Бюджет і аналітика | AiCommand, TranslateCommand, OpenRouter.invoke, AiCallRepository.spent, CostsCommand |
| Верстка експорту | BookExporter |

Перед додаванням другого джерела потрібен resolver джерел: зараз CLI й вебворкер
безпосередньо створюють Syosetu. `server.task.TaskService` валідує запит і зберігає
чергу; `TaskWorker` викликає той самий core Pipeline, ізольовано від CLI.
`TaskAiFactory` дозволяє перевіряти воркер без платного провайдера. Повідомлення
стану й витрати читаються з PostgreSQL; технічні повідомлення core лишаються в логах.

## Вебакаунти й редагування

Spring Security керує сесією та CSRF; `AccessService` читає актуальні ролі з БД.
`CorrectionService` під lock новели створює ручну ревізію та `work_origins`;
`GlossaryService` враховує цей ланцюжок при пошуку залежних перекладів.
SQL акаунтів і майстерні згрупований у `server.repository`, міграція — `V3.sql`.
`V4.sql` додає сповіщення. `core.repository.NotificationEventRepository` записує
подію в тій самій транзакції, що й публікацію глави (`JobRepository.save`),
нові ключі словника (`GlossaryService.update`) або стан вебзавдання (`TaskRepository`).
Унікальний `event_key` запобігає дублям при повторному збереженні чи відновленні.
Текст помилки, ключі API та AI-контекст у сповіщення не копіюються.
`server.repository.NotificationRepository` фільтрує події за актуальною роллю
і датою створення акаунта та зберігає прочитання; `NotificationsController`
перевіряє авторизацію. Масове прочитання обмежене останнім ID, побаченим клієнтом,
щоб одночасно отримана нова подія лишилась непрочитаною.

`server.service.GlossaryProposalService` групує записи журналу за нормалізованим
вмістом і позначає збіги з канонічним словником. V5 зберігає відхилення за відбитком,
тому повтор тієї самої пропозиції не повертається на перевірку. Запис відхилення
та аудит виконуються в одній транзакції. Core продовжує зберігати повний журнал.
`TaskRequest.dictionarySearchLimit` передається через воркер і `TaskAiFactory`
до OpenRouter. У старому JSON це поле відсутнє; record підставляє 6.
`site_settings` зберігає вебналаштування; кожне завдання отримує їхній знімок.
Детальна карта та інваріанти — [Акаунти й майстерня](accounts-and-management.md).
