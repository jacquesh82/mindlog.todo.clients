#!/usr/bin/env bash
#
# xcode.sh — invoque xcodebuild sur le projet iOS généré par Capacitor.
#
#   bash scripts/xcode.sh build      # .app non signé
#   bash scripts/xcode.sh ipa        # + empaquetage .ipa
#
# Jumeau de ../../android/scripts/gradle.sh, et comme lui il décide de l'identité
# de la variante d'après MINDLOG_ENV. Android lit `-PmindlogEnv` dans son
# build.gradle ; ici les valeurs passent par trois réglages de build MINDLOG_*,
# que la cible App référence dans son project.pbxproj.
#
# Ce détour n'est pas cosmétique. Un réglage passé à `xcodebuild` en ligne de
# commande s'applique à TOUTES les cibles du workspace, Pods compris : écraser
# PRODUCT_BUNDLE_IDENTIFIER directement donnait l'identifiant de l'application
# aux trois frameworks Capacitor embarqués, et l'installation échouait sur
# `DuplicateIdentifier` — après le build, après la signature, sur l'appareil.
# Les variables MINDLOG_* n'étant référencées que par la cible App, les Pods
# gardent les identifiants que CocoaPods leur donne.
#
# macOS uniquement — xcodebuild n'existe nulle part ailleurs. Le build de CI
# fait exactement la même chose sur un runner macOS.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ACTION="${1:-build}"

# Même environnement que sync.sh : c'est lui qui décide du serveur baké dans le
# bundle web, et celui-ci qui décide de l'identifiant posé sur l'appareil. Les
# deux doivent impérativement rester alignés.
ENV_NAME="${MINDLOG_ENV:-qualif}"
case "$ENV_NAME" in
  prod|qualif|local) ;;
  *) echo "✗ environnement inconnu : '$ENV_NAME' (attendu : prod qualif local)" >&2; exit 1 ;;
esac

# Hors prod, l'identifiant reçoit le suffixe `.testing` : l'app de test
# s'installe donc À CÔTÉ de la prod au lieu de l'écraser. Le schéma d'URL du
# retour OAuth suit l'identifiant (cf. CFBundleURLTypes dans Info.plist), faute
# de quoi les deux variantes se disputeraient le même deep link.
if [ "$ENV_NAME" = "prod" ]; then
  APP_ID="today.mindlog.todo"
  APP_NAME="mindlog.todo"
  VERSION="1.7.1"
else
  APP_ID="today.mindlog.todo.testing"
  APP_NAME="mindlog.todo ($ENV_NAME)"
  VERSION="1.7.1-$ENV_NAME"
fi

[ -d "$HERE/ios/App" ] || {
  echo "✗ projet iOS absent — lancer d'abord : npx cap add ios" >&2; exit 1; }

command -v xcodebuild >/dev/null || {
  echo "✗ xcodebuild introuvable — ce script demande macOS et Xcode." >&2; exit 1; }

# Le workspace et non le projet : les dépendances de Capacitor arrivent par
# CocoaPods, et le .xcodeproj seul ne les voit pas.
[ -d "$HERE/ios/App/App.xcworkspace" ] && [ -d "$HERE/ios/App/Pods" ] || {
  echo "▸ pod install (absent)"
  ( cd "$HERE/ios/App" && pod install )
}

echo "▸ xcodebuild $ACTION (env=$ENV_NAME, id=$APP_ID)"
cd "$HERE/ios/App"

# Non signé : la signature se fait après coup, avec iloader et un Apple ID
# gratuit. Voir le README.
xcodebuild build \
  -workspace App.xcworkspace \
  -scheme App \
  -configuration Release \
  -sdk iphoneos \
  -derivedDataPath build \
  ONLY_ACTIVE_ARCH=NO \
  MINDLOG_APP_ID="$APP_ID" \
  MINDLOG_APP_NAME="$APP_NAME" \
  MINDLOG_APP_VERSION="$VERSION" \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY="" \
  CODE_SIGN_ENTITLEMENTS=""

[ "$ACTION" = "ipa" ] || exit 0

APP=$(find build/Build/Products -maxdepth 2 -name 'App.app' -type d | head -1)
[ -n "$APP" ] || { echo "✗ aucun .app produit" >&2; exit 1; }

rm -rf Payload
mkdir Payload
cp -R "$APP" Payload/
IPA="$HERE/mindlog-todo-$ENV_NAME.ipa"
rm -f "$IPA"
zip -qry "$IPA" Payload
rm -rf Payload

echo "✓ $IPA"
