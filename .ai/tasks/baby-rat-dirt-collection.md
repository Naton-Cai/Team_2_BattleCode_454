# Baby Rat Dirt Collection

Status: **proposed** (2026-10-26). Not implemented or verified.

These requirements describe what `ratplayer` baby rats do. `examplefuncsplayer` is outside this behavior.

The original feature was "Baby Rat will place dirt to block off cats." This stage covers only digging dirt into the team stash, so a later placement feature has dirt to use. Placing dirt is deferred.

## Engine rules

Rules come from the supplied `specs.md`, with amendments through engine v1.2.6. Page numbers are original PDF pages.

- A rat can dig only on a directly adjacent tile inside its vision cone (pp. 7, 13). For a baby rat facing `d`, those tiles are `loc.add(d)`, `loc.add(d.rotateLeft())`, and `loc.add(d.rotateRight())`. `GameConstants.BUILD_DISTANCE_SQUARED` is 2.
- Each dig costs 5 cheese (`GameConstants.DIG_DIRT_CHEESE_COST`). Raw cheese is spent first, then global cheese (p. 8). Each dig adds 25 action cooldown (`GameConstants.DIG_COOLDOWN`) (p. 13).
- Dug dirt goes straight into the team-wide stash, which `rc.getDirt()` reports (pp. 7, 13).
- Biting also uses the action cooldown, and an action is allowed only while the cooldown is below 10 (p. 10). So a rat makes at most one bite or dig per turn.
- The legality check `canRemoveDirt(MapLocation)` runs right before `removeDirt(MapLocation)`, with no heavy work between them (p. 18). A thrown exception costs 500 bytecodes (p. 20).

## Baby rat

On each turn, a baby rat does the following.

1. It hands cheese to the king and picks up cheese, as described in `.ai/tasks/baby-rat-movement.md`. This part is unchanged.
2. It tries each action in `Config.BABY_ACTION_ORDER` in list order and stops after the first one that succeeds. An action left out of the list does not run.
   - `ATTACK`: the existing bite of an adjacent cat, or of an enemy rat after a backstab. This part is unchanged.
   - `DIG_DIRT`: described below.
3. It makes its movement decision. This part is unchanged. Movement runs after the action, so a tile freed by digging can be stepped into on the same turn.

### Dig dirt

- The rat checks the forward tile, then the left tile, then the right tile. It digs the first one where `rc.canRemoveDirt(loc)` is true, and it digs at most once per turn.
- The rat digs only if the global cheese left after paying stays at or above `Config.MIN_CHEESE_RESERVE_FOR_DIG`. That value is `rc.getGlobalCheese() - max(0, DIG_DIRT_CHEESE_COST - rc.getRawCheese())`.
- If `removeDirt` throws a `GameActionException`, the exception reaches the existing `RobotPlayer.playTurn` boundary. The rat does not retry on that turn, and the next turn runs normally.

## Configs

| Config | Starting value | Role |
| --- | --- | --- |
| `Config.BABY_ACTION_ORDER` | `{ATTACK, DIG_DIRT}` | Order in which a baby rat tries its actions. Values come from the new `Config.BabyAction` enum. |
| `Config.MIN_CHEESE_RESERVE_FOR_DIG` | `800` | Lowest global cheese allowed after a dig is paid for. Same as the starting `MIN_CHEESE_RESERVE` spawn reserve, but set separately. |

`Config.reset()` restores both values.

Both values are team strategy settings, not engine rules. The team agreed on 2026-10-26 that the dig reserve lives in `Config`. Changing its value there does not change these requirements. Tests that depend on the boundary use the configured value.

## Planned code changes

- `src/ratplayer/Config.java`: add `enum BabyAction { ATTACK, DIG_DIRT }`, `BABY_ACTION_ORDER`, `MIN_CHEESE_RESERVE_FOR_DIG`, and their `reset()` values.
- `src/ratplayer/BabyRat.java`: `attack` returns `boolean`. Add `digDirt(rc)`, which returns `boolean`. Replace the direct `attack(rc)` call in `run` with a loop over `BABY_ACTION_ORDER`.

## Out of scope

- Placing dirt, including placing it to block cats.
- Choosing which dirt to dig based on cats, or digging only dirt that blocks a path.
- Digging any tile other than the three adjacent tiles in front of the rat.
- Rat king digging.

## Test cases

Named checks live in `.ai/test-cases.md` under Baby Rat dirt collection. Do not add them to `.ai/project-state.md`.

## Acceptance

- Each requirement above has at least one passing test from the dirt-collection tables in `.ai/test-cases.md`.
- The existing `BabyRatAttackTest` cases still pass with the default order.
- Engine tests show the dirt and cheese changes and the bytecode headroom. Match evidence is not required for this stage. If it is not run, report it as **not run**.
