#!/usr/bin/env bash
# Wrapper de build du client natif iOS, pendant de ../../android-native/scripts/gradle.sh.
#
# Deux garanties, les mêmes : le projet Xcode est régénéré s'il manque (il n'est
# pas versionné), et l'environnement est explicite, pour qu'aucun build ne parte
# silencieusement sur la valeur par défaut.
#
#   ./scripts/xcode.sh build                     # qualif
#   MINDLOG_ENV=local ./scripts/xcode.sh build
#   MINDLOG_ENV=prod  ./scripts/xcode.sh archive
#   ./scripts/xcode.sh test                      # tests du paquet (swift test)
#
# macOS uniquement : xcodebuild n'existe nulle part ailleurs.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_NAME="${MINDLOG_ENV:-qualif}"
ACTION="${1:-build}"

cd "$HERE"

case "$ENV_NAME" in
  prod) SCHEME="MindlogTodo (prod)" ;;
  qualif) SCHEME="MindlogTodo (qualif)" ;;
  local) SCHEME="MindlogTodo (local)" ;;
  *) echo "Unknown MINDLOG_ENV=$ENV_NAME — expected one of: prod, qualif, local" >&2; exit 1 ;;
esac

# Les tests portent sur le paquet, pas sur l'application : rien de ce qui est
# testé ne demande un simulateur.
if [ "$ACTION" = "test" ]; then
  exec swift test
fi

if ! command -v xcodebuild >/dev/null; then
  echo "xcodebuild introuvable — ce script demande macOS et Xcode." >&2
  exit 1
fi

if [ ! -d MindlogTodo.xcodeproj ]; then
  command -v xcodegen >/dev/null || {
    echo "XcodeGen manquant : brew install xcodegen" >&2
    exit 1
  }
  xcodegen generate
fi

exec xcodebuild \
  -project MindlogTodo.xcodeproj \
  -scheme "$SCHEME" \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  "$ACTION"
