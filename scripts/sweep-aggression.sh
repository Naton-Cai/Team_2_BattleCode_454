#!/usr/bin/env bash
# True self-play on the AGGRESSION lerp. Copies live ratplayer to one snapshot
# per grid value named ratplayer_a<token> so the client shows the aggression
# (e.g. ratplayer_a0p5 vs ratplayer_a1). Unique pairs i<=j (21 on the default
# grid, including -1 = live DEFAULT mix). comparePlayers already plays both
# sides, so (a, a') is not rerun as (a', a). Does not write winners into Config.
#
# Usage:
#   scripts/sweep-aggression.sh
#   scripts/sweep-aggression.sh DefaultSmall,tiny
#   scripts/sweep-aggression.sh --full-pool
#
# Optional env:
#   AGGRESSIONS=-1,0,0.25,0.5,0.75,1
#   COPY_SOURCE=ratplayer
#   PAIR_INDEX=0   # one unique pair (0-20 on the default grid); skip the matrix
#
# First pass (default): DefaultSmall,DefaultMedium,tiny  (~21x3x2 games)
# Full-pool confirm (same maps as comparePlayers):
#   scripts/sweep-aggression.sh --full-pool
#   ./gradlew sweepAggression -PcompareMaps=full
set -euo pipefail
cd "$(dirname "$0")/.."

COPY_SOURCE=${COPY_SOURCE:-ratplayer}
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

# Java package: -1 -> ratplayer_am1, 0.5 -> ratplayer_a0p5.
aggression_pkg() {
  local token=$1
  token=${token//-/m}
  token=${token//./p}
  echo "ratplayer_a${token}"
}

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

needed=()
for pair in "${pairs[@]}"; do
  read -r a1 a2 <<<"$pair"
  needed+=("$a1")
  needed+=("$a2")
done
unique_a=()
seen_a=" "
for a in "${needed[@]}"; do
  case "$seen_a" in
    *" $a "*) continue ;;
  esac
  seen_a+="$a "
  unique_a+=("$a")
done

for a in "${unique_a[@]}"; do
  dest=$(aggression_pkg "$a")
  copy_one "$dest"
  set_args=(setSnapshotAggression "-PteamB=${dest}" "-Paggression=${a}" --console=plain)
  knob_log="${SWEEP_DIR}/knobs-a=${a}.txt"
  echo "+ ./gradlew ${set_args[*]}" | tee -a "$COMBINED"
  ./gradlew "${set_args[@]}" | tee -a "$COMBINED" >"$knob_log"
done

for pair in "${pairs[@]}"; do
  read -r a1 a2 <<<"$pair"
  team_a=$(aggression_pkg "$a1")
  team_b=$(aggression_pkg "$a2")
  point_log="${SWEEP_DIR}/a=${a1}-vs-${a2}.log"
  knob_a="${SWEEP_DIR}/knobs-a=${a1}.txt"
  knob_b="${SWEEP_DIR}/knobs-a=${a2}.txt"
  if [[ ! -f "$knob_a" || ! -f "$knob_b" ]]; then
    echo "Missing knob dump for pair ${a1} vs ${a2}" >&2
    exit 1
  fi
  {
    echo
    echo "== aggression ${a1} vs ${a2} =="
    cat "$knob_a"
    if [[ "$a1" != "$a2" ]]; then
      cat "$knob_b"
    fi
  } | tee -a "$COMBINED" "$point_log"

  compare_args=(
    classes
    comparePlayers
    "-PcompareA=${team_a}"
    "-PcompareB=${team_b}"
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

python3 scripts/summarize-aggression-sweep.py "$SWEEP_DIR" ratplayer1 ratplayer2 "$SUMMARY"
