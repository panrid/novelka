#!/bin/sh
# Deploy/update the dev test site beside production on the VPS.
# - Builds the dev-app container from /opt/novelka/app
# - Starts dev-app with .env.dev and resource limits (1 GB RAM, 0.5 CPU)
# - Waits for /api/health to answer ok
# - Reloads Caddy so dev.novelka.in.ua routes to dev-app
set -eu

cd /opt/novelka
prod_compose="docker compose --env-file .env.production -f infra/production.compose.yaml"
dev_compose="docker compose --env-file .env.dev -f infra/dev.compose.yaml"

echo "Building dev-app..."
$dev_compose build dev-app

echo "Starting dev-app..."
$dev_compose up -d --remove-orphans dev-app

echo "Waiting for dev-app /api/health..."
dev_id=$($dev_compose ps -q dev-app)
dev_ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$dev_id")
healthy=""
for _ in $(seq 1 90); do
    if $prod_compose exec -T caddy wget -qO- "http://$dev_ip:8080/api/health" 2>/dev/null | grep -q '"status":"ok"'; then
        healthy=yes
        break
    fi
    sleep 2
done

if [ -z "$healthy" ]; then
    echo "dev-app did not become healthy!" >&2
    $dev_compose logs --tail 80 dev-app >&2 || true
    exit 1
fi

echo "dev-app is healthy ($dev_ip). Reloading Caddy..."
$prod_compose exec -T caddy caddy reload --config /etc/caddy/Caddyfile >/dev/null

echo "Dev deployment complete:"
$dev_compose ps
