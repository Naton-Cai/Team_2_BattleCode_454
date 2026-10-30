# Dirt Collection

## Engine rules

Rules come from the supplied `specs.md`, with amendments through engine v1.2.6. Page numbers are original PDF pages.

- Digging must be in vision and adjacent to the unit (pp. 7, 13).
- For a baby rat facing `d`, those tiles are `loc.add(d)`, `loc.add(d.rotateLeft())`, and `loc.add(d.rotateRight())`. `GameConstants.BUILD_DISTANCE_SQUARED` is 2.
- A rat king has 360-degree vision and a 3x3 footprint. It can dig tiles within `GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED` (8) of its center, i.e. distance `(3/2 + √2)` (p. 7).
- Each dig costs 5 cheese (`GameConstants.DIG_DIRT_CHEESE_COST`). Raw cheese is spent first, then global cheese (p. 8). Each dig adds 25 action cooldown (`GameConstants.DIG_COOLDOWN`) (p. 13).
- Dug dirt goes straight into the team-wide stash, which `rc.getDirt()` reports (pp. 7, 13).
- Biting, spawning, and digging all use the action cooldown. An action is allowed only while the cooldown is below 10 (p. 10). So a unit makes at most one of those per turn.
- The legality check `canRemoveDirt(MapLocation)` runs right before `removeDirt(MapLocation)`, with no heavy work between them (p. 18). A thrown exception costs 500 bytecodes (p. 20).

## Shared dig rules

- The unit digs only if the global cheese left after paying stays at or above `Config.MIN_CHEESE_RESERVE_FOR_DIG`. That value is `rc.getGlobalCheese() - max(0, DIG_DIRT_CHEESE_COST - rc.getRawCheese())`.
- It digs at most once per turn, at the first legal candidate.
- If `removeDirt` throws a `GameActionException`, the exception reaches the existing `RobotPlayer.playTurn` boundary. The unit does not retry on that turn, and the next turn runs normally.

## Baby rat

On each turn, a baby rat does the following.

1. It hands cheese to the king and picks up cheese, as described in `.ai/tasks/baby-rat-movement.md`. This part is unchanged.
2. It tries each action in `Config.BABY_ACTION_ORDER` in list order and stops after the first one that succeeds. An action left out of the list does not run.
   - `ATTACK`: the existing bite of an adjacent cat, or of an enemy rat after a backstab. This part is unchanged.
   - `DIG_DIRT`: `Dirt.digInVisionCone` — forward, then left, then right.
3. Existing cat-trap placement still runs after that action phase.
4. It makes its movement decision. Movement runs after the action, so a tile freed by digging can be stepped into on the same turn.

## Rat king

On each turn, a rat king still identifies mines, moves, and picks up cheese first. Spawning and digging share the action cooldown:

1. It tries to spawn, as before.
2. If spawning did not happen, it calls `Dirt.digAroundKing`. Candidates are the 24 tiles with `r² <= 8` from the center, excluding the center itself. Order: the eight spawn-ring tiles clockwise from north (the same tiles spawning checks), then the remaining in-range tiles. `canRemoveDirt` skips walls, the king's own footprint, and tiles out of range.

A king that can spawn still spawns. Dirt is collected when spawn is refused (cap, cheese reserve, blocked tiles, or action not ready).

## Configs

| Config | Starting value | Role |
| --- | --- | --- |
| `Config.BABY_ACTION_ORDER` | `{ATTACK, DIG_DIRT}` | Order in which a baby rat tries its actions. |
| `Config.DEFAULT_BABY_ACTION_ORDER` | `{ATTACK, DIG_DIRT}` | Default array `reset()` assigns. |
| `Config.MIN_CHEESE_RESERVE_FOR_DIG` | `800` | Lowest global cheese allowed after a dig is paid for. Shared by babies and kings. Independent of the spawn reserve. |

`Config.reset()` restores these values.