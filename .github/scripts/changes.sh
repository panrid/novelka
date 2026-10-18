#!/usr/bin/env bash
# Which checks the code at HEAD needs (deploy-production.yml, pull-request.yml).
# HEAD is compared with the commit last deployed (the newest green deploy run on main), not with the
# previous push: a change whose tests failed never reached the server and is checked again.
# Writes backend=true|false and frontend=true|false to $GITHUB_OUTPUT; anything unclear checks both.
set -uo pipefail

result() {
    { echo "backend=$1"; echo "frontend=$2"; } >> "$GITHUB_OUTPUT"
    echo "$3" | tee -a "$GITHUB_STEP_SUMMARY"
    exit 0
}
full() {
    result true true "Повна перевірка: $1"
}

case "$GITHUB_EVENT_NAME" in
    push | pull_request) ;;
    *) full "запуск вручну ($GITHUB_EVENT_NAME)" ;;
esac
base=$(gh api -X GET "repos/$GITHUB_REPOSITORY/actions/workflows/deploy-production.yml/runs" \
    -f branch=main -f status=success -F per_page=1 --jq '.workflow_runs[0].head_sha // empty') \
    || full "не вдалося дізнатися останній деплой"
[ -n "$base" ] || full "успішного деплою ще не було"
# --no-renames: a moved file counts at both paths (and needs no file contents in a blobless clone).
files=$(git diff --name-only --no-renames "$base" HEAD) || full "не вдалося порівняти з ${base:0:7}"
code=$(grep -v -E '^doc/|\.md$' <<< "$files" | grep -v '^$')
[ -n "$code" ] || full "від ${base:0:7} код не змінився"
count=$(wc -l <<< "$code" | tr -d ' ')
echo "::group::Змінено від ${base:0:7} (файлів: $count)"
echo "$code"
echo '::endgroup::'
since="від ${base:0:7}, файлів: $count"

# The server fills frontend/index.html for search engines and link previews (SearchPages), and
# backend/build.gradle builds the site into the JAR: either one means both.
if ! grep -q -v '^frontend/' <<< "$code" && ! grep -q -x 'frontend/index.html' <<< "$code"; then
    result false true "Лише інтерфейс: тести бекенду пропущено ($since)"
fi
if ! grep -q -v '^backend/' <<< "$code" && ! grep -q -x 'backend/build.gradle' <<< "$code"; then
    result true false "Лише бекенд: перевірки інтерфейсу пропущено ($since)"
fi
areas=$(grep -o -x -E 'frontend/index.html|backend/build.gradle' <<< "$code"
    grep -v -x -E 'frontend/index.html|backend/build.gradle' <<< "$code" | sed -E 's#/.*#/#')
full "змінено $(sort -u <<< "$areas" | paste -s -d ' ' - | sed 's/ /, /g') ($since)"
