# Архітектура і карта коду

[До змісту](README.md) · [Процес і дані](pipeline-and-data.md) · [Розробка](development.md)

## Межі проєкту

Novelka зараз є локальним CLI для одного оператора. PostgreSQL зберігає оригінали,
результати та журнал витрат, OpenRouter виконує аналіз, переклад і вичитку.
Сайт, облікові записи, бібліотеки, редакторська модерація, оплати й ілюстрації — майбутні етапи.

`settings.gradle` підключає два модулі: `cli` → `core`. Обидва використовують Java 25.
Майбутній server зможе залежати від core, але для цього ще потрібно винести частину
прикладної логіки та SQL із CLI. Поточний core не залежить від Picocli, хоча ще друкує
деякі повідомлення безпосередньо в stdout/stderr.

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
| `core` | Pipeline, JDBC Store, інтеграції, словник, експорт та утиліти |

Файли Java лежать у `<module>/src/main/java`; тести — в `src/test/java`.
Пакет `core` поки переважно плаский. Це залишений борг, а не завершене розділення всього ядра.

## Шлях виконання команди

1. Кореневий shell-скрипт `novelka` читає конфігурацію та викликає інкрементальну збірку.
2. Зібраний application launcher запускає `Novelka.main` із JVM timezone UTC.
3. Picocli перевіряє синтаксис і обов'язкові параметри; help не відкриває базу.
4. `DatabaseCommand.call` створює Store; його конструктор застосовує відсутні міграції.
5. Команда розпізнає ID/аліас. Команди зміни перекладу беруть lock за канонічним ID.
6. Команда викликає ядро й друкує результат; try-with-resources закриває Store.

Успішне виконання повертає exit code 0, помилка виконання — 1,
помилка параметрів Picocli зазвичай — 2. Launcher повертає помилку збірки без запуску старого CLI.

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
| `Store` | Один JDBC Connection, SQL, транзакції, locks, міграції та читання/збереження моделей |
| `BookExporter` | HTML/EPUB із переданих завершених Work; не виконує AI-запитів |
| `Json`, `Hashes`, `Tokens` | Серіалізація, SHA-256 серіалізованого значення, o200k_base |

`ApplicationContext.pipeline` створює три OpenRouter-клієнти, по одному для analyze,
translate і proofread. Вибір моделі та тарифу відбувається для кожного етапу.
Це просте ручне складання залежностей без DI-фреймворку.

## Моделі

- `Novel`: канонічний ID джерела, оригінальна назва, автор, URL, кількість епізодів.
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
| Аліаси | Store.resolveNovel/saveAlias + cli.command.alias |
| Зміна джерела | NovelSource, Syosetu, ImportCommand, TranslateCommand |
| Контекст, гендер, сталі імена | Dictionary, Entry, prompts/analyze.txt, Pipeline.applyAnalysis |
| Стиль перекладу | prompts/translate.txt та prompts/proofread.txt |
| Повтор після збою | Pipeline.run і OpenRouter.generate/invoke |
| Бюджет і аналітика | AiCommand, TranslateCommand, OpenRouter.invoke, Store.spent, CostsCommand |
| Верстка експорту | BookExporter |

Перед додаванням другого джерела потрібен resolver джерел: зараз CLI безпосередньо
створює Syosetu. Перед веб API доречно виділити прикладні сервіси імпорту/перекладу
та репозиторії зі Store. Не варто змінювати одночасно структуру пакетів і формат збережених даних.
