#!/bin/sh
# The only thing the owner's Mac may do over SSH: its key in ~novelka/.ssh/authorized_keys is
#   restrict,command="sh /opt/novelka/infra/backup-export.sh" ssh-ed25519 AAAA… novelka-icloud-backup
# so whatever it asks for arrives here in SSH_ORIGINAL_COMMAND, and only these commands run:
#   list                              good copies in novelka_backups: «name bytes», one per line
#   get NAME                          the copy itself, on stdout
#   sha256 NAME                       its SHA-256
#   mark-offsite icloud DUMP BYTES    a copy of DUMP has reached iCloud (status for Grafana)
#   status                            the status files (Prometheus text)
# NAME is novelka-YYYY-MM-DD.dump or media-YYYY-MM-DD.tgz; nothing else can be read, nothing but the
# status can be written. The work is done by infra/backup.sh inside the «backup» container.
# See doc/deployment.md, «Резервні копії».
set -eu
set -f

container=${NOVELKA_BACKUP_CONTAINER:-novelka-backup-1}

# shellcheck disable=SC2086 # split into words on purpose; globbing is off
set -- ${SSH_ORIGINAL_COMMAND:-}

valid_name() {
    case "${1:-}" in
        novelka-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].dump) return 0 ;;
        media-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].tgz) return 0 ;;
    esac
    echo "bad name" >&2
    exit 2
}

case "${1:-} $#" in
    "list 1" | "status 1")
        exec docker exec "$container" sh /backup.sh "$1"
        ;;
    "get 2")
        valid_name "$2"
        exec docker exec "$container" cat "/backups/$2"
        ;;
    "sha256 2")
        valid_name "$2"
        exec docker exec "$container" sh /backup.sh sha256 "$2"
        ;;
    "mark-offsite 4")
        [ "$2" = icloud ] || { echo "unknown target" >&2; exit 2; }
        valid_name "$3"
        case "$4" in '' | *[!0-9]*) echo "bad size" >&2; exit 2 ;; esac
        exec docker exec "$container" sh /backup.sh mark-offsite "$2" "$3" "$4"
        ;;
    *)
        echo "Allowed: list | get NAME | sha256 NAME | mark-offsite icloud DUMP BYTES | status" >&2
        exit 2
        ;;
esac
