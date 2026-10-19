#!/bin/sh
# Puts one build onto one site of the server (doc/deployment.md):
#
#   sh /opt/novelka/builds/<commit>/infra/deploy.sh dev  <commit>   the test site, dev.novelka.in.ua
#   sh /opt/novelka/builds/<commit>/infra/deploy.sh prod <commit>   the production site, novelka.in.ua
#
# The deploy workflow uploads the JAR CI tested and this folder to /opt/novelka/builds/<commit>/ and
# runs it. The image «novelka:<commit>» is built from that JAR once; each site runs it under its own
# tag («novelka:dev», «novelka:prod»), so both sites run the very same image.
#
# A deploy without a pause for readers: the new version starts beside the old one, and the old
# one stops only once the new one answers. Caddy sends every new connection to whichever copy
# listens (the service name resolves to both while both run). The old copy then finishes a model's
# answer already on its way (up to 180 s on production) and leaves; a run goes on in the new one.
#
# If the new version never becomes healthy, it is removed and the old one keeps the site, with its
# image tag and Compose files as they were.
set -eu

root=/opt/novelka
mode=${1:-}
sha=${2:-}

fail() {
    echo "$*" >&2
    exit 1
}

case $sha in
    *[!0-9a-f]* | "") fail "Usage: deploy.sh dev|prod <full commit hash>" ;;
esac
[ ${#sha} -eq 40 ] || fail "A full commit hash (40 characters) is needed, not «$sha»."

case $mode in
    dev)
        site=dev
        project=novelka-dev
        service=dev-app
        env_file=$root/.env.dev
        dir=$root/infra-dev
        file=dev.compose.yaml
        grace=30
        ;;
    prod)
        site=prod
        project=novelka
        service=app
        env_file=$root/.env.production
        dir=$root/infra
        file=production.compose.yaml
        grace=180
        ;;
    *) fail "Usage: deploy.sh dev|prod <full commit hash>" ;;
esac

build=$root/builds/$sha
[ -f "$build/app/novelka.jar" ] || fail "No build $sha on the server ($build); it comes with a push to main."

mkdir -p "$root/dev-access"
if [ "$site" = dev ]; then
    # Not configured yet: no deploy at all rather than an open test site.
    [ -f "$env_file" ] || fail "No $env_file: set up the test site first (doc/deployment.md, «Тестовий сайт»)."
    ls "$root"/dev-access/*.caddy >/dev/null 2>&1 \
        || fail "No $root/dev-access/*.caddy: the test site would be open to everyone (doc/deployment.md, «Тестовий сайт»)."
fi

# One deploy on the server at a time (the test and the production site share PostgreSQL and Caddy).
exec 9>"$root/.deploy.lock"
if command -v flock >/dev/null 2>&1; then
    flock 9
fi

image=novelka:$sha
if ! docker image inspect "$image" >/dev/null 2>&1; then
    echo "Building $image…"
    docker build -q -t "$image" --label "org.opencontainers.image.revision=$sha" "$build/app" >/dev/null
fi

# What to put back if the new version fails: the site's image tag and its Compose files.
previous_image=$(docker image inspect -f '{{.Id}}' "novelka:$site" 2>/dev/null || true)
rm -rf "$dir.previous"
if [ -d "$dir" ]; then
    cp -a "$dir" "$dir.previous"
fi
mkdir -p "$dir"
# cp writes into the same files: Caddy keeps seeing its Caddyfile through the bind mount.
cp "$build"/infra/* "$dir/"
docker tag "$image" "novelka:$site"

put_back() {
    if [ -n "$previous_image" ]; then
        docker tag "$previous_image" "novelka:$site"
    else
        docker image rm "novelka:$site" >/dev/null 2>&1 || true
    fi
    if [ -d "$dir.previous" ]; then
        cp "$dir.previous"/* "$dir/"
    fi
}

compose="docker compose -p $project --env-file $env_file -f $dir/$file"

if [ "$site" = prod ]; then
    # The first version of the site ran as the Compose project «infra»; it gives way once.
    docker rm -f infra-app-1 infra-caddy-1 infra-postgres-1 2>/dev/null || true
    $compose up -d --no-deps --remove-orphans postgres backup mail caddy
else
    $compose up -d --no-deps --remove-orphans dev-mail
fi

old=$($compose ps -q "$service")
if [ -z "$old" ]; then
    $compose up -d --no-deps "$service"
    old=""
else
    count=$(echo "$old" | wc -l)
    $compose up -d --no-deps --no-recreate --scale "$service=$((count + 1))" "$service"
fi
new=$($compose ps -q "$service" | grep -v -x -F "${old:-none}" || true)
if [ -z "$new" ]; then
    put_back
    fail "The new version did not start."
fi

caddy=$(docker ps -q -f label=com.docker.compose.project=novelka -f label=com.docker.compose.service=caddy | head -n 1)
[ -n "$caddy" ] || fail "Caddy is not running."
ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$new")
echo "New version of $site ($sha): $new at $ip, waiting until it answers…"
healthy=""
for _ in $(seq 1 90); do
    if docker exec "$caddy" wget -qO- "http://$ip:8080/api/health" 2>/dev/null | grep -q '"status":"ok"'; then
        healthy=yes
        break
    fi
    sleep 2
done
if [ -z "$healthy" ]; then
    echo "The new version did not become healthy; the old one stays." >&2
    docker logs --tail 80 "$new" >&2 || true
    docker rm -f "$new" >/dev/null
    put_back
    exit 1
fi

if [ "$site" = prod ]; then
    # Caddy may have a changed Caddyfile.
    docker exec "$caddy" caddy reload --config /etc/caddy/Caddyfile >/dev/null
fi

for container in $old; do
    echo "Stopping the old version $container (it finishes a model's answer on its way)…"
    docker stop -t "$grace" "$container" >/dev/null
    docker rm "$container" >/dev/null
done

echo "$sha $(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$root/$site.history"

# Builds and images kept: the 10 newest, and the last 5 of each site (what a rollback returns to).
keep=$(
    ls -1t "$root/builds" | head -n 10
    for s in dev prod; do
        if [ -f "$root/$s.history" ]; then
            cut -d ' ' -f 1 "$root/$s.history" | tail -n 5
        fi
    done
)
for gone in $(ls -1 "$root/builds"); do
    if ! echo "$keep" | grep -q -x -F "$gone"; then
        rm -rf "${root:?}/builds/$gone"
        docker image rm "novelka:$gone" >/dev/null 2>&1 || true
    fi
done

$compose ps
