#!/bin/sh
# Server backups: the «backup» service in production.compose.yaml runs this (image postgres:17).
#
# Once a day (the first hourly check after midnight UTC) it saves the database
# (/backups/novelka-YYYY-MM-DD.dump, 14 days kept) and the pictures (/backups/media-YYYY-MM-DD.tgz,
# 7 days kept). A copy is written to «….part», checked, and only then renamed; a failed or suspicious
# copy is deleted, logged as «BACKUP FAILED: …» on stderr and recorded in the status files below, and
# tried again an hour later. A dump is good when pg_dump succeeded, it is at least
# BACKUP_DB_MIN_BYTES long and pg_restore can read its table of contents; a picture archive when tar
# succeeded and gzip can read it to the end.
#
# Status for Grafana (doc/deployment.md, «Резервні копії»), Prometheus text format, in
# /backups/status (the volume novelka_backups):
#   server.prom  — this script: novelka_backup_last_success_timestamp_seconds{part="db|media"},
#                  novelka_backup_last_failure_timestamp_seconds, novelka_backup_last_ok,
#                  novelka_backup_last_size_bytes;
#   offsite.prom — the owner's Mac after a copy reached iCloud (via «mark-offsite»):
#                  novelka_backup_offsite_last_success_timestamp_seconds{target="icloud"},
#                  novelka_backup_offsite_last_size_bytes.
# Each value is also kept as a plain number in /backups/status/state/<name>.
#
# Other commands (infra/backup-export.sh calls them through «docker exec»):
#   list                           good copies: «name bytes», one per line
#   sha256 NAME                    the copy's SHA-256
#   mark-offsite TARGET DUMP BYTES a copy of DUMP (BYTES in all) reached TARGET (now: icloud)
#   status                         print the status files
set -u

dir=/backups
status_dir=$dir/status
state=$status_dir/state
db_min_bytes=${BACKUP_DB_MIN_BYTES:-1000000}
mkdir -p "$state"

now() { date -u +%s; }
log() { echo "$(date -u +%FT%TZ) $*"; }
fail() { echo "$(date -u +%FT%TZ) BACKUP FAILED: $*" >&2; }

put() { printf '%s\n' "$2" > "$state/$1.tmp" && mv -f "$state/$1.tmp" "$state/$1"; }
get() {
    value=$(cat "$state/$1" 2>/dev/null) || value=0
    case "$value" in '' | *[!0-9]*) value=0 ;; esac
    echo "$value"
}

# Atomically replaces a .prom file: node_exporter reads only *.prom, never the .tmp beside it.
write_prom() {
    cat > "$status_dir/$1.tmp" && mv -f "$status_dir/$1.tmp" "$status_dir/$1"
}

write_server_status() {
    {
        echo '# HELP novelka_backup_last_success_timestamp_seconds Unix time of the last good server backup.'
        echo '# TYPE novelka_backup_last_success_timestamp_seconds gauge'
        for p in db media; do echo "novelka_backup_last_success_timestamp_seconds{part=\"$p\"} $(get $p.success)"; done
        echo '# HELP novelka_backup_last_failure_timestamp_seconds Unix time of the last failed server backup (0: none yet).'
        echo '# TYPE novelka_backup_last_failure_timestamp_seconds gauge'
        for p in db media; do echo "novelka_backup_last_failure_timestamp_seconds{part=\"$p\"} $(get $p.failure)"; done
        echo '# HELP novelka_backup_last_ok Whether the latest server backup attempt succeeded (1) or failed (0).'
        echo '# TYPE novelka_backup_last_ok gauge'
        for p in db media; do echo "novelka_backup_last_ok{part=\"$p\"} $(get $p.ok)"; done
        echo '# HELP novelka_backup_last_size_bytes Size of the last good server backup.'
        echo '# TYPE novelka_backup_last_size_bytes gauge'
        for p in db media; do echo "novelka_backup_last_size_bytes{part=\"$p\"} $(get $p.size)"; done
    } | write_prom server.prom
}

write_offsite_status() {
    {
        echo '# HELP novelka_backup_offsite_last_success_timestamp_seconds Unix time (server clock) the last copy reached off-site storage.'
        echo '# TYPE novelka_backup_offsite_last_success_timestamp_seconds gauge'
        echo "novelka_backup_offsite_last_success_timestamp_seconds{target=\"icloud\"} $(get icloud.success)"
        echo '# HELP novelka_backup_offsite_last_size_bytes Bytes fetched for the last off-site copy (database and pictures).'
        echo '# TYPE novelka_backup_offsite_last_size_bytes gauge'
        echo "novelka_backup_offsite_last_size_bytes{target=\"icloud\"} $(get icloud.size)"
    } | write_prom offsite.prom
}

size_of() { stat -c %s "$1" 2>/dev/null || echo 0; }

# Before this script there was no state: the newest copy already on disk counts as the last good one,
# so the first deploy does not look like a day without backups.
seed() {
    for p in db media; do
        [ -s "$state/$p.success" ] && continue
        case $p in db) pattern='novelka-*.dump' ;; media) pattern='media-*.tgz' ;; esac
        newest=$(find "$dir" -maxdepth 1 -name "$pattern" -size +0 | sort | tail -n 1)
        [ -n "$newest" ] || continue
        put $p.success "$(stat -c %Y "$newest")"
        put $p.size "$(size_of "$newest")"
        put $p.ok 1
    done
}

backup_db() {
    out=$dir/novelka-$1.dump
    part=$out.part
    rm -f "$part"
    if ! pg_dump -h postgres -U novelka -d novelka -Fc -f "$part"; then
        rm -f "$part"; fail "database: pg_dump failed"; return 1
    fi
    size=$(size_of "$part")
    if [ "$size" -lt "$db_min_bytes" ]; then
        rm -f "$part"; fail "database: the dump is only $size bytes (less than $db_min_bytes)"; return 1
    fi
    if ! pg_restore --list "$part" > /dev/null; then
        rm -f "$part"; fail "database: pg_restore cannot read the dump"; return 1
    fi
    mv -f "$part" "$out" || { rm -f "$part"; fail "database: cannot rename the dump"; return 1; }
    log "database saved: novelka-$1.dump ($size bytes)"
}

backup_media() {
    out=$dir/media-$1.tgz
    part=$out.part
    rm -f "$part"
    if ! tar czf "$part" -C /media .; then
        rm -f "$part"; fail "pictures: tar failed"; return 1
    fi
    if ! gzip -t "$part"; then
        rm -f "$part"; fail "pictures: the archive is damaged"; return 1
    fi
    size=$(size_of "$part")
    mv -f "$part" "$out" || { rm -f "$part"; fail "pictures: cannot rename the archive"; return 1; }
    log "pictures saved: media-$1.tgz ($size bytes)"
}

# record PART OUTCOME FILE
record() {
    if [ "$2" = ok ]; then
        put "$1.success" "$(now)"
        put "$1.size" "$(size_of "$3")"
        put "$1.ok" 1
    else
        put "$1.failure" "$(now)"
        put "$1.ok" 0
    fi
}

run() {
    day=$(date -u +%F)
    if [ ! -s "$dir/novelka-$day.dump" ]; then
        if backup_db "$day"; then record db ok "$dir/novelka-$day.dump"; else record db failed ""; fi
    fi
    if [ ! -s "$dir/media-$day.tgz" ]; then
        if backup_media "$day"; then record media ok "$dir/media-$day.tgz"; else record media failed ""; fi
    fi
    find "$dir" -maxdepth 1 -name 'novelka-*.dump' -mtime +14 -delete
    find "$dir" -maxdepth 1 -name 'media-*.tgz' -mtime +7 -delete
    write_server_status
}

valid_name() {
    case "$1" in
        novelka-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].dump) return 0 ;;
        media-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].tgz) return 0 ;;
    esac
    return 1
}

case "${1:-loop}" in
    loop)
        trap 'exit 0' TERM INT
        # Whatever an interrupted run left (as on 2026-10-16) is never a copy.
        rm -f "$dir"/*.part
        seed
        write_server_status
        [ -s "$state/icloud.success" ] && write_offsite_status
        while true; do
            run
            sleep 3600 &
            wait $!
        done
        ;;
    list)
        cd "$dir" || exit 1
        for f in novelka-*.dump media-*.tgz; do
            valid_name "$f" && [ -s "$f" ] && printf '%s %s\n' "$f" "$(size_of "$f")"
        done
        exit 0
        ;;
    sha256)
        valid_name "${2:-}" || { echo "bad name" >&2; exit 2; }
        sha256sum "$dir/$2" | cut -d ' ' -f 1
        ;;
    mark-offsite)
        [ "${2:-}" = icloud ] || { echo "unknown target" >&2; exit 2; }
        valid_name "${3:-}" || { echo "bad name" >&2; exit 2; }
        case "${4:-}" in '' | *[!0-9]*) echo "bad size" >&2; exit 2 ;; esac
        put icloud.success "$(now)"
        put icloud.size "$4"
        echo "$3" > "$state/icloud.dump"
        write_offsite_status
        log "off-site copy marked: $3 in icloud ($4 bytes)"
        ;;
    status)
        cat "$status_dir"/*.prom
        ;;
    *)
        echo "unknown command" >&2
        exit 2
        ;;
esac
