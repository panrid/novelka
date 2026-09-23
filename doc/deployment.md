# Локальне та production-середовище

## Середовища

`local` — розробка на MacBook: PostgreSQL запускається через
`infra/compose.yaml`, сайт — `./novelka-web`, API доступне на `127.0.0.1:8080`.

`production` — VPS `62.72.33.247`: PostgreSQL, Spring Boot і Caddy працюють у
`infra/production.compose.yaml`. Caddy завершує HTTPS і передає запити на app.
Публічна адреса — `https://novelka.panrid.space`.

## DNS

У панелі DNS домену `panrid.space` створіть запис:

```text
Type: A
Name: novelka
Value: 62.72.33.247
TTL: 300 або Auto
```

Перевірка з локальної машини:

```sh
dig +short novelka.panrid.space
```

Потрібно дочекатися, поки відповідь міститиме `62.72.33.247`.

## Підготовка VPS

Команди виконуються під root на Ubuntu. Користувач створюється один раз:

```sh
adduser novelka
usermod -aG sudo novelka
mkdir -p /home/novelka/.ssh
chmod 700 /home/novelka/.ssh
nano /home/novelka/.ssh/authorized_keys
chmod 600 /home/novelka/.ssh/authorized_keys
chown -R novelka:novelka /home/novelka/.ssh
apt update
apt install -y ca-certificates curl git
curl -fsSL https://get.docker.com | sh
usermod -aG docker novelka
mkdir -p /opt/novelka
chown -R novelka:novelka /opt/novelka
```

В `authorized_keys` вставте публічний ключ розробника. Після повторного SSH-входу
під `novelka` перевірте `docker compose version`.

Відкрийте firewall-порти:

```sh
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
```

Створіть production-секрет:

```sh
sudo -u novelka sh -c 'openssl rand -base64 36 > /tmp/novelka-db-password'
sudo -u novelka sh -c 'printf "NOVELKA_DB_PASSWORD=%s\\n" "$(cat /tmp/novelka-db-password)" > /opt/novelka/.env.production'
rm /tmp/novelka-db-password
chmod 600 /opt/novelka/.env.production
```

`.env.production` не додається до GitHub і не передається workflow.

Перед деплоєм версії з акаунтами також задайте `NOVELKA_OWNER_USERNAME` і
`NOVELKA_OWNER_PASSWORD` у цьому файлі, щоб створити власника при запуску.
Для вебперекладу потрібен `OPENROUTER_API_KEY`. Значення вводьте редактором у
env-файл на VPS, не в workflow. Деталі й правила видалення bootstrap-пароля:
[Акаунти та майстерня](accounts-and-management.md#створити-власника).
Для показу балансу акаунта у налаштуваннях власника необовʼязково додайте
`OPENROUTER_MANAGEMENT_KEY` у той самий env-файл. Це окремий ключ OpenRouter;
без нього переклад працює, але баланс не відображається. Після зміни env-файлу
перезапустіть контейнер застосунку.
Production Compose передає ці змінні app та вмикає Secure session cookie.
Міграція V3 додає акаунти, правки, налаштування, аудит і чергу; V4 — сповіщення
та стан прочитання для акаунтів. V4 застосовується автоматично при запуску і не
надсилає сповіщень про вже наявні переклади. Перед оновленням
збережіть резервну копію PostgreSQL; відкат JAR не видаляє міграцію чи нові дані.

## GitHub Actions

У репозиторії `panrid/novelka` створіть GitHub Actions secrets:

```text
DEPLOY_HOST=62.72.33.247
DEPLOY_USER=novelka
DEPLOY_SSH_KEY=<приватний deploy-ключ>
```

Ключ створюється локально:

```sh
ssh-keygen -t ed25519 -C github-actions-novelka -f ~/.ssh/novelka_deploy
cat ~/.ssh/novelka_deploy.pub
```

Публічний ключ додайте в `/home/novelka/.ssh/authorized_keys` на VPS.
Приватний ключ додайте тільки в `DEPLOY_SSH_KEY`.

Workflow `.github/workflows/deploy-production.yml` запускається після push у `main`
або вручну через `Actions → Deploy production → Run workflow`. Він запускає тести,
збирає JAR і frontend, передає release-файли на VPS, виконує Docker Compose та
перевіряє `https://novelka.panrid.space/`.

## Важливі межі

- Workflow не містить пароль PostgreSQL і OpenRouter-ключ.
- PostgreSQL не публікує порт у мережу VPS.
- Caddy зберігає сертифікати у Docker volume.
- Перед першим production-деплоєм DNS, порти 80/443 і SSH мають бути доступними.
- Rollback виконується повторним запуском workflow з попереднього commit; окремої
  автоматичної міграції вниз немає.
