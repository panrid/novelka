#!/bin/bash
# Real pg_dump/pg_restore + age + rclone local backend. Only disposable fixture data.
set -euo pipefail
here=$(cd "$(dirname "$0")/../.." && pwd)
tmp=$(mktemp -d)
container="novelka-backup-test-$$"
image="novelka-offsite-test:$$"
cleanup() {
    docker rm -f "$container" >/dev/null 2>&1 || true
    docker run --rm --entrypoint sh -v "$tmp:/cleanup" "$image" -c 'find /cleanup -mindepth 1 -delete' >/dev/null 2>&1 || true
    docker image rm "$image" >/dev/null 2>&1 || true
    rm -rf "$tmp"
}
trap cleanup EXIT
mkdir -p "$tmp/backups" "$tmp/media" "$tmp/state" "$tmp/work" "$tmp/config" "$tmp/cloud"
printf 'fixture image bytes\n' > "$tmp/media/example.txt"
docker build -t "$image" "$here/infra/offsite"
docker run -d --name "$container" -e POSTGRES_PASSWORD=fixture-only -e POSTGRES_USER=novelka \
    -e POSTGRES_DB=novelka -e PGPASSWORD=fixture-only \
    -v "$tmp/backups:/backups" -v "$tmp/media:/media:ro" \
    -v "$here/infra/backup.sh:/backup.sh:ro" postgres:17 >/dev/null
for _ in $(seq 1 60); do
    if docker exec "$container" pg_isready -U novelka >/dev/null 2>&1; then break; fi
    sleep 1
done
docker exec "$container" psql -U novelka -d novelka -v ON_ERROR_STOP=1 -c \
    "CREATE TABLE restoration_fixture (value text); INSERT INTO restoration_fixture VALUES ('перевірено');" >/dev/null
docker exec -e PGHOST=127.0.0.1 "$container" bash /backup.sh once
docker run --rm --entrypoint age-keygen -v "$tmp/config:/config" "$image" -o /config/identity.txt
docker run --rm --entrypoint age-keygen -v "$tmp/config:/config:ro" "$image" -y /config/identity.txt > "$tmp/config/recipient.txt"
run_worker() {
    docker run --rm -e DRIVE_ROOT=/cloud -e RCLONE_CONFIG=/tmp/empty.conf \
        -v "$tmp/backups:/backups:ro" -v "$tmp/state:/state" -v "$tmp/work:/work" \
        -v "$tmp/config/recipient.txt:/config/recipient.txt:ro" -v "$tmp/cloud:/cloud" "$image" once
}
run_worker
run_worker
[ "$(find "$tmp/cloud" -name '*.age' | wc -l)" -eq 1 ]
day=$(date -u +%F)
name="novelka-drive-v1-$day.tar.age"
docker run --rm --entrypoint age -v "$tmp/config:/config:ro" -v "$tmp/cloud:/cloud:ro" \
    -v "$tmp/work:/work" "$image" -d -i /config/identity.txt -o /work/pair.tar "/cloud/$name"
tar xf "$tmp/work/pair.tar" -C "$tmp/work"
(cd "$tmp/work" && sha256sum --check SHA256SUMS)
docker exec "$container" createdb -U novelka restored_fixture
docker exec -i "$container" pg_restore -U novelka -d restored_fixture --exit-on-error --no-owner --no-privileges \
    < "$tmp/work/novelka-$day.dump"
[ "$(docker exec "$container" psql -U novelka -d restored_fixture -Atc 'SELECT value FROM restoration_fixture')" = 'перевірено' ]
mkdir "$tmp/work/restored-media"
tar xzf "$tmp/work/media-$day.tgz" -C "$tmp/work/restored-media"
cmp "$tmp/media/example.txt" "$tmp/work/restored-media/example.txt"
echo 'OK: справжні pg_dump/pg_restore, age, rclone local, повторний запуск і розпакування fixture'
