#!/usr/bin/env bash
# Is the site up (uptime.yml)? Telegram hears only about a change: one message when the site stops
# answering, one when it is back. The previous state is read from $STATE (missing = the site was up)
# and, on a change, written there after Telegram accepted the message; changed=true in $GITHUB_OUTPUT
# tells the workflow to keep it. A down site is not a failure: the run stays green, so GitHub does not
# email every 5 minutes. The run fails only if the check itself broke (Telegram refused the message):
# the state stays as it was and the next run tries again.
#
# ADDRESS replaces https://novelka.in.ua for a manual check of the alarm; its messages say so.
# Locally: STATE=/tmp/s DRY_RUN=1 ADDRESS=https://127.0.0.1:9 bash .github/scripts/uptime.sh
set -uo pipefail

site=${ADDRESS:-https://novelka.in.ua}
site=${site%/}
: "${STATE:?}" "${GITHUB_OUTPUT:=/dev/null}" "${GITHUB_STEP_SUMMARY:=/dev/null}"
now=$(date +%s)

# Kyiv time of a Unix timestamp, in the given date format (GNU date on the runner, BSD date on a Mac).
kyiv() {
    if date -d @0 > /dev/null 2>&1; then TZ=Europe/Kyiv date -d "@$1" "+$2"; else TZ=Europe/Kyiv date -r "$1" "+$2"; fi
}

# "≈ 25 хв", "≈ 2 год 10 хв", "≈ 1 д 3 год".
duration() {
    local minutes=$((($1 + 30) / 60))
    if ((minutes < 60)); then
        echo "≈ $((minutes < 1 ? 1 : minutes)) хв"
    elif ((minutes < 1440)); then
        local m=$((minutes % 60))
        echo "≈ $((minutes / 60)) год$( ((m)) && echo " $m хв")"
    else
        local h=$((minutes % 1440 / 60))
        echo "≈ $((minutes / 1440)) д$( ((h)) && echo " $h год")"
    fi
}

# Fetches $site$1 with the same retries as before (a single network blip is not an outage). Prints
# nothing when the page is fine, otherwise what went wrong in plain words.
probe() {
    local path=$1 body code rc
    body=$(mktemp)
    code=$(curl --fail --silent --show-error --retry 3 --retry-delay 20 --retry-all-errors --max-time 20 \
        -o "$body" -w '%{http_code}' "$site$path" 2> "$body.err")
    rc=$?
    cat "$body.err" >&2
    local name=$path
    [ "$path" = / ] && name=головна
    case $rc in
        0)
            if [ "$path" = /api/health ] && ! grep -q '"status":"ok"' "$body"; then
                echo "$name відповідає без «ok»"
            fi ;;
        22)
            case $code in
                500) echo "$name — 500 (помилка сервера; можливо, недоступна база)" ;;
                502 | 504) echo "$name — $code (застосунок за проксі не відповідає)" ;;
                503) echo "$name — 503 (сервіс недоступний)" ;;
                *) echo "$name — $code" ;;
            esac ;;
        28) echo "$name не відповідає за 20 с" ;;
        6) echo "адреса не знаходиться (DNS)" ;;
        7) echo "сервер не приймає з'єднання" ;;
        35 | 51 | 53 | 54 | 58 | 59 | 60 | 77 | 80 | 82 | 83 | 90 | 91) echo "помилка HTTPS (сертифікат чи з'єднання)" ;;
        52) echo "$name: сервер закрив з'єднання без відповіді" ;;
        56) echo "$name: з'єднання обірвалось" ;;
        *) echo "$name: помилка з'єднання (curl $rc)" ;;
    esac
    rm -f "$body" "$body.err"
}

# Sends $1 to the owner; succeeds only if Telegram accepted it.
send() {
    local text=$1
    [ -n "${ADDRESS:-}" ] && text="🧪 Перевірка тривоги ($site)"$'\n'"$text"
    if [ -n "${DRY_RUN:-}" ]; then
        echo "Telegram (не надсилається): $text"
        return 0
    fi
    if [ -z "${TELEGRAM_TOKEN:-}" ] || [ -z "${TELEGRAM_CHAT_ID:-}" ]; then
        echo "::error::Немає секретів UPTIME_TELEGRAM_TOKEN і UPTIME_TELEGRAM_CHAT_ID — тривогу не надіслано"
        return 1
    fi
    local reply
    reply=$(curl --silent --show-error --retry 2 --retry-all-errors --max-time 20 \
        --data-urlencode "chat_id=$TELEGRAM_CHAT_ID" --data-urlencode "text=$text" \
        -d disable_web_page_preview=true "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage")
    if ! grep -q '"ok":true' <<< "$reply"; then
        echo "::error::Telegram не прийняв повідомлення: $(grep -o '"description":"[^"]*"' <<< "$reply")"
        return 1
    fi
}

field() {
    [ -f "$STATE" ] && sed -n "s/^$1=//p" "$STATE" | head -1
}

was=$(field status)
was=${was:-up}
since=$(field since)

reason=$(probe /api/health)
[ -z "$reason" ] && reason=$(probe /)
if [ -z "$reason" ]; then is=up; else is=down; fi

summary() {
    echo "$1" | tee -a "$GITHUB_STEP_SUMMARY"
}

if [ "$is" = "$was" ]; then
    echo "changed=false" >> "$GITHUB_OUTPUT"
    if [ "$is" = up ]; then
        summary "🟢 $site працює."
    else
        summary "🔴 $site не відповідає з $(kyiv "${since:-$now}" '%d.%m %H:%M') (Київ), $(duration $((now - ${since:-$now}))): $reason. Тривогу вже надіслано — тепер тиша."
        echo "::warning::Сайт не відповідає: $reason"
    fi
    exit 0
fi

if [ "$is" = down ]; then
    run=${GITHUB_RUN_ID:+$'\n'"${GITHUB_SERVER_URL:-https://github.com}/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"}
    send "🔴 Новелка не відповідає з $(kyiv "$now" '%H:%M') (Київ): $reason$run" || exit 1
    printf 'status=down\nsince=%s\nreason=%s\n' "$now" "$reason" > "$STATE"
    summary "🔴 $site не відповідає: $reason. Тривогу надіслано."
    echo "::warning::Сайт не відповідає: $reason"
else
    send "🟢 Новелка знову працює, не відповідала $(duration $((now - ${since:-$now})))" || exit 1
    printf 'status=up\nsince=%s\n' "$now" > "$STATE"
    summary "🟢 $site знову працює. Повідомлення надіслано."
fi
echo "changed=true" >> "$GITHUB_OUTPUT"
