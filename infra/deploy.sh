#!/bin/sh
# A deploy without a pause for readers: the new version starts beside the old one, and the old
# one stops only once the new one answers. Caddy sends every new connection to whichever copy
# listens («app» resolves to both while both run). The old copy then finishes a model's answer
# already on its way (up to 180 s) and leaves; a run goes on in the new one.
#
# If the new version never becomes healthy, it is removed and the old one keeps the site.
#
# Run on the server from /opt/novelka (the deploy workflow does it).
set -eu

cd /opt/novelka
compose="docker compose --env-file .env.production -f infra/production.compose.yaml"

# The first version of the site ran as the Compose project «infra»; it gives way once.
docker rm -f infra-app-1 infra-caddy-1 infra-postgres-1 2>/dev/null || true

$compose build app
$compose up -d --no-deps --remove-orphans postgres backup caddy

old=$($compose ps -q app)
if [ -z "$old" ]; then
    $compose up -d --no-deps app
    old=""
else
    count=$(echo "$old" | wc -l)
    $compose up -d --no-deps --no-recreate --scale app=$((count + 1)) app
fi
new=$($compose ps -q app | grep -v -x -F "${old:-none}" || true)
if [ -z "$new" ]; then
    echo "The new version did not start." >&2
    exit 1
fi

ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$new")
echo "New version: $new at $ip, waiting until it answers…"
healthy=""
for _ in $(seq 1 90); do
    if $compose exec -T caddy wget -qO- "http://$ip:8080/api/health" 2>/dev/null | grep -q '"status":"ok"'; then
        healthy=yes
        break
    fi
    sleep 2
done
if [ -z "$healthy" ]; then
    echo "The new version did not become healthy; the old one stays." >&2
    docker logs --tail 80 "$new" >&2 || true
    docker rm -f "$new" >/dev/null
    exit 1
fi

# Caddy may have a changed Caddyfile.
$compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null

for container in $old; do
    echo "Stopping the old version $container (it finishes a model's answer on its way)…"
    docker stop -t 180 "$container" >/dev/null
    docker rm "$container" >/dev/null
done

$compose ps
