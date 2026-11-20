#!/usr/bin/env bash
# True self-play on the AGGRESSION lerp: ratplayer1(a) vs ratplayer2(a').
# Copies live ratplayer to both snapshots, then unique pairs i<=j (15 on the
# default grid). comparePlayers already plays both sides, so (a, a') is not
# rerun as (a', a). Does not write winners into Config.
#
# Usage:
#   scripts/sweep-aggression.sh
#   scripts/sweep-aggression.sh DefaultSmall,tiny
#   scripts/sweep-aggression.sh --full-pool
#
# Optional env:
#   AGGRESSIONS=0,0.25,0.5,0.75,1
#   COPY_SOURCE=ratplayer TEAM_A=ratplayer1 TEAM_B=ratplayer2
#
# First pass (default): DefaultSmall,DefaultMedium,tiny  (~15x3x2 games)
# Full-pool confirm (same maps as comparePlayers):
#   scripts/sweep-aggression.sh --full-pool
#   ./gradlew sweepAggression -PcompareMaps=full
set -euo pipefail
cd "$(dirname "$0")/.."

COPY_SOURCE=${COPY_SOURCE:-ratplayer}
TEAM_A=${TEAM_A:-ratplayer1}
TEAM_B=${TEAM_B:-ratplayer2}
AGGRESSIONS=${AGGRESSIONS:-0,0.25,0.5,0.75,1}
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

copy_one "$TEAM_A"
copy_one "$TEAM_B"

n=${#grid[@]}
for ((i = 0; i < n; i++)); do
  for ((j = i; j < n; j++)); do
    a1=${grid[i]}
    a2=${grid[j]}
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
done

python3 - "$SWEEP_DIR" "$TEAM_A" "$TEAM_B" "$SUMMARY" <<'PY'
import re
import sys
from pathlib import Path

sweep_dir, team_a, team_b, summary_path = Path(sys.argv[1]), sys.argv[2], sys.argv[3], Path(sys.argv[4])
knob_names = (
    "BACKSTAB_ROUND",
    "BABY_CAT_FLEE_HEALTH",
    "KING_CAT_FLEE_HEALTH",
    "MAX_BABY_RATS",
)
pair_re = re.compile(r"^a=(.+)-vs-(.+)\.log$")
point_files = sorted(
    sweep_dir.glob("a=*-vs-*.log"),
    key=lambda p: tuple(
        float(x) for x in pair_re.match(p.name).groups()
    ),
)

def parse_wins(text):
    wins = {team_a: 0, team_b: 0}
    games = 0
    block = re.search(r"== Wins ==\n(.*)", text, re.S)
    if not block:
        return wins, games
    for line in block.group(1).splitlines():
        parts = line.split()
        if len(parts) != 2:
            continue
        name, value = parts
        if name == "games":
            games = int(value)
        elif name in wins:
            wins[name] = int(value)
    return wins, games

def norm_a(raw):
    a = raw.strip()
    if a.endswith("f") or a.endswith("F"):
        a = a[:-1]
    return format(float(a), "g")


def parse_knobs(text, knobs_by_a):
    for m in re.finditer(
        r"effective Config \(aggression=([^)]+)\):(.*?)(?=\n\[|\n== |\n> Task |\Z)",
        text,
        re.S,
    ):
        block = m.group(2)
        found = {}
        for name in knob_names:
            km = re.search(rf"{name}\s+(\S+)", block)
            if km:
                found[name] = km.group(1)
        if found:
            knobs_by_a[norm_a(m.group(1))] = found

knobs_by_a = {}
results = {}
grid_labels = []
seen = set()
for path in point_files:
    m = pair_re.match(path.name)
    a1, a2 = m.group(1), m.group(2)
    for label in (a1, a2):
        if label not in seen:
            seen.add(label)
            grid_labels.append(label)
    text = path.read_text(errors="replace")
    parse_knobs(text, knobs_by_a)
    wins, games = parse_wins(text)
    results[(a1, a2)] = (wins[team_a], wins[team_b], games)

grid_labels.sort(key=float)
n = len(grid_labels)
cells = [[None] * n for _ in range(n)]
for i, a1 in enumerate(grid_labels):
    for j, a2 in enumerate(grid_labels):
        if (a1, a2) in results:
            w1, w2, games = results[(a1, a2)]
            cells[i][j] = (w1, w2, games)
        elif (a2, a1) in results:
            w1, w2, games = results[(a2, a1)]
            cells[i][j] = (w2, w1, games)

lines = ["== Matrix (row wins-col wins) =="]
col_w = max(7, *(len(a) for a in grid_labels), len("vs_field"))
headers = ["a".ljust(col_w)] + [a.rjust(col_w) for a in grid_labels] + ["vs_field".rjust(col_w)]
lines.append(" ".join(headers))
for i, a1 in enumerate(grid_labels):
    row = [a1.ljust(col_w)]
    field_w = field_g = 0
    for j, _a2 in enumerate(grid_labels):
        cell = cells[i][j]
        if cell is None:
            row.append("-".rjust(col_w))
            continue
        w1, w2, games = cell
        row.append(f"{w1}-{w2}".rjust(col_w))
        if i != j and games:
            field_w += w1
            field_g += games
    if field_g:
        row.append(f"{field_w}/{field_g}".rjust(col_w))
    else:
        row.append("-".rjust(col_w))
    lines.append(" ".join(row))

lines.append("")
lines.append("== Knobs ==")
knob_headers = ("a", "backstab", "baby_flee", "king_flee", "max_babies")
knob_rows = []
for a in grid_labels:
    knobs = knobs_by_a.get(norm_a(a), {})
    knob_rows.append(
        [
            a,
            knobs.get("BACKSTAB_ROUND", "-"),
            knobs.get("BABY_CAT_FLEE_HEALTH", "-"),
            knobs.get("KING_CAT_FLEE_HEALTH", "-"),
            knobs.get("MAX_BABY_RATS", "-"),
        ]
    )
widths = [len(h) for h in knob_headers]
for row in knob_rows:
    for i, cell in enumerate(row):
        widths[i] = max(widths[i], len(cell))
lines.append(" ".join(h.ljust(widths[i]) for i, h in enumerate(knob_headers)))
for row in knob_rows:
    lines.append(" ".join(row[i].ljust(widths[i]) for i in range(len(knob_headers))))
if not point_files:
    lines.append("No pair logs found.")
lines.append("")
lines.append("Does not write winners into Config.")
body = "\n".join(lines) + "\n"
summary_path.write_text(body)
print()
print(body, end="")
print(f"Summary: {summary_path}")
PY
