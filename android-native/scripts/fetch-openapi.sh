#!/usr/bin/env bash
# Refresh the committed OpenAPI snapshot the Kotlin DTOs are generated from.
#
# The snapshot is committed and the generated Kotlin is not: a clone builds
# without a server in reach, and `git diff openapi/` shows exactly what the API
# changed — which is the only part worth reviewing.
#
#   ./scripts/fetch-openapi.sh                       # from a running server
#   MINDLOG_API=https://todo.gra01.mindlog.today/app ./scripts/fetch-openapi.sh
#   ./scripts/fetch-openapi.sh --from-repo           # from the sibling repo's build
#
# `--from-repo` exists because the server needs a database to start, and the
# document does not: it is a pure function of the compiled sources.
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
  # 3.0, not 3.1: openapi-generator's Kotlin backend reads 3.1's
  # `type: ["string","null"]` as a plain non-null String.
  curl -fsS "$API/openapi-3.0.json" | jq -S . > "$OUT"
fi

# `jq -S` sorts keys, so re-running on an unchanged server produces no diff.
echo "Wrote $(jq '.paths | keys | length' < "$OUT") paths to ${OUT#"$HERE"/../}"
git -C "$HERE/.." diff --stat -- openapi/ || true
