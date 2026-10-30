# Rat King Low-Health Cat Block-and-Flee

When a rat king's health is at or below a configurable threshold and a cat is visible, the king places blocking dirt and walks the other way. Baby-rat cat policy is unchanged. Kings are not at pounce-kill risk: a cat cannot land on a king (spec p. 15) and kills kings by scratching (20 HP).

## Engine rules

Rules come from the supplied `specs.md`, with amendments through engine v1.2.6. Page numbers are original PDF pages.

- Kings can place dirt on tiles with `r² <= 8` from the 3x3 center, the same range as digging (pp. 7, 13–14).
- Placing dirt costs no cheese and uses the 25 action cooldown (`GameConstants.DIG_COOLDOWN`) (pp. 8, 13).
- Dirt comes from the team stash. The engine refuses occupied tiles, including the king's own footprint (p. 13).
- Spawn, dig, and place dirt share the action cooldown (p. 10).
- `canPlaceDirt(MapLocation)` runs right before `placeDirt(MapLocation)` (p. 18).

## King

On each turn, a rat king does the following.

1. It identifies cheese mines, as before.
2. It applies `Config.KING_CAT_RESPONSE_STRATEGY` from the shared `CatResponseStrategy` enum:
   - `NONE`: no dirt block and no flee. The rest of the turn is unchanged.
   - `BLOCK_AND_FLEE_WHEN_LOW_HEALTH` (default): if `rc.getHealth() <= Config.KING_CAT_FLEE_HEALTH` and a cat is visible, place dirt on the spawn-ring tile toward the nearest cat (`center + 2 * sign(delta)`), then take one step the opposite way if movement is ready. A successful move republishes the king center. Spawn and generic digging do not run on that turn.
   - `BITE_THEN_BLOCK_AND_FLEE`: same dirt-and-flee path when a cat is visible, with no HP gate. Kings do not bite, so this never waits on a missed attack.
3. If that policy did not handle the turn, existing movement runs.
4. Cheese pickup still runs after movement.
5. If the cat policy did not fire, spawn still beats dig as in `.ai/tasks/baby-rat-dirt-collection.md`.

Nearest cat is by squared distance. Equal distances keep the earlier `senseNearbyRobots` result.

`Dirt.tryPlace` is the shared `canPlaceDirt` / `placeDirt` pair. A `GameActionException` from `placeDirt` reaches the existing `RobotPlayer.playTurn` boundary.

## Configs

| Config | Starting value | Role |
| --- | --- | --- |
| `Config.KING_CAT_RESPONSE_STRATEGY` | `BLOCK_AND_FLEE_WHEN_LOW_HEALTH` | Which `CatResponseStrategy` a king uses. |
| `Config.DEFAULT_KING_CAT_RESPONSE_STRATEGY` | `BLOCK_AND_FLEE_WHEN_LOW_HEALTH` | Default `reset()` assigns. |
| `Config.KING_CAT_FLEE_HEALTH` | `600` | HP gate for `BLOCK_AND_FLEE_WHEN_LOW_HEALTH` on a king. Full king HP because kings do not attack. |

`Config.reset()` restores these values.

## Engine bytecode

`RatKingCatAvoidanceEngineTest` runs one instrumented turn with a low-health king and a visible cat. The full turn must leave at least 1,000 bytecodes under the 20,000 king limit. LiveMap cat waypoints are required to construct a cat in the sandbox. `InternalRobot` health is set with `addHealth` because LiveMap bodies do not apply the `RobotInfo` health field.
