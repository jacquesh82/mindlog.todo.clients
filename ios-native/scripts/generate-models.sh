#!/usr/bin/env bash
# Regenerate the Swift DTOs from the committed OpenAPI snapshot.
#
# Unlike the Android client, the generated Swift IS committed (under
# Sources/CoreNetwork/Generated). Android regenerates on every `assembleDebug`
# because Gradle already has a JVM in hand; Xcode does not, and making every
# clone install a JDK to open a project is a worse trade than reviewing a
# generated diff. `git diff Sources/CoreNetwork/Generated` after this script is
# the equivalent review.
#
#   ./scripts/generate-models.sh
#
# The generator is openapi-generator-cli, run through whichever is available:
# a jar already in the Gradle cache (the Android client pulls the same
# version), or npx.
set -euo pipefail

VERSION="7.24.0"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPEC="$HERE/openapi/mindlog-todo.openapi.json"
OUT="$HERE/Sources/CoreNetwork/Generated"

[ -f "$SPEC" ] || { echo "Missing $SPEC — run ./scripts/fetch-openapi.sh first." >&2; exit 1; }

# Models only. The generator's client scaffolding brings its own URLSession
# stack, auth handling and error types, none of which survive contact with the
# token renewal and the SSE stream in this module.
#
# Two supporting files come along because the models do not compile without
# them, and no more: `Validation.swift` (StringRule/NumericRule, referenced by
# every model carrying a constraint) and `JSONValue.swift` (the type of
# `Error.details`, which the contract leaves free-form). Both are pure
# Foundation.
GLOBAL="models,modelDocs=false,modelTests=false,supportingFiles=Validation.swift:JSONValue.swift"

# Every id is `format: uuid` in the contract, which the generator turns into a
# Foundation UUID; ids are opaque to a client and String is what goes over the
# wire. Dates stay strings for the same reason the Android client sets
# `dateLibrary=string`: mapping them to Date here only adds a conversion the
# client must undo before sending the value back.
TYPES="UUID=String,DateTime=String,Date=String"

# `Task`, `Label` and `Section` are the contract's names and also Swift's own:
# _Concurrency.Task, SwiftUI.Label, SwiftUI.Section. A model named `Task` shadows
# the concurrency one inside every file of this package, so `Task { … }` stops
# compiling. The prefix is a rename of the Swift type only — CodingKeys, and
# therefore the wire format, are untouched.
PREFIX="Todo"

run_generator() {
  local jar
  jar="$(find "$HOME/.gradle/caches" -name "openapi-generator-cli-$VERSION.jar" 2>/dev/null | head -1)"
  if [ -n "$jar" ] && command -v java >/dev/null; then
    java -jar "$jar" "$@"
  elif command -v npx >/dev/null; then
    npx --yes "@openapitools/openapi-generator-cli@$VERSION" "$@"
  else
    echo "Need either java (with the jar in the Gradle cache) or npx." >&2
    exit 1
  fi
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

run_generator generate \
  -i "$SPEC" \
  -g swift6 \
  -o "$TMP" \
  --global-property "$GLOBAL" \
  --type-mappings "$TYPES" \
  --model-name-prefix "$PREFIX" \
  --additional-properties "projectName=MindlogTodo,useSPMFileStructure=true" \
  >/dev/null

rm -rf "$OUT"
mkdir -p "$OUT"
cp "$TMP/Sources/MindlogTodo/Models/"*.swift "$OUT/"
cp "$TMP/Sources/MindlogTodo/Infrastructure/Validation.swift" "$OUT/"
cp "$TMP/Sources/MindlogTodo/Infrastructure/JSONValue.swift" "$OUT/"

echo "Wrote $(ls "$OUT" | wc -l) files to ${OUT#"$HERE"/}"
git -C "$HERE" diff --stat -- Sources/CoreNetwork/Generated || true
