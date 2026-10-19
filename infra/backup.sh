#!/bin/bash
# PostgreSQL 17 image. Publish the manifest last: only it makes a complete daily pair.
set -euo pipefail
umask 077
export TZ=UTC
backup_dir=${BACKUP_DIR:-/backups}
media_dir=${MEDIA_DIR:-/media}
mkdir -p "$backup_dir/status"
exec 9>"$backup_dir/.backup.lock"
flock -n 9 || exit 0
cd "$backup_dir"
work=''
cleanup() { [ -z "$work" ] || rm -rf -- "$work"; }
trap cleanup EXIT
trap 'exit 143' TERM
trap 'exit 130' INT

status() {
    local ok=$1 timestamp=$2
    printf 'novelka_backup_last_attempt_ok %s\nnovelka_backup_last_attempt_timestamp_seconds %s\n' \
        "$ok" "$timestamp" > status/server.prom.tmp
    if [ -f status/last-success ]; then
        printf 'novelka_backup_last_success_timestamp_seconds %s\n' "$(cat status/last-success)" >> status/server.prom.tmp
    else
        printf 'novelka_backup_last_success_timestamp_seconds 0\n' >> status/server.prom.tmp
    fi
    mv status/server.prom.tmp status/server.prom
}

pair() {
    local day=$1 db="novelka-$1.dump" media="media-$1.tgz" manifest="novelka-$1.sha256"
    if [ -s "$manifest" ]; then
        # Never silently accept a manifest for a different set of files.
        [ "$(wc -l < "$manifest")" -eq 2 ] || return 1
        [ "$(awk '{print $2}' "$manifest")" = "$(printf '%s\n%s' "$db" "$media")" ] || return 1
        sha256sum --check --status "$manifest" || return 1
        [ -s "$db" ] && [ -s "$media" ] || return 1
        return 0
    fi
    work=$(mktemp -d "$backup_dir/.pair-$day.XXXXXX") || return 1
    pg_dump -h "${PGHOST:-postgres}" -U "${PGUSER:-novelka}" -d "${PGDATABASE:-novelka}" -Fc -f "$work/$db" || return 1
    [ -s "$work/$db" ] || return 1
    pg_restore --list "$work/$db" > /dev/null || return 1
    tar czf "$work/$media" -C "$media_dir" . || return 1
    [ -s "$work/$media" ] || return 1
    gzip -t "$work/$media" || return 1
    (cd "$work" && sha256sum "$db" "$media" > "$manifest") || return 1
    mv "$work/$db" "$db" || return 1
    mv "$work/$media" "$media" || return 1
    mv "$work/$manifest" "$manifest" || return 1
    cleanup
    work=''
}

run() {
    local day timestamp
    day=$(date -u +%F)
    timestamp=$(date -u +%s)
    if pair "$day"; then
        # Rechecking the same pair must not make an old backup appear newer.
        if [ "$(cat status/last-day 2>/dev/null || true)" != "$day" ]; then
            printf '%s\n' "$timestamp" > status/last-success.tmp
            mv status/last-success.tmp status/last-success
            printf '%s\n' "$day" > status/last-day.tmp
            mv status/last-day.tmp status/last-day
        fi
        status 1 "$timestamp"
        echo "$(date -u +%FT%TZ) BACKUP OK: $day — база, картинки й SHA-256 готові"
        # Only names owned by this tool; pruning is never run after a failed pair.
        find . -maxdepth 1 -type f -name 'novelka-????-??-??.dump' -mtime +14 -delete
        find . -maxdepth 1 -type f \( -name 'media-????-??-??.tgz' -o -name 'novelka-????-??-??.sha256' \) -mtime +7 -delete
    else
        cleanup
        work=''
        status 0 "$timestamp"
        echo "$(date -u +%FT%TZ) BACKUP FAILED: $day — повну пару не створено або перевірку не пройдено" >&2
        return 1
    fi
}
case "${1:-loop}" in
    once) run ;;
    loop) while true; do run || true; sleep 3600 9>&- & wait $!; done ;;
    *) echo 'Використання: backup.sh [once|loop]' >&2; exit 2 ;;
esac
