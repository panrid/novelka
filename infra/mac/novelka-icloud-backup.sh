#!/bin/bash
# Off-site copy of Novelka's backups, run by launchd on the owner's Mac every hour
# (infra/mac/ua.in.novelka.icloud-backup.plist). Once the server has a newer database dump than the
# one already in iCloud, it fetches that dump and the pictures of the same day over SSH, checks
# them, encrypts them with age for the owner's public key and puts them into iCloud Drive
# (Novelka/копії), keeps KEEP_DAYS days there and then marks the copy on the server, where Grafana
# reminds the owner if the mark is older than 2 days. Otherwise it does nothing.
#
# Settings: ~/.config/novelka-backup/config (see config.example). The private age key is never on
# this path: only the owner has it, for a restore. Log: ~/Library/Logs/novelka-icloud-backup.log.
# Install, restore: doc/deployment.md, «Резервні копії».
#
# macOS /bin/bash 3.2 and BSD tools: no GNU-only flags.
set -euo pipefail

export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
umask 077

config="${NOVELKA_BACKUP_CONFIG:-$HOME/.config/novelka-backup/config}"
state_dir="$HOME/.config/novelka-backup/state"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*"; }
die() { log "ПОМИЛКА: $*" >&2; exit 1; }

[ -r "$config" ] || die "немає налаштувань $config"
# shellcheck source=/dev/null
. "$config"

: "${NOVELKA_SSH_TARGET:=novelka@62.72.33.247}"
: "${NOVELKA_SSH_KEY:=$HOME/.ssh/novelka_icloud_backup}"
: "${AGE_RECIPIENT:?AGE_RECIPIENT (публічний ключ age1…) не задано в $config}"
: "${DEST:=$HOME/Library/Mobile Documents/com~apple~CloudDocs/Novelka/копії}"
: "${KEEP_DAYS:=30}"
: "${MIN_DB_BYTES:=1000000}"
: "${MAX_DUMP_AGE_HOURS:=50}"
: "${NOTIFY_AFTER_HOURS:=26}"

case "$AGE_RECIPIENT" in
    age1*) ;;
    *) die "AGE_RECIPIENT має бути публічним ключем age1…, а не приватним" ;;
esac
command -v age > /dev/null || die "немає age (brew install age)"

mkdir -p "$state_dir"
tmp=""
finished=""

# A failure is only logged while the last good copy is recent; once it is older than
# NOTIFY_AFTER_HOURS the Mac also shows a notification (Grafana sends its own after 2 days).
on_exit() {
    code=$?
    [ -n "$tmp" ] && rm -rf "$tmp"
    rmdir "$state_dir/lock" 2> /dev/null || true
    if [ "$code" -ne 0 ] && [ -z "$finished" ]; then
        last=0
        [ -f "$state_dir/last-success" ] && last=$(stat -f %m "$state_dir/last-success")
        if [ $(( $(date +%s) - last )) -gt $(( NOTIFY_AFTER_HOURS * 3600 )) ]; then
            osascript -e 'display notification "Копія в iCloud не вдалася — див. ~/Library/Logs/novelka-icloud-backup.log" with title "Новелка"' 2> /dev/null || true
        fi
    fi
}

# Only one run at a time (a long download may outlast the hour). A lock left by a crash for more
# than 6 hours is taken over.
if ! mkdir "$state_dir/lock" 2> /dev/null; then
    if [ $(( $(date +%s) - $(stat -f %m "$state_dir/lock") )) -lt 21600 ]; then
        exit 0
    fi
    log "старе блокування — забираю"
    touch "$state_dir/lock"
fi
trap on_exit EXIT

remote() {
    ssh -i "$NOVELKA_SSH_KEY" -o BatchMode=yes -o IdentitiesOnly=yes -o ConnectTimeout=20 \
        -o ServerAliveInterval=15 -o ServerAliveCountMax=4 "$NOVELKA_SSH_TARGET" "$*"
}

# Is this copy in iCloud already? An evicted file («Optimize Mac Storage») is «.NAME.icloud».
in_icloud() {
    [ -s "$DEST/$1.age" ] || [ -e "$DEST/.$1.age.icloud" ]
}

# Copies older than KEEP_DAYS go, judged by the date in the name (an evicted file keeps no useful
# mtime). Runs only when the newest copy is in place, so a Mac that slept for a month keeps them.
prune() {
    cutoff=$(date -j -v-"${KEEP_DAYS}"d +%Y-%m-%d)
    for f in "$DEST"/novelka-*.dump.age "$DEST"/media-*.tgz.age "$DEST"/.novelka-*.dump.age.icloud "$DEST"/.media-*.tgz.age.icloud; do
        [ -e "$f" ] || continue
        day=$(basename "$f" | sed -E -n 's/^\.?(novelka|media)-([0-9]{4}-[0-9]{2}-[0-9]{2})\..*/\2/p')
        [ -n "$day" ] || continue
        if [[ "$day" < "$cutoff" ]]; then
            rm -f "$f"
            log "видалено стару копію $(basename "$f")"
        fi
    done
}

# fetch NAME BYTES: downloads into $tmp and checks length and SHA-256 against the server.
fetch() {
    name=$1
    expected=$2
    remote get "$name" > "$tmp/$name" || die "не вдалося завантажити $name"
    got=$(stat -f %z "$tmp/$name")
    [ "$got" = "$expected" ] || die "$name: завантажено $got байт, а на сервері $expected"
    remote_sum=$(remote sha256 "$name") || die "не вдалося отримати контрольну суму $name"
    local_sum=$(shasum -a 256 "$tmp/$name" | awk '{print $1}')
    [ "$remote_sum" = "$local_sum" ] || die "$name: контрольна сума не збігається"
}

# mark DUMP BYTES: the mark on the server Grafana watches. Remembered here, so a mark that failed
# is set again on the next run although the copy is already in iCloud.
mark() {
    remote mark-offsite icloud "$1" "$2" > /dev/null || die "копія в iCloud, але позначку на сервері не поставлено"
    echo "$1" > "$state_dir/marked"
    touch "$state_dir/last-success"
}

# store NAME: encrypts $tmp/NAME into iCloud as NAME.age (via a hidden partial file, then rename).
store() {
    name=$1
    age -r "$AGE_RECIPIENT" -o "$tmp/$name.age" "$tmp/$name" || die "age не зашифрував $name"
    rm -f "$tmp/$name"
    [ -s "$tmp/$name.age" ] || die "$name.age порожній"
    cp "$tmp/$name.age" "$DEST/.$name.age.partial" || die "не вдалося записати в $DEST"
    [ "$(stat -f %z "$DEST/.$name.age.partial")" = "$(stat -f %z "$tmp/$name.age")" ] \
        || die "$name.age записався не повністю"
    mv -f "$DEST/.$name.age.partial" "$DEST/$name.age"
    rm -f "$tmp/$name.age"
}

listing=$(remote list) || die "сервер не відповів на «list» ($NOVELKA_SSH_TARGET)"
dump=$(printf '%s\n' "$listing" | awk '$1 ~ /^novelka-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]\.dump$/ {print $1}' | sort | tail -n 1)
[ -n "$dump" ] || die "на сервері немає жодної копії бази"
day=${dump#novelka-}
day=${day%.dump}

mkdir -p "$DEST" || die "немає доступу до $DEST"
if in_icloud "$dump"; then
    # Nothing new on the server yet (or only the mark is missing).
    if [ "$(cat "$state_dir/marked" 2> /dev/null || true)" != "$dump" ]; then
        bytes=$(printf '%s
' "$listing" | awk -v n="$dump" '$1 == n {print $2}')
        mark "$dump" "${bytes:-0}"
        log "позначку для $dump поставлено"
    fi
    prune
    finished=yes
    exit 0
fi

dump_time=$(date -j -u -f '%Y-%m-%d %H:%M:%S' "$day 00:00:00" +%s)
if [ $(( $(date +%s) - dump_time )) -gt $(( MAX_DUMP_AGE_HOURS * 3600 )) ]; then
    die "найсвіжіша копія бази на сервері — $dump, старша за $MAX_DUMP_AGE_HOURS год: копії на сервері не робляться"
fi

dump_bytes=$(printf '%s\n' "$listing" | awk -v n="$dump" '$1 == n {print $2}')
[ "$dump_bytes" -ge "$MIN_DB_BYTES" ] || die "$dump лише $dump_bytes байт (менше $MIN_DB_BYTES)"

media="media-$day.tgz"
media_bytes=$(printf '%s\n' "$listing" | awk -v n="$media" '$1 == n {print $2}')
if [ -z "$media_bytes" ]; then
    media=$(printf '%s\n' "$listing" | awk '$1 ~ /^media-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]\.tgz$/ {print $1}' | sort | tail -n 1)
    [ -n "$media" ] || die "на сервері немає жодної копії картинок"
    media_bytes=$(printf '%s\n' "$listing" | awk -v n="$media" '$1 == n {print $2}')
    log "картинок за $day немає, беру $media"
fi

tmp=$(mktemp -d "${TMPDIR:-/tmp}/novelka-backup.XXXXXX")
log "копіюю $dump ($dump_bytes байт) і $media ($media_bytes байт)"

fetch "$dump" "$dump_bytes"
[ "$(head -c 5 "$tmp/$dump")" = "PGDMP" ] || die "$dump — не копія pg_dump"
fetch "$media" "$media_bytes"
gzip -t "$tmp/$media" || die "$media пошкоджений"

# The pictures go first: the database copy is the sign that the day is done.
in_icloud "$media" || store "$media"
store "$dump"

total=$(( dump_bytes + media_bytes ))
mark "$dump" "$total"
log "готово: $dump.age і $media.age у $DEST"
prune
finished=yes
