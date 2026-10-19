#!/bin/bash
# Removes Novelka's iCloud backup job. Keeps the settings (~/.config/novelka-backup) and the copies
# in iCloud Drive; delete them by hand if they are no longer needed.
set -euo pipefail

label=ua.in.novelka.icloud-backup
launchctl bootout "gui/$(id -u)/$label" 2> /dev/null || true
rm -f "$HOME/Library/LaunchAgents/$label.plist"
rm -rf "$HOME/Library/Application Support/novelka-backup"
echo "Видалено: $label. Налаштування й копії в iCloud лишилися."
