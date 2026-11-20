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

# JaCoCo only measures src/ratplayer. Generated snapshots do not need an exclude.
if [[ "${SKIP_GRADLE:-}" != "1" ]]; then
  ./gradlew classes
fi
echo "Copied $SRC_DIR -> $DEST_DIR"
