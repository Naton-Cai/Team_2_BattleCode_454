#!/usr/bin/env bash
# Headless A vs B, then B vs A, on a comma-separated map list.
# Usage: scripts/compare-players.sh [teamA] [teamB] [maps]
# Example: scripts/compare-players.sh ratplayer ratplayer2
set -euo pipefail
cd "$(dirname "$0")/.."

TEAM_A=${1:-ratplayer}
TEAM_B=${2:-ratplayer2}
MAP_POOL=(
  5t4rv4t10n_1337
  arrows
  averyfineline
  averystrangespace
  canyoudig
  cheesebottles
  cheesefarm
  cheeseguardians
  closeup
  corridorofdoomanddespair
  DefaultLarge
  DefaultMedium
  DefaultSmall
  dirtfulcat
  dirtpassageway
  EscapeTheNight
  evileye
  hatefullattice
  jail
  keepout
  knifefight
  Meow
  mercifullattice
  minimaze
  Nofreecheese
  peaceinourtime
  pipes
  popthecork
  rift
  safelycontained
  sittingducks
  starvation
  streetsofnewyork
  TheHeist
  thunderdome
  tiny
  toomuchcheese
  trapped
  uneruesansfin
  wallsofparadis
  whatsthecatdoin
  whereisthecheese
  ZeroDay
)
MAPS=${3:-$(IFS=,; echo "${MAP_POOL[*]}")}

STAMP=$(date +%Y%m%d-%H%M%S)
GIT=$(git rev-parse --short HEAD 2>/dev/null || echo nogit)
if ! git diff --quiet HEAD -- 2>/dev/null; then
  GIT="${GIT}-dirty"
fi
RUN_DIR="matches/${STAMP}-${GIT}"
mkdir -p "$RUN_DIR"
COMBINED_LOG="${RUN_DIR}/${TEAM_A}-vs-${TEAM_B}.log"
: >"$COMBINED_LOG"
echo "Writing matches to ${RUN_DIR}"

run_side() {
  local a=$1
  local b=$2
  local replay="${RUN_DIR}/${a}-vs-${b}.bc26"
  if [[ -n "${COMPARE_CP:-}" ]]; then
    local java_cmd="${JAVA_CMD:-java}"
    "$java_cmd" \
      --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
      --add-opens=java.base/jdk.internal.math=ALL-UNNAMED \
      --add-opens=java.base/jdk.internal.util=ALL-UNNAMED \
      --add-opens=java.base/jdk.internal.access=ALL-UNNAMED \
      --add-opens=java.base/sun.security.action=ALL-UNNAMED \
      -Dbc.server.wait-for-client=false \
      -Dbc.server.mode=headless \
      -Dbc.server.map-path=maps \
      -Dbc.server.robot-player-to-system-out=false \
      -Dbc.server.debug=false \
      -Dbc.engine.debug-methods=false \
      -Dbc.engine.enable-profiler=false \
      -Dbc.engine.show-indicators=true \
      -Dbc.game.team-a="$a" \
      -Dbc.game.team-b="$b" \
      -Dbc.game.team-a.language=java \
      -Dbc.game.team-b.language=java \
      -Dbc.game.team-a.url="${COMPARE_CLASSES:-build/classes}" \
      -Dbc.game.team-b.url="${COMPARE_CLASSES:-build/classes}" \
      -Dbc.game.team-a.package="$a" \
      -Dbc.game.team-b.package="$b" \
      -Dbc.game.maps="$MAPS" \
      -Dbc.server.validate-maps=true \
      -Dbc.server.alternate-order=false \
      -Dbc.server.save-file="$replay" \
      -cp "$COMPARE_CP" \
      battlecode.server.Main \
      -c=- | tee -a "$COMBINED_LOG"
  else
    ./gradlew runLocal \
      -PteamA="$a" \
      -PteamB="$b" \
      -PlanguageA=java \
      -PlanguageB=java \
      -Pmaps="$MAPS" \
      -PwaitForClient=false \
      -PalternateOrder=false \
      -PvalidateMaps=true \
      -PoutputVerbose=false \
      -Pdebug=false \
      -PenableProfiler=false \
      -PshowIndicators=true \
      -Preplay="$replay" | tee -a "$COMBINED_LOG"
  fi
}

if [[ "${SKIP_GRADLE:-}" != "1" ]]; then
  ./gradlew classes
fi
run_side "$TEAM_A" "$TEAM_B"
run_side "$TEAM_B" "$TEAM_A"

python3 - "$TEAM_A" "$TEAM_B" "$COMBINED_LOG" <<'PY'
import re
import sys
from collections import Counter

team_a, team_b, path = sys.argv[1], sys.argv[2], sys.argv[3]
header = re.compile(r"\[server\] (\S+) vs\. (\S+) on (\S+)")
win = re.compile(r"\[server\]\s+(\S+) \(([AB])\) wins \(round (\d+)\)")
reason = re.compile(r"\[server\] Reason: (.*)")

games = []
current = None
with open(path) as f:
    for line in f:
        h = header.search(line)
        if h:
            current = {"a": h.group(1), "b": h.group(2), "map": h.group(3)}
            continue
        w = win.search(line)
        if w and current is not None:
            current["winner"] = w.group(1)
            current["side"] = w.group(2)
            current["round"] = w.group(3)
            games.append(current)
            current = None
            continue
        r = reason.search(line)
        if r and games and "reason" not in games[-1]:
            games[-1]["reason"] = r.group(1).strip()

print()
print("== Games ==")
if not games:
    print("No finished matches found in", path)
    sys.exit(1)
for g in games:
    why = g.get("reason", "")
    print(
        f"{g['map']:28} {g['a']} vs {g['b']}  "
        f"{g['winner']} ({g['side']})  r{g['round']}  {why}"
    )

wins = Counter(g["winner"] for g in games)
print()
print("== Wins ==")
for name in (team_a, team_b):
    print(f"{name:28} {wins.get(name, 0)}")
other = sorted(set(wins) - {team_a, team_b})
for name in other:
    print(f"{name:28} {wins[name]}")
print(f"{'games':28} {len(games)}")
PY
