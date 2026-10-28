# Деплой

## Робочий сервер

З 2026-10-28 сайт працює в однонодовому **k3s** на `62.72.33.247`
(`srv1581547`), на тому самому сервері, де розміщене оточення розробки.
Адреса — `https://novelka.in.ua`, Студія — `/studio`.

У просторі імен `novelka-prod`:

| Ресурс | Призначення |
|---|---|
| Deployment і Service `novelka` | застосунок на порту 8080 |
| StatefulSet і Service `postgres` | PostgreSQL 17 |
| PVC `postgres-data-postgres-0` | робоча база, 10 GiB |
| PVC `novelka-media` | картинки й інші завантаження, 5 GiB |
| Secret `postgres-auth` | пароль бази, ключ `NOVELKA_DB_PASSWORD` |
| Secret `novelka-config` | ключі ШІ, Google, Telegram і налаштування пошти |
| Ingress `novelka` | Traefik, HTTPS і Let's Encrypt |

Дані вже перенесені. Звичайний деплой **не створює й не перевстановлює** PostgreSQL,
PVC, Secret або Traefik. Маніфести початкового перенесення лежать на сервері в
`/srv/novelka/k8s/`; секрети в репозиторій не потрапляють.

Образи імпортуються без реєстру в локальний containerd k3s. Тому сценарій навмисно
відмовляється працювати, якщо в кластері більше однієї ноди. Перед розширенням кластера
потрібно окремо спроєктувати доставку образів і спільне сховище медіа.

## Як іде деплой

Push у `main` запускає `.github/workflows/deploy-production.yml`; самі документи — ні.
Тести й збірка JAR залишаються на GitHub-hosted runner. Вибір перевірок від останнього
успішного деплою (`.github/scripts/changes.sh`) і повторне використання перевіреного JAR
із pull request збережені. Червоні перевірки блокують деплой.

1. Перевірений `novelka.jar`, `backend/Dockerfile`, `infra/deploy.sh` і `infra/k8s/`
   доставляються через SSH у `/srv/novelka-releases/<повний SHA коміту>/`.
2. На сервері `infra/deploy.sh` перевіряє готовність ноди, наявність базових ресурсів
   і коректність маніфестів через server-side dry-run, застосовує маніфест пошти й чекає
   готовності SMTP перед оновленням застосунку. Режими `--check` і `--build-only` пошту не змінюють.
3. Тимчасовий pod BuildKit збирає образ **локально**, лише з JAR і Dockerfile.
   Йому потрібен `privileged` для роботи BuildKit; токена service account і томів сайту в нього немає.
   Ліміти — 1 CPU і 2 GiB RAM, час життя — до 10 хвилин.
4. Образ `docker.io/library/novelka:<SHA>` імпортується в namespace `k8s.io` containerd.
   BuildKit видаляється. Образ не публікується в Docker Hub; `imagePullPolicy: Never`.
5. Стратегічний патч `infra/k8s/deployment.patch.yaml` змінює образ і параметри оновлення,
   зберігаючи чинні змінні, посилання на секрети й том медіа.
6. `RollingUpdate` з `maxUnavailable: 0`, `maxSurge: 1` залишає стару копію,
   доки нова не пройде `/api/health` і не буде готовою 10 секунд.
   Startup probe дає запуску близько 180 секунд. Readiness перевіряє також доступ до бази.
   Завершення старої копії має бюджет 180 секунд на поточні відповіді моделей.
7. Після `rollout status` перевіряються публічні `/api/health` і головна сторінка.
   Помилка оновлення або перевірки запускає відкат до попередньої ревізії Deployment,
   а сценарій завершується помилкою. GitHub також повторно перевіряє публічний сайт.

Паралельні деплої обмежені і GitHub concurrency, і локальним `flock`.
Збій збірки не змінює робочий Deployment. Повторний запуск того самого коміту використовує вже імпортований образ, не змінюючи тег.
Образи попередніх версій автоматично не
видаляються: вони потрібні для відкату; місце на диску слід контролювати.

Обидві копії короткий час працюють з однією базою. Міграції мають бути сумісними
з попередньою версією; видалення й перейменування — у два деплої. Відкат образу
**не відкочує міграцій бази**.

## Доступ GitHub Actions

Використовуються secrets середовища `production`: `DEPLOY_HOST`, `DEPLOY_USER`,
`DEPLOY_SSH_KEY`, рекомендовано `DEPLOY_KNOWN_HOSTS`.

`DEPLOY_HOST=62.72.33.247`, `DEPLOY_USER=novelka-deploy`. Окремий акаунт має каталог
`/srv/novelka-releases` і право `sudo -n /usr/local/sbin/novelka-deploy <SHA>`.
Root-вхідний сценарій приймає лише повний SHA і відомий режим запуску.
Це привілейований доступ: акаунт доставляє код деплою й маніфести BuildKit, тому
йому довіряють як production. Іншим користувачам це право не надається.

Разове налаштування сервера (публічний ключ відповідного секрету CI):

```sh
sudo bash infra/setup-deploy.sh /шлях/до/ключа.pub
```

Сценарій встановлює root-owned `/usr/local/sbin/novelka-deploy` і окреме правило sudoers;
перевіряє правило через `visudo`. Ключ акаунта має SSH-обмеження `restrict`:
без тунелів, пересилання агента й PTY; команди та SFTP залишаються доступними.
Повторний запуск замінює дозволений ключ цього акаунта на переданий.
Пароль sudo в CI не передається. Локальний пароль розробника не замінює SSH-ключа Actions.
Перед першим запуском власник перевіряє ці налаштування; сам сценарій їх не змінює.
Окремий self-hosted runner, Docker daemon чи реєстр образів не потрібні.

Під час налаштування 2026-10-28 створено окремий ключ у
`/home/panrid/.local/share/novelka-ci/id_ed25519`; він не в репозиторії.
З авторизованим GitHub CLI на цьому сервері secrets можна записати без виведення ключа:

```sh
gh secret set DEPLOY_HOST --repo panrid/novelka --env production --body 62.72.33.247
gh secret set DEPLOY_USER --repo panrid/novelka --env production --body novelka-deploy
gh secret set DEPLOY_SSH_KEY --repo panrid/novelka --env production < /home/panrid/.local/share/novelka-ci/id_ed25519
gh secret set DEPLOY_KNOWN_HOSTS --repo panrid/novelka --env production < /home/panrid/.local/share/novelka-ci/known_hosts
```

GitHub CLI встановлений і авторизований окремо від ChatGPT GitHub App.
Secrets середовища `production` оновлено для акаунта `novelka-deploy`; задача — #322.


## Ручна перевірка й запуск

У підготовленому каталозі релізу мають бути `novelka.jar`, `Dockerfile`,
`infra/deploy.sh`, `infra/k8s/buildkit.yaml`, `infra/k8s/deployment.patch.yaml`, `infra/k8s/mail.yaml`.
Замість `<SHA>` — повний 40-символьний SHA коміту.

```sh
# Лише перевірка передумов і server-side dry-run; ресурси не змінюються.
sudo bash /srv/novelka-releases/<SHA>/infra/deploy.sh /srv/novelka-releases/<SHA> <SHA> --check

# Справжня локальна збірка та імпорт образу, без оновлення сайту.
sudo bash /srv/novelka-releases/<SHA>/infra/deploy.sh /srv/novelka-releases/<SHA> <SHA> --build-only

# Збірка, імпорт, оновлення й перевірка сайту.
sudo bash /srv/novelka-releases/<SHA>/infra/deploy.sh /srv/novelka-releases/<SHA> <SHA>
```

Статус і ручний відкат (попередній образ має залишатися в containerd):

```sh
sudo kubectl -n novelka-prod get pods
sudo kubectl -n novelka-prod rollout history deployment/novelka
sudo kubectl -n novelka-prod rollout undo deployment/novelka --to-revision=<номер>
sudo kubectl -n novelka-prod rollout status deployment/novelka --timeout=480s
curl --fail https://novelka.in.ua/api/health
```

`NOVELKA_PUBLIC_URL` залишається `https://novelka.in.ua`. Google OAuth і Telegram
використовують цей домен; налаштування інтеграцій зберігаються в чинному Deployment і Secrets.

## Перевірка змін інфраструктури

```sh
bash -n infra/deploy.sh infra/deploy-entrypoint.sh infra/setup-deploy.sh
shellcheck infra/deploy.sh infra/deploy-entrypoint.sh infra/setup-deploy.sh
actionlint .github/workflows/deploy-production.yml
sudo python3 infra/tests/deploy.py
```

Остання команда перевіряє сценарії успіху, повторного використання образу, помилок
збірки, оновлення й публічної перевірки, помилки SMTP, а також обидва режими без оновлення сайту.
У ній усі звернення до Kubernetes, containerd і HTTP підмінені; справжній кластер
не використовується. Реальну збірку окремо перевіряють через `--build-only`.

## Пошта

SMTP працює як Deployment і внутрішній Service `mail` у `novelka-prod`
(`infra/k8s/mail.yaml`, #325). Публічного SMTP-порту немає; NetworkPolicy дозволяє
порт 587 лише pod застосунку. Postfix також приймає пошту лише з мережі pod
`10.42.0.0/16` і від домену `novelka.in.ua`, доставляє через IPv4.

Налаштування застосунку залишаються в Secret `novelka-config`:
`MAIL_HOST=mail`, `MAIL_PORT=587`, `MAIL_AUTH=false`, `MAIL_STARTTLS=false`,
відправник `Новелка <no-reply@novelka.in.ua>`. Пароль для внутрішнього SMTP не потрібен.
Якщо змінюється CIDR кластера, оновіть `POSTFIX_mynetworks` у маніфесті пошти.

Ліміт SMTP — 256 MiB і 0,5 CPU; запит — 64 MiB і 0,025 CPU. PVC `mail-data` (1 GiB,
`local-path-retain`) зберігає чергу `/var/spool/postfix` і ключі `/etc/opendkim/keys`.
При перевстановленні pod цей том не видаляють: інакше зміниться DKIM-ключ.
Init-контейнер готує каталог ключа з власником `opendkim` і правами `0700`.

Новий селектор DKIM — `novelka202610`; DNS TXT —
`novelka202610._domainkey.novelka.in.ua`. Публічний запис можна отримати так:

```sh
sudo kubectl -n novelka-prod exec deployment/mail -- cat /etc/opendkim/keys/novelka.in.ua.txt
sudo kubectl -n novelka-prod exec deployment/mail -- opendkim-testkey -d novelka.in.ua -s novelka202610 -vvv
sudo kubectl -n novelka-prod exec deployment/mail -- postqueue -p
```

Публікуйте лише TXT із `.txt`, ніколи приватний `.private`. SPF уже дозволяє
`62.72.33.247`, PTR веде на `novelka.in.ua`; DMARC — `p=none`. MX і подальша
перевірка доставлення різним провайдерам залишаються окремою задачею #284.

Перевірено 2026-10-28: DNS TXT збігається з ключем, OpenDKIM підтвердив `key OK`;
один погоджений тестовий лист із pod сайту підписаний DKIM і прийнятий Gmail (`250`),
черга спорожніла. Після перезапуску ключ зберігається; сторонній pod не має доступу до SMTP.

## Що лишилося після перенесення

- **Резервне копіювання** відкладено власником 2026-10-28; у цій задачі його не налаштовуємо.
- Переадресації `www` і старого домену та моніторинг залишаються окремими задачами. Поточний Ingress містить
  лише `novelka.in.ua`; наявність цих можливостей зі старого Compose не припускаємо.
- Окреме dev-оточення скасовано власником 2026-10-28 через обмежені ресурси. Його
  Compose-файл, сценарій деплою й маршрут видалені; локальна розробка й тести залишаються.
- `infra/production.compose.yaml` і
  `infra/Caddyfile` залишені як опис старого середовища. Production workflow їх не запускає.
  `infra/compose.yaml` і далі використовується для локальної розробки.
