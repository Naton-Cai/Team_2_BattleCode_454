#!/usr/bin/env bash
# True self-play on the AGGRESSION lerp: ratplayer1(a) vs ratplayer2(a').
# Copies live ratplayer to both snapshots, then unique pairs i<=j (21 on the
# default grid, including -1 = live DEFAULT mix). comparePlayers already plays
# both sides, so (a, a') is not rerun as (a', a). Does not write winners into Config.
#
# Usage:
#   scripts/sweep-aggression.sh
#   scripts/sweep-aggression.sh DefaultSmall,tiny
#   scripts/sweep-aggression.sh --full-pool
#
# Optional env:
#   AGGRESSIONS=-1,0,0.25,0.5,0.75,1
#   COPY_SOURCE=ratplayer TEAM_A=ratplayer1 TEAM_B=ratplayer2
#   PAIR_INDEX=0   # one unique pair (0-20 on the default grid); skip the matrix
#
# First pass (default): DefaultSmall,DefaultMedium,tiny  (~21x3x2 games)
# Full-pool confirm (same maps as comparePlayers):
#   scripts/sweep-aggression.sh --full-pool
#   ./gradlew sweepAggression -PcompareMaps=full
set -euo pipefail
cd "$(dirname "$0")/.."

COPY_SOURCE=${COPY_SOURCE:-ratplayer}
TEAM_A=${TEAM_A:-ratplayer1}
TEAM_B=${TEAM_B:-ratplayer2}
AGGRESSIONS=${AGGRESSIONS:--1,0,0.25,0.5,0.75,1}
DEFAULT_MAPS="DefaultSmall,DefaultMedium,tiny"

MAPS="$DEFAULT_MAPS"
FULL_POOL=0
if [[ "${1:-}" == "--full-pool" ]]; then
  FULL_POOL=1
  MAPS=""
elif [[ -n "${1:-}" ]]; then
  MAPS=$1
fi

STAMP=$(date +%Y%m%d-%H%M%S)
GIT=$(git rev-parse --short HEAD 2>/dev/null || echo nogit)
if ! git diff --quiet HEAD -- 2>/dev/null; then
  GIT="${GIT}-dirty"
fi
SWEEP_DIR="matches/sweep-${STAMP}-${GIT}"
mkdir -p "$SWEEP_DIR"
SUMMARY="${SWEEP_DIR}/summary.txt"
COMBINED="${SWEEP_DIR}/sweep.log"
: >"$COMBINED"
echo "Writing sweep to ${SWEEP_DIR}"

grid=()
IFS=',' read -r -a RAW_GRID <<< "$AGGRESSIONS"
for a in "${RAW_GRID[@]}"; do
  a=$(echo "$a" | tr -d '[:space:]')
  [[ -n "$a" ]] || continue
  grid+=("$a")
done

copy_one() {
  local dest=$1
  local args=(copyPlayer "-PcopySource=${COPY_SOURCE}" "-PcopyDest=${dest}" --console=plain)
  echo "+ ./gradlew ${args[*]}" | tee -a "$COMBINED"
  ./gradlew "${args[@]}" | tee -a "$COMBINED"
}

n=${#grid[@]}
pairs=()
for ((i = 0; i < n; i++)); do
  for ((j = i; j < n; j++)); do
    pairs+=("${grid[i]} ${grid[j]}")
  done
done

if [[ -n "${PAIR_INDEX:-}" ]]; then
  if [[ ! "$PAIR_INDEX" =~ ^[0-9]+$ ]] || ((PAIR_INDEX < 0 || PAIR_INDEX >= ${#pairs[@]})); then
    echo "PAIR_INDEX must be 0-$((${#pairs[@]} - 1)), got '${PAIR_INDEX}'" >&2
    exit 1
  fi
  pairs=("${pairs[PAIR_INDEX]}")
fi

copy_one "$TEAM_A"
copy_one "$TEAM_B"

for pair in "${pairs[@]}"; do
  read -r a1 a2 <<<"$pair"
  point_log="${SWEEP_DIR}/a=${a1}-vs-${a2}.log"
  echo | tee -a "$COMBINED"
  echo "== aggression ${a1} vs ${a2} ==" | tee -a "$COMBINED" "$point_log"

  set_a1=(setSnapshotAggression "-PteamB=${TEAM_A}" "-Paggression=${a1}" --console=plain)
  echo "+ ./gradlew ${set_a1[*]}" | tee -a "$COMBINED" "$point_log"
  ./gradlew "${set_a1[@]}" | tee -a "$COMBINED" "$point_log"

  compare_args=(
    setSnapshotAggression
    classes
    comparePlayers
    "-Paggression=${a2}"
    "-PteamB=${TEAM_B}"
    "-PcompareA=${TEAM_A}"
    "-PcompareB=${TEAM_B}"
    --console=plain
  )
  if [[ "$FULL_POOL" -eq 0 ]]; then
    compare_args+=("-PcompareMaps=${MAPS}")
  fi
  echo "+ ./gradlew ${compare_args[*]}" | tee -a "$COMBINED" "$point_log"
  ./gradlew "${compare_args[@]}" | tee -a "$COMBINED" "$point_log"
done

if [[ -n "${PAIR_INDEX:-}" ]]; then
  echo "Pair log: ${SWEEP_DIR}/a=${a1}-vs-${a2}.log"
  exit 0
fi

python3 scripts/summarize-aggression-sweep.py "$SWEEP_DIR" "$TEAM_A" "$TEAM_B" "$SUMMARY"
