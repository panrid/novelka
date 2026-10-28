#!/usr/bin/env bash
# Run on the single-node k3s server as root. CI supplies a tested JAR.
set -Eeuo pipefail
umask 077

usage() {
    echo "Usage: $0 RELEASE_DIRECTORY COMMIT_SHA [--check|--build-only]" >&2
    exit 2
}
[[ $# -ge 2 && $# -le 3 ]] || usage
release=$(realpath "$1")
revision=$2
mode=${3:-deploy}
[[ $revision =~ ^[0-9a-f]{40}$ ]] || usage
[[ $mode == deploy || $mode == --check || $mode == --build-only ]] || usage
[[ $EUID == 0 ]] || { echo 'Run as root (or through sudo -n).' >&2; exit 1; }
for executable in k3s kubectl python3 curl flock tar; do command -v "$executable" >/dev/null; done
for file in novelka.jar Dockerfile infra/k8s/buildkit.yaml infra/k8s/deployment.patch.yaml infra/k8s/mail.yaml; do
    [[ -s "$release/$file" ]] || { echo "Missing release file: $file" >&2; exit 1; }
done

# Also serialize manual deployments with GitHub Actions.
exec 9>/run/lock/novelka-deploy.lock
flock -n 9 || { echo 'Another deployment is running.' >&2; exit 1; }
namespace=novelka-prod
image="docker.io/library/novelka:$revision"
builder="novelka-build-${revision:0:12}"
work=$(mktemp -d)
builder_created=false
cleanup() {
    if [[ $builder_created == true ]]; then
        kubectl -n "$namespace" delete pod "$builder" --ignore-not-found --wait=false >/dev/null || true
    fi
    rm -rf "$work"
}
trap cleanup EXIT

# Local images and the shared RWO media volume require this single-node installation.
[[ $(kubectl get nodes -o json | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["items"]))') == 1 ]] \
    || { echo 'This deployment supports a single-node k3s cluster only.' >&2; exit 1; }
kubectl wait node --all --for=condition=Ready --timeout=30s
kubectl -n "$namespace" get deployment novelka -o json > "$work/current.json"
kubectl -n "$namespace" get pvc novelka-media -o json | python3 -c '
import json,sys
assert json.load(sys.stdin)["status"]["phase"] == "Bound", "Media volume is not bound"
'
# Check existence, without reading secret values.
kubectl -n "$namespace" get secret postgres-auth novelka-config -o name >/dev/null
old_revision=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["metadata"]["annotations"]["deployment.kubernetes.io/revision"])' "$work/current.json")
sed -e "s|@IMAGE@|$image|g" -e "s|@REVISION@|$revision|g" \
    "$release/infra/k8s/deployment.patch.yaml" > "$work/patch.yaml"
sed "s|@BUILDER@|$builder|g" "$release/infra/k8s/buildkit.yaml" > "$work/builder.yaml"
kubectl -n "$namespace" patch deployment novelka --type=strategic --patch-file "$work/patch.yaml" \
    --dry-run=server -o name
kubectl create --dry-run=server -f "$work/builder.yaml" -o name
kubectl apply --dry-run=server -f "$release/infra/k8s/mail.yaml" -o name
if [[ $mode == --check ]]; then
    echo 'Preflight passed; no resources changed.'
    exit 0
fi

# Provision SMTP before touching the application. The PVC keeps both queue and DKIM keys.
# A build-only check must not change any running service.
if [[ $mode == deploy ]]; then
    kubectl apply -f "$release/infra/k8s/mail.yaml"
    kubectl -n "$namespace" rollout status deployment/mail --timeout=240s
fi

# A rerun of the same commit reuses its image instead of changing an existing tag.
if k3s ctr -n k8s.io images ls -q | grep -Fx "$image" >/dev/null; then
    echo "Reusing local image $image."
else
    # BuildKit has no service-account token and never mounts production data.
    kubectl create -f "$work/builder.yaml"
    builder_created=true
    kubectl -n "$namespace" wait --for=condition=Ready "pod/$builder" --timeout=180s
    kubectl -n "$namespace" exec "$builder" -- mkdir -p /work/context
    tar -C "$release" -cf - Dockerfile novelka.jar | kubectl -n "$namespace" exec -i "$builder" -- tar -xf - -C /work/context
    kubectl -n "$namespace" exec "$builder" -- buildctl-daemonless.sh build \
        --frontend dockerfile.v0 --local context=/work/context --local dockerfile=/work/context \
        --output "type=oci,name=$image,dest=/work/image.tar"
    kubectl -n "$namespace" exec "$builder" -- cat /work/image.tar > "$work/image.tar"
    k3s ctr -n k8s.io images import "$work/image.tar"
    k3s ctr -n k8s.io images ls -q | grep -Fx "$image" >/dev/null
    kubectl -n "$namespace" delete pod "$builder" --wait=true --timeout=60s
    builder_created=false
fi
if [[ $mode == --build-only ]]; then
    echo "Built and imported $image; production is unchanged."
    exit 0
fi

rollback() {
    trap - ERR INT TERM
    echo "Deployment failed; restoring Kubernetes revision $old_revision." >&2
    kubectl -n "$namespace" get pods -l app=novelka -o wide >&2 || true
    kubectl -n "$namespace" rollout undo deployment/novelka --to-revision="$old_revision" \
        && kubectl -n "$namespace" rollout status deployment/novelka --timeout=480s \
        || echo 'Rollback did not complete; check the cluster.' >&2
    exit 1
}
trap rollback ERR INT TERM
kubectl -n "$namespace" patch deployment novelka --type=strategic --patch-file "$work/patch.yaml"
kubectl -n "$namespace" rollout status deployment/novelka --timeout=480s
curl --fail --silent --show-error --max-time 15 --retry 5 --retry-delay 5 --retry-all-errors \
    https://novelka.in.ua/api/health | python3 -c 'import json,sys; assert json.load(sys.stdin)["status"] == "ok"'
curl --fail --silent --show-error --max-time 15 --retry 5 --retry-delay 5 --retry-all-errors \
    -o /dev/null https://novelka.in.ua/
trap - ERR INT TERM
echo "Deployed $image."
