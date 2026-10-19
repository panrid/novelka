#!/bin/sh
# Starts or updates metrics, logs and alerts (infra/monitoring/compose.yaml) after the site's deploy.
# Nothing happens until the owner has made /opt/novelka/.env.monitoring (doc/deployment.md, «Метрики,
# журнали й тривоги»), so a server without it deploys exactly as before.
set -eu

cd /opt/novelka
if [ ! -f .env.monitoring ]; then
    echo "Monitoring: no /opt/novelka/.env.monitoring yet, skipped (doc/deployment.md)."
    exit 0
fi

docker compose --env-file .env.production --env-file .env.monitoring -f infra/monitoring/compose.yaml \
    up -d --remove-orphans
# Configuration files are read only on start, and `up` does not see them change; each is back in
# seconds, and Alloy keeps the logs meanwhile.
docker compose --env-file .env.production --env-file .env.monitoring -f infra/monitoring/compose.yaml \
    restart prometheus loki alloy grafana
