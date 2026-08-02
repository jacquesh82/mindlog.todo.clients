#!/usr/bin/env bash
#
# sync.sh — rejoue le code Web dans l'app iOS.
#
#   MINDLOG_ENV=qualif|prod|local  (défaut : qualif)
#
# 1. build Vite du client web (../web) avec les variables propres à la coquille
#    (VITE_BASE=/ car les assets sont à la racine du bundle, VITE_API_URL absolu
#    car la WebView est sur une autre origine que l'API) ;
# 2. `cap sync ios` → copie dist/ dans ios/App/App/public et réaligne les
#    plugins natifs.
#
# C'est l'UNIQUE commande à lancer pour reporter une évolution du client web
# dans l'app iOS. Jumeau de ../../android/scripts/sync.sh : si l'un des deux
# change, l'autre doit suivre.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # mindlog.todo.clients/ios
WEB="$(cd "$HERE/../web" && pwd)"                         # mindlog.todo.clients/web

ENV_NAME="${MINDLOG_ENV:-qualif}"
ENV_FILE="$HERE/scripts/env/$ENV_NAME.env"
[ -f "$ENV_FILE" ] || {
  echo "✗ environnement inconnu : '$ENV_NAME' (attendu : $(cd "$HERE/scripts/env" && ls *.env | sed 's/\.env$//' | tr '\n' ' '))" >&2
  exit 1
}

set -a; . "$ENV_FILE"; set +a
# Les assets sont servis depuis la racine du bundle embarqué, pas depuis /app.
export VITE_BASE=/

echo "▸ [1/2] build web (env=$ENV_NAME, VITE_API_URL=$VITE_API_URL, VITE_BASE=$VITE_BASE)"
( cd "$WEB" && npm run build )

# Le dist/ produit ici est « saveur embarquée » (base=/ + API absolue) et n'est
# PAS celui qu'attend le déploiement web (base=/app/ + API relative). On le
# marque pour éviter qu'un build Docker local parte avec le mauvais bundle — le
# même marqueur que pose la coquille Android, pour la même raison.
printf 'built-by=ios/scripts/sync.sh\nenv=%s\nVITE_API_URL=%s\nVITE_BASE=%s\n' \
  "$ENV_NAME" "$VITE_API_URL" "$VITE_BASE" > "$WEB/dist/.android-build"

echo "▸ [2/2] cap sync ios"
( cd "$HERE" && npx cap sync ios )

echo "✓ app iOS alignée sur le code web (env=$ENV_NAME)"
echo "  Build      : npm run build:debug"
echo "  Archive    : npm run build:release  (env=prod)"
