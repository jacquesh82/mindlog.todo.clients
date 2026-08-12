#!/usr/bin/env bash
#
# logs.sh — journal de l'appareil, filtré sur l'application.
#
#   ./scripts/logs.sh                 # 60 s, coquille de qualif
#   ./scripts/logs.sh 120             # 120 s
#   MINDLOG_ENV=prod ./scripts/logs.sh
#
# Lancer le script, PUIS ouvrir l'application sur l'iPhone. La sortie contient
# les erreurs JavaScript : Capacitor renvoie la console de la WebView vers le
# journal natif, ce qui est le seul moyen de les voir sans Safari — donc sans
# Mac.
#
# Le filtre porte sur le processus `App`, qui est le nom de l'exécutable d'une
# app Capacitor (CFBundleExecutable), et non sur « mindlog » : chercher le nom
# du produit ne remonte rien.
#
# Sans borne de durée, `idevicesyslog` écrit sans fin — une capture oubliée a
# déjà produit un fichier de 2,7 Go. D'où le timeout, obligatoire.
set -euo pipefail

DURATION="${1:-60}"
ENV_NAME="${MINDLOG_ENV:-qualif}"
[ "$ENV_NAME" = "prod" ] && SUFFIX="" || SUFFIX=".testing"
BUNDLE="today.mindlog.todo$SUFFIX"

command -v idevicesyslog >/dev/null || {
  echo "✗ idevicesyslog absent — paquet libimobiledevice" >&2; exit 1; }

idevice_id -l 2>/dev/null | grep -q . || {
  echo "✗ aucun appareil : brancher l'iPhone, le déverrouiller, puis réessayer." >&2
  exit 1
}

OUT="$(mktemp -t mindlog-ios-log-XXXXXX.txt)"

echo "▸ capture de ${DURATION}s → $OUT"
echo "▸ OUVREZ MAINTENANT « mindlog.todo » sur l'iPhone, et laissez l'écran affiché."
echo

# `App` couvre l'app Capacitor ; le nom de bundle couvre ce que le système
# raconte à son sujet ; WebKit couvre les échecs de chargement de la WebView.
timeout "$DURATION" idevicesyslog 2>/dev/null \
  | grep -iE "App\[[0-9]+\]|$BUNDLE|WebContent|WebKit" \
  | tee "$OUT" || true

echo
echo "✓ $(wc -l < "$OUT") lignes retenues → $OUT"
echo
echo "  Les lignes qui comptent en premier :"
grep -iE "error|exception|fail|refus|denied|blocked|cors|origin|SyntaxError|TypeError" "$OUT" \
  | head -20 || echo "  (aucune erreur évidente — joindre le fichier entier)"
