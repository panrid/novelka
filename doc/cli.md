# Запуск і довідник CLI

[До змісту](README.md) · [Конфігурація](../README.md#конфігурація) · [Відновлення](pipeline-and-data.md)

## Перший запуск

Потрібні JDK 25 і PostgreSQL. Docker потрібен лише для запропонованого локального
PostgreSQL; JVM не запускається в контейнері. Gradle Wrapper завантажує власну версію
та залежності при першій збірці.

Із кореня репозиторію:

```sh
# Лише якщо .env.local ще немає; наявний файл не перезаписуйте.
test -e .env.local || cp .env.example .env.local
# Заповніть OPENROUTER_API_KEY у редакторі.
docker compose --env-file .env.local -f infra/compose.yaml up -d --wait
./novelka init
./novelka --help
```

Compose читає NOVELKA_DB_PASSWORD з явно переданого env-файлу. Зміна пароля у файлі
не змінює пароль уже ініціалізованого PostgreSQL volume. Не видаляйте volume для
виправлення конфігурації: там зберігаються ваші переклади.

## Як працює launcher

Кореневий `./novelka`:

1. Знаходить власний корінь і читає `.env.local` з цього кореня.
2. Якщо задано NOVELKA_ENV_FILE, читає саме цей файл замість стандартного.
   Відносний шлях рахується від поточної директорії термінала. Відсутній явно заданий файл — помилка.
3. На кожний запуск викликає `gradlew -p ROOT -q :cli:installDist`.
   Gradle пропускає незмінені задачі, але перевіряє й видалення джерел та зміни ресурсів.
4. Запускає зібраний CLI з оригінальними аргументами і поточною директорією.

Є невелика затримка на перевірку Gradle навіть для --help. Якщо потрібен запуск без
цієї перевірки, після `./gradlew :cli:installDist` можна запускати
`cli/build/install/novelka/bin/novelka` напряму. Він **не завантажує env-файл**:
передайте експортовані змінні самостійно.

Env-файл читається shell-командою source/dot: це довірена локальна shell-конфігурація,
не універсальний dotenv-парсер. Значення з пробілами беріть у лапки.
Значення з файлу перевизначають експортовані змінні, якщо присутні в обох місцях.
Логи збірки й резервів коштів ідуть у stderr; деякі CLI-команди змішують прогрес і JSON
у stdout, тому загального контракту «один JSON на кожну команду» зараз немає.

Для запуску з будь-якого каталогу можна додати у свою shell-конфігурацію функцію:

```sh
novelka() {
    /Users/dbahazhkov/IdeaProjects/novelka/novelka "$@"
}
```

Шляхи --file та --output залишаються відносними до поточного каталогу термінала.

## Приклад для n0022gd

```sh
# Метадані без завантаження тексту.
./novelka import https://ncode.syosetu.com/n0022gd/ --alias water

# Або аліас для вже імпортованої новели.
./novelka alias add n0022gd water

# Перший непорожній рядок chapter.txt — заголовок.
./novelka import-text water --chapter 1 --file chapter.txt
./novelka chapters water

# Платна дія: аналіз + переклад + повна автоматична вичитка.
NOVELKA_ENV_FILE=.env.translation.local ./novelka translate water --chapter 1 --budget-usd 0.10
./novelka status water
./novelka costs water --details
./novelka export water --format html --output exports/water.html
```

Файл .env.translation.local опційний і має містити потрібні налаштування сам:
автоматичного об'єднання з .env.local немає.

## Команди

| Команда | Призначення | AI-витрати |
|---|---|---|
| init | Застосувати зареєстровані міграції | Ні |
| import URL [--alias NAME] [--chapter N або --chapters A-B] | Метадані й опційно оригінали | Ні |
| import-text NOVEL --chapter N --file FILE | UTF-8 глава з локального файлу | Ні |
| chapters NOVEL | Метадані та вже збережені оригінали, не весь віддалений каталог | Ні |
| translate NOVEL --chapter N або --chapters A-B | Завантажити відсутні оригінали, обробити сегменти | Так |
| resume JOB_ID [--retry-uncertain] | Продовжити конкретний Work | Можливі |
| proofread NOVEL --chapter N | Нова ревізія вичитки з наявної чернетки | Так |
| status [NOVEL] | Без аргументу — новели/аліаси; з аргументом — всі ревізії Work | Ні |
| glossary NOVEL [--file FILE] [--proposals] | Словник, ручний імпорт, перегляд пропозицій | Ні |
| export NOVEL --format html або epub --output FILE | Експорт актуальних complete-глав | Ні |
| costs [NOVEL] [--details] | Агрегати або журнал AI-спроб | Ні |
| alias add NOVEL ALIAS | Прив'язати ім'я; повтор для тієї ж новели безпечний | Ні |
| alias list [NOVEL] | Усі або лише відповідні аліаси | Ні |
| alias remove ALIAS | Прибрати тільки ім'я, зберегти новелу та переклади | Ні |

Усі команди мають --help. NOVEL — ID або аліас; resume приймає саме JOB_ID.
Для translate обов'язково рівно один із --chapter / --chapters.
Діапазон включний, починається з 1 і обмежений збереженою кількістю глав.
--force існує лише для translate.

AI-команди підтримують --model і --budget-usd. Модель для етапу обирається так:
NOVELKA_<STAGE>_MODEL → --model → NOVELKA_MODEL → openai/gpt-4o-mini.
Окремо задана модель етапу має пріоритет навіть над --model.
Для моделі, відмінної від початкової, задайте тарифи відповідного етапу:
NOVELKA_<STAGE>_INPUT_USD_M та NOVELKA_<STAGE>_OUTPUT_USD_M.
Це локальні параметри резервування; автоматичного запиту каталогу тарифів немає.

## Аліаси

Від 1 до 64 символів; початок — літера чи цифра, далі також дозволені крапка,
підкреслення й дефіс. Пробіли по краях прибираються; регістр зводиться до нижнього.
Українські літери підтримуються; внутрішні пробіли — ні. Новела може мати кілька аліасів.

Аліас не може вказувати на дві новели. Реєстрація нового ID теж перевіряє конфлікт
із наявними аліасами, тому пізніший імпорт не змінить значення команди непомітно.
Переприв'язування потребує явного remove, потім add. Канонічний ID новели не змінюється.

import записує метадані й аліас в одній транзакції після перевірки діапазону.
Глави імпортуються послідовно: якщо завантаження пізнішої впало, попередні вже збережені.

## Типові помилки

| Повідомлення/симптом | Що перевірити |
|---|---|
| Connection refused | PostgreSQL, порт, NOVELKA_DB_URL; стан docker compose |
| Password authentication failed | Відповідність env-файлу й пароля вже створеної БД |
| Environment file not found | Шлях NOVELKA_ENV_FILE від поточного каталогу |
| Novel or alias not found | status, alias list; чи виконано import metadata |
| Alias conflicts / already points | alias list; використайте іншу назву або явно переприв'яжіть |
| Paragraph exceeds segment limit | Збільште NOVELKA_SEGMENT_CHARS для нового Work |
| Source changed | Перегляньте зміни оригіналу; translate --force створить нову платну ревізію |
| Budget reached | costs --details; продовжте з прийнятним бюджетом |
| Uncertain previous request | Перевірте ai_calls/витрати провайдера перед --retry-uncertain |
| No fully proofread chapters | status; потрібна остання complete-ревізія з актуальним sourceHash |

--retry-uncertain може повторно оплатити запит із невідомим попереднім результатом.
Поточна proofread не оновлює sourceHash під змінений оригінал:
для такого випадку потрібен translate --force, інакше результат не потрапить в експорт.
