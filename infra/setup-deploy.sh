#!/usr/bin/env bash
# One-time server setup. Supply only a PUBLIC SSH key; the private key stays with the owner.
set -euo pipefail
[[ $EUID == 0 && $# == 1 ]] || { echo "Usage: sudo bash $0 PUBLIC_KEY_FILE" >&2; exit 2; }
ssh-keygen -lf "$1" >/dev/null
source_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
account=novelka-deploy
if ! id "$account" >/dev/null 2>&1; then
    useradd --system --create-home --user-group --home-dir /var/lib/novelka-deploy --shell /bin/bash "$account"
fi
account_home=$(getent passwd "$account" | cut -d: -f6)
install -d -o "$account" -g "$account" -m 700 "$account_home/.ssh" /srv/novelka-releases
keyfile=$(mktemp)
rule=$(mktemp)
trap 'rm -f "$keyfile" "$rule"' EXIT
# Disable SSH forwarding, agent forwarding and PTYs; retain command execution and SFTP for scp.
sed 's/^/restrict /' "$1" > "$keyfile"
install -o "$account" -g "$account" -m 600 "$keyfile" "$account_home/.ssh/authorized_keys"
install -o root -g root -m 755 "$source_dir/deploy-entrypoint.sh" /usr/local/sbin/novelka-deploy
printf '%s\n' 'novelka-deploy ALL=(root) NOPASSWD: /usr/local/sbin/novelka-deploy' > "$rule"
visudo -cf "$rule"
install -o root -g root -m 440 "$rule" /etc/sudoers.d/novelka-deploy
echo 'Deployment account ready: novelka-deploy; uploads: /srv/novelka-releases.'
