#!/usr/bin/env bash
# Gradle wrapper for the native client, mirroring ../android/scripts/gradle.sh.
#
# Two things it guarantees: a JDK 21+ (the machine default is 17, which AGP
# rejects) and an explicit -PmindlogEnv, so no build silently picks the default.
#
#   ./scripts/gradle.sh assembleDebug                    # qualif
#   MINDLOG_ENV=local ./scripts/gradle.sh installDebug
#   MINDLOG_ENV=prod  ./scripts/gradle.sh assembleRelease
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_NAME="${MINDLOG_ENV:-qualif}"

if [ -z "${JAVA_HOME:-}" ] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE '"(2[1-9]|[3-9][0-9])'; then
  for v in 21 25 26 24 23 22; do
    for candidate in "/usr/lib/jvm/java-$v-openjdk" "/usr/lib/jvm/java-$v-openjdk-amd64"; do
      if [ -x "$candidate/bin/java" ]; then
        export JAVA_HOME="$candidate"
        break 2
      fi
    done
  done
fi

if [ -z "${JAVA_HOME:-}" ]; then
  echo "No JDK 21+ found under /usr/lib/jvm — set JAVA_HOME yourself." >&2
  exit 1
fi

cd "$HERE"
exec ./gradlew -PmindlogEnv="$ENV_NAME" "$@"
