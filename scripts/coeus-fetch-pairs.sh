#!/usr/bin/env bash
# Copy Coeus sweep pair logs and .bc26 replays to this laptop.
# Run from the repo root with the PSU VPN on.
# Usage:
#   scripts/coeus-fetch-pairs.sh
#   scripts/coeus-fetch-pairs.sh matches-coeus-pairs/latest
# Does not write Config.
set -euo pipefail
cd "$(dirname "$0")/.."

KEY=${COEUS_KEY:-$HOME/.ssh/id_ed25519_psu}
HOST=${COEUS_HOST:-login1.coeus.rc.pdx.edu}
USER=${COEUS_USER:-jorgep}
REPO=${COEUS_REPO:-Team_2_BattleCode_454}
REMOTE_DIR=${COEUS_REMOTE_DIR:-${REPO}/matches/pairs}
DEST=${1:-matches-coeus-pairs/$(date +%Y%m%d-%H%M%S)}
SSH=(ssh -i "$KEY" -o IdentitiesOnly=yes)

if [[ ! -f "$KEY" ]]; then
  echo "Missing cluster key: $KEY" >&2
  exit 1
fi

mkdir -p "$DEST"
echo "Fetching ${USER}@${HOST}:~/${REMOTE_DIR}/ -> ${DEST}/"
rsync -az --progress \
  -e "${SSH[*]}" \
  "${USER}@${HOST}:${REMOTE_DIR}/" \
  "${DEST}/"

echo "Fetching leftover ${USER}@${HOST}:~/${REPO}/work/**/*.bc26 -> ${DEST}/work/"
rsync -az --progress \
  --include='*/' \
  --include='*.bc26' \
  --exclude='*' \
  -e "${SSH[*]}" \
  "${USER}@${HOST}:${REPO}/work/" \
  "${DEST}/work/"

echo
echo "Logs:"
ls -l "$DEST"/*.log "$DEST"/summary.txt 2>/dev/null || true
echo
echo "Replays:"
find "$DEST" -name '*.bc26' -print | sort
count=$(find "$DEST" -name '*.bc26' | wc -l | tr -d ' ')
echo "${count} .bc26 files"
echo
if [[ -f "${DEST}/summary.txt" ]]; then
  echo "Summary: ${DEST}/summary.txt"
  cat "${DEST}/summary.txt"
else
  echo "No summary.txt yet (merge may still be pending)." >&2
fi
