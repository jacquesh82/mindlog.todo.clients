#!/usr/bin/env bash
#
# gradle.sh — invoque le wrapper Gradle du projet Android généré par Capacitor,
# avec un JDK explicite. Le JDK par défaut du poste est le 17, or Capacitor 7 /
# AGP 8.7 exigent le 21 ; on ne dépend donc pas de `archlinux-java`.
#
#   bash scripts/gradle.sh assembleDebug
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Premier JDK ≥ 21 trouvé, sauf si l'appelant impose déjà JAVA_HOME.
if [ -z "${JAVA_HOME:-}" ]; then
  for v in 21 25 26; do
    if [ -d "/usr/lib/jvm/java-$v-openjdk" ]; then
      export JAVA_HOME="/usr/lib/jvm/java-$v-openjdk"
      break
    fi
  done
fi
[ -n "${JAVA_HOME:-}" ] || { echo "✗ aucun JDK >= 21 trouvé sous /usr/lib/jvm" >&2; exit 1; }

[ -d "$HERE/android" ] || {
  echo "✗ projet Android absent — lancer d'abord : npx cap add android" >&2; exit 1; }

# Même environnement que sync.sh : c'est lui qui décide de l'applicationId
# (`today.mindlog.todo.testing` hors prod) et du serveur baké dans le bundle.
# Les deux doivent impérativement rester alignés.
ENV_NAME="${MINDLOG_ENV:-qualif}"

echo "▸ gradle $* (env=$ENV_NAME, JAVA_HOME=$JAVA_HOME)"
cd "$HERE/android"
exec ./gradlew -PmindlogEnv="$ENV_NAME" "$@"
