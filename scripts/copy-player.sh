#!/usr/bin/env bash
# Snapshot a player package under src/ and rewrite its Java package name.
# Does not implement strategy. After the copy, edit src/<dest> by hand.
# Usage: scripts/copy-player.sh [source] [dest]
# Example: scripts/copy-player.sh ratplayer ratplayer2
set -euo pipefail
cd "$(dirname "$0")/.."

SOURCE=${1:-ratplayer}
DEST=${2:-ratplayer2}
SRC_DIR="src/${SOURCE}"
DEST_DIR="src/${DEST}"

if [[ ! "$SOURCE" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || [[ ! "$DEST" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
  echo "Source and dest must be Java identifiers (e.g. ratplayer2), got '$SOURCE' '$DEST'" >&2
  exit 1
fi
if [[ "$SOURCE" == "$DEST" ]]; then
  echo "Source and dest must be different, got '$SOURCE'" >&2
  exit 1
fi
if [[ ! -d "$SRC_DIR" ]]; then
  echo "Missing source player: $SRC_DIR" >&2
  exit 1
fi
if [[ -e "$DEST_DIR" ]]; then
  rm -rf "$DEST_DIR"
fi

cp -R "$SRC_DIR" "$DEST_DIR"
find "$DEST_DIR" -name '*.java' -print0 | while IFS= read -r -d '' file; do
  perl -pi -e "s/\\b${SOURCE}\\b/${DEST}/g" "$file"
done

OVERLAY_SRC="scripts/player-overlays/ratplayer/Aggression.java"
DEST_CONFIG="${DEST_DIR}/Config.java"
if [[ -f "$OVERLAY_SRC" && -f "$DEST_CONFIG" ]] \
    && grep -q 'DEFENSIVE_BACKSTAB_ROUND' "$DEST_CONFIG" \
    && grep -q 'OFFENSIVE_BABY_CAT_FLEE_HEALTH' "$DEST_CONFIG"; then
  cp "$OVERLAY_SRC" "${DEST_DIR}/Aggression.java"
  perl -pi -e "s/\\bratplayer\\b/${DEST}/g" "${DEST_DIR}/Aggression.java"
  python3 - "$DEST_CONFIG" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
if "Aggression.apply()" in text:
    sys.exit(0)
hook = """
  static {
    Aggression.apply();
  }
"""
idx = text.rfind("}")
if idx < 0:
    sys.exit("dest Config.java has no closing brace")
path.write_text(text[:idx] + hook + text[idx:])
PY
fi

# JaCoCo only measures src/ratplayer. Generated snapshots do not need an exclude.
if [[ "${SKIP_GRADLE:-}" != "1" ]]; then
  ./gradlew classes
fi
echo "Copied $SRC_DIR -> $DEST_DIR"
