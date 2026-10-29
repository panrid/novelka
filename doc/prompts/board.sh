#!/usr/bin/env bash
# Read-only snapshot. Board status is not issue state: closed issues may have stale statuses.
set -euo pipefail

python3 - <<'PY'
import json
import subprocess
import sys


def gh(*args):
    return subprocess.check_output(["gh", *args], text=True)


try:
    board = json.loads(gh("project", "item-list", "2", "--owner", "panrid",
                          "--limit", "350", "--format", "json"))
    if len(board["items"]) < board["totalCount"]:
        sys.exit("Дошка має понад 350 пунктів: збільште ліміт, щоб знімок був повним.")
    # The project JSON omits issue state. Fetch open issue numbers separately.
    opened = set(gh("api", "--method", "GET",
                    "repos/panrid/novelka/issues?state=open&per_page=100",
                    "--paginate", "--jq",
                    '.[] | select(has("pull_request") | not) | .number').split())
except (subprocess.CalledProcessError, json.JSONDecodeError, KeyError):
    sys.exit("Не вдалося прочитати дошку/задачі. Перевірте доступ gh до репозиторію й project.")


def cell(value):
    return " ".join(str(value).split()).replace("|", "\\|")


rows = []
for item in board["items"]:
    issue = item.get("content", {})
    if (issue.get("type") != "Issue" or issue.get("repository") != "panrid/novelka"
            or str(issue.get("number")) not in opened):
        continue
    # gh may return the Ukrainian field name with a damaged first character.
    stage = item.get("Етап", next((v for k, v in item.items() if k.endswith("тап")), "—"))
    rows.append((issue["number"], issue["title"], item.get("status", "—"), stage))

print("| Номер | Назва | Стан | Етап |")
print("|---|---|---|---|")
for number, title, status, stage in sorted(rows):
    print(f"| #{number} | {cell(title)} | {cell(status)} | {cell(stage)} |")
PY
