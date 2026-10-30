#!/usr/bin/env bash
# Server-side watch of the Novelka VPS (the outside view is .github/workflows/uptime.yml).
#   novelka-watch check   hourly: tells Telegram only when a problem appears, changes or stays for 6 h
#   novelka-watch daily   once a day: a short status report, problems or not
# Secrets: /etc/novelka-watch.env (TELEGRAM_TOKEN, TELEGRAM_CHAT_ID), root-only. Installed by setup-watch.sh.
# Locally: DRY_RUN=1 sudo -E bash infra/novelka-watch.sh daily
set -uo pipefail

mode=${1:-check}
env_file=${WATCH_ENV:-/etc/novelka-watch.env}
state=${WATCH_STATE:-/var/lib/novelka-watch/alerts}
export KUBECONFIG=${KUBECONFIG:-/etc/rancher/k3s/k3s.yaml}
if [ -z "${DRY_RUN:-}" ]; then
    # shellcheck disable=SC1090
    source "$env_file"
fi
host=novelka.in.ua
ns=novelka-prod

send() {
    if [ -n "${DRY_RUN:-}" ]; then
        echo "Telegram (не надсилається):"
        echo "$1"
        return 0
    fi
    local reply
    reply=$(curl --silent --show-error --retry 2 --retry-all-errors --max-time 20 \
        --data-urlencode "chat_id=$TELEGRAM_CHAT_ID" --data-urlencode "text=$1" \
        -d disable_web_page_preview=true "https://api.telegram.org/bot$TELEGRAM_TOKEN/sendMessage")
    grep -q '"ok":true' <<< "$reply" || { echo "Telegram не прийняв повідомлення: $reply" >&2; return 1; }
}

# Facts, one per line "name|value".
disk_pct=$(df --output=pcent / | tail -1 | tr -dc '0-9')
mem_avail_mb=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
mem_total_mb=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
swap_used_mb=$(awk '/SwapTotal/ {t=$2} /SwapFree/ {f=$2} END {print int((t-f)/1024)}' /proc/meminfo)
load1=$(cut -d' ' -f1 /proc/loadavg)
cpus=$(nproc)
oom=$(journalctl -k --since '-1 hour' --no-pager 2> /dev/null | grep -ci 'out of memory\|oom-kill')
failed_units=$(systemctl --failed --no-legend 2> /dev/null | awk '{print $2}' | paste -sd, -)
pods=$(kubectl -n "$ns" get pods --no-headers 2> /dev/null)
pods_bad=$(awk '{split($2,r,"/"); if ($3 != "Running" || r[1] != r[2]) print $1" "$3}' <<< "$pods" | paste -sd, -)
restarts_day=$(kubectl -n "$ns" get pods -o jsonpath='{range .items[*]}{.metadata.name}{" "}{range .status.containerStatuses[*]}{.restartCount}{" "}{.lastState.terminated.finishedAt}{"\n"}{end}{end}' 2> /dev/null \
    | awk -v since="$(date -u -d '-24 hours' +%FT%TZ)" 'NF >= 3 && $3 > since {print $1}' | sort -u | paste -sd, -)
cert_end=$(echo | timeout 15 openssl s_client -servername "$host" -connect "$host:443" 2> /dev/null | openssl x509 -noout -enddate 2> /dev/null | cut -d= -f2)
cert_days=
[ -n "$cert_end" ] && cert_days=$((($(date -d "$cert_end" +%s) - $(date +%s)) / 86400))
[ -z "$pods" ] && pods_bad="кластер не відповідає"

problems=()
((disk_pct >= 85)) && problems+=("Диск заповнений на ${disk_pct}%")
((mem_avail_mb * 100 / mem_total_mb < 10)) && problems+=("Мало пам'яті: доступно ${mem_avail_mb} МБ із ${mem_total_mb}")
((swap_used_mb >= 1500)) && problems+=("Swap вже використано ${swap_used_mb} МБ")
((oom > 0)) && problems+=("OOM-killer за останню годину: ${oom}")
[ -n "$failed_units" ] && problems+=("Збійні сервіси systemd: $failed_units")
[ -n "$pods_bad" ] && problems+=("Pod'и не готові: $pods_bad")
[ -z "$cert_days" ] && problems+=("Не вдалось прочитати сертифікат $host")
[ -n "$cert_days" ] && ((cert_days < 14)) && problems+=("Сертифікат $host спливає через ${cert_days} дн.")
awk -v l="$load1" -v c="$cpus" 'BEGIN {exit !(l > c * 2)}' && problems+=("Навантаження ${load1} на ${cpus} ядра")

case $mode in
    check)
        now=$(date +%s)
        sig=$(printf '%s\n' "${problems[@]:-}" | sort | sha1sum | cut -d' ' -f1)
        old_sig=$(sed -n 's/^sig=//p' "$state" 2> /dev/null)
        old_at=$(sed -n 's/^at=//p' "$state" 2> /dev/null)
        if [ ${#problems[@]} -eq 0 ]; then
            [ -f "$state" ] && send "🟢 Сервер знову в нормі" && rm -f "$state"
            exit 0
        fi
        if [ "$sig" != "$old_sig" ] || ((now - ${old_at:-0} >= 21600)); then
            text="🟠 Сервер Новелки: проблеми"$'\n'$(printf '• %s\n' "${problems[@]}")
            send "$text" && printf 'sig=%s\nat=%s\n' "$sig" "$now" > "$state"
        fi
        ;;
    daily)
        {
            if [ ${#problems[@]} -eq 0 ]; then echo "🟢 Сервер Новелки: усе гаразд"; else echo "🟠 Сервер Новелки: є проблеми"; printf '• %s\n' "${problems[@]}"; fi
            echo
            echo "Диск: ${disk_pct}% · Пам'ять: доступно ${mem_avail_mb} з ${mem_total_mb} МБ · Swap: ${swap_used_mb} МБ"
            echo "Навантаження: ${load1} (ядер ${cpus}) · Сертифікат: ${cert_days:-?} дн."
            echo "Pod'и: $(awk '{print $1" "$3}' <<< "$pods" | paste -sd';' -)"
            echo "Перезапуски за добу: ${restarts_day:-немає}"
            echo "Помилки застосунку за добу: $(kubectl -n "$ns" logs deploy/novelka --since=24h 2> /dev/null | grep -c ' ERROR ')"
        } > /tmp/novelka-watch.$$
        send "$(cat /tmp/novelka-watch.$$)"
        rc=$?
        rm -f /tmp/novelka-watch.$$
        exit $rc
        ;;
    *)
        echo "Usage: $0 check|daily" >&2
        exit 2
        ;;
esac
