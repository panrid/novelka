#!/usr/bin/env bash
# Root-owned sudo entrypoint installed by setup-deploy.sh. The CI account is trusted
# to supply deployment code and privileged BuildKit manifests, hence has production trust.
set -euo pipefail
[[ $# -ge 1 && $# -le 2 && $1 =~ ^[0-9a-f]{40}$ ]] || exit 2
mode=${2:-deploy}
[[ $mode == deploy || $mode == --check || $mode == --build-only ]] || exit 2
release="/srv/novelka-releases/$1"
exec /usr/bin/bash "$release/infra/deploy.sh" "$release" "$1" "$mode"
