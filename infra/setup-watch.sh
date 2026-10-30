#!/usr/bin/env bash
# One-time server setup of the Telegram watch. The secrets stay in /etc/novelka-watch.env (root, 600) and
# are never part of the repository: put TELEGRAM_TOKEN= and TELEGRAM_CHAT_ID= there first.
set -euo pipefail
[[ $EUID == 0 ]] || { echo "Usage: sudo bash $0" >&2; exit 2; }
[[ -f /etc/novelka-watch.env ]] || { echo "Немає /etc/novelka-watch.env" >&2; exit 1; }
source_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
install -o root -g root -m 755 "$source_dir/novelka-watch.sh" /usr/local/sbin/novelka-watch
install -d -o root -g root -m 700 /var/lib/novelka-watch
chmod 600 /etc/novelka-watch.env

unit() { # name, description, calendar
    cat > "/etc/systemd/system/novelka-watch-$1.service" <<EOF
[Unit]
Description=Novelka server watch ($1)
[Service]
Type=oneshot
ExecStart=/usr/local/sbin/novelka-watch $1
Nice=10
EOF
    cat > "/etc/systemd/system/novelka-watch-$1.timer" <<EOF
[Unit]
Description=$2
[Timer]
OnCalendar=$3
Persistent=true
RandomizedDelaySec=60
[Install]
WantedBy=timers.target
EOF
}
unit check "Novelka server watch: hourly check" "hourly"
unit daily "Novelka server watch: daily report" "*-*-* 08:00:00 Europe/Kyiv"
systemctl daemon-reload
systemctl enable --now novelka-watch-check.timer novelka-watch-daily.timer
echo 'Watch ready: novelka-watch-check.timer (hourly), novelka-watch-daily.timer (08:00 Київ).'
