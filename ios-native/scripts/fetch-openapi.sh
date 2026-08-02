#!/usr/bin/env bash
# Refresh the committed OpenAPI snapshot the Swift DTOs are generated from.
#
# Same job, same flags as ../../android-native/scripts/fetch-openapi.sh — each
# native client keeps its own snapshot so a clone builds without a server in
# reach. Two snapshots can drift apart; `git diff */openapi/` is where that
# shows.
#
#   ./scripts/fetch-openapi.sh                       # from a running server
#   MINDLOG_API=https://todo.gra01.mindlog.today/app ./scripts/fetch-openapi.sh
#   ./scripts/fetch-openapi.sh --from-repo           # from the sibling repo's build
#
# `--from-repo` exists because the server needs a database to start, and the
# document does not: it is a pure function of the compiled sources.
#
# Refreshing the snapshot does not touch the generated Swift — run
# ./scripts/generate-models.sh afterwards.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$HERE/../openapi/mindlog-todo.openapi.json"
REPO="${MINDLOG_TODO_REPO:-$HERE/../../../mindlog.todo}"

mkdir -p "$(dirname "$OUT")"

if [ "${1:-}" = "--from-repo" ]; then
  if [ ! -f "$REPO/packages/server/dist/openapi.js" ]; then
    echo "Build the server first:  (cd $REPO && npm run build -w @mindlog/server)" >&2
    exit 1
  fi
  node --input-type=module -e "
    import { getOpenApiDocumentV30 } from '$REPO/packages/server/dist/openapi.js';
    process.stdout.write(JSON.stringify(getOpenApiDocumentV30()));
  " | jq -S . > "$OUT"
else
  API="${MINDLOG_API:-http://localhost:8080}"
  # 3.0, not 3.1: 3.1 writes a nullable field as `type: ["string","null"]`,
  # which the generator reads as a plain non-optional String.
  curl -fsS "$API/openapi-3.0.json" | jq -S . > "$OUT"
fi

# `jq -S` sorts keys, so re-running on an unchanged server produces no diff.
echo "Wrote $(jq '.paths | keys | length' < "$OUT") paths to ${OUT#"$HERE"/../}"
git -C "$HERE/.." diff --stat -- openapi/ || true
