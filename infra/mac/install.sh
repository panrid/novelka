#!/bin/bash
# Installs (or updates) Novelka's iCloud backup for the current macOS user. From the repository:
#   bash infra/mac/install.sh
# Then fill in ~/.config/novelka-backup/config (AGE_RECIPIENT). Details: doc/deployment.md.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
label=ua.in.novelka.icloud-backup
app_dir="$HOME/Library/Application Support/novelka-backup"
config_dir="$HOME/.config/novelka-backup"
plist="$HOME/Library/LaunchAgents/$label.plist"

mkdir -p "$app_dir" "$config_dir" "$HOME/Library/LaunchAgents" "$HOME/Library/Logs"
chmod 700 "$config_dir"
install -m 755 "$here/novelka-icloud-backup.sh" "$app_dir/novelka-icloud-backup.sh"
if [ ! -e "$config_dir/config" ]; then
    install -m 600 "$here/config.example" "$config_dir/config"
    echo "Налаштування: $config_dir/config — впишіть AGE_RECIPIENT (публічний ключ age1…)."
fi
sed "s#__HOME__#$HOME#g" "$here/$label.plist" > "$plist"
plutil -lint "$plist" > /dev/null

launchctl bootout "gui/$(id -u)/$label" 2> /dev/null || true
launchctl bootstrap "gui/$(id -u)" "$plist"
echo "Встановлено: $label (журнал — ~/Library/Logs/novelka-icloud-backup.log)."
echo "Запустити зараз: launchctl kickstart -p gui/$(id -u)/$label"
