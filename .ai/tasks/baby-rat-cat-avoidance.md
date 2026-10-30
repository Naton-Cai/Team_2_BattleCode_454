# Baby Rat Cat Block-and-Flee

Baby rats still bite an adjacent cat in the facing cone. When a cat is visible and that bite did not happen, the rat places blocking dirt and walks the other way. Kings are unchanged.

## Engine rules

Rules come from the supplied `specs.md`, with amendments through engine v1.2.6. Page numbers are original PDF pages.

- Digging and placing dirt share vision and adjacency with each other (pp. 7, 13). For a baby rat facing `d`, those tiles are `loc.add(d)`, `loc.add(d.rotateLeft())`, and `loc.add(d.rotateRight())`.
- Placing dirt costs no cheese (`GameConstants.PLACE_DIRT_CHEESE_COST` is 0) and uses the same 25 action cooldown as digging (`GameConstants.DIG_COOLDOWN`) (pp. 8, 13).
- Dirt comes from the team stash (`rc.getDirt()`). The engine refuses occupied tiles, walls, existing dirt, cheese, and cheese mines (p. 13).
- Biting, digging, placing dirt, and placing traps share the action cooldown. An action is allowed only while the cooldown is below 10 (p. 10).
- The legality check `canPlaceDirt(MapLocation)` runs right before `placeDirt(MapLocation)`, with no heavy work between them (p. 18).

## Baby rat

On each turn, a baby rat does the following.

1. It hands cheese to the king and picks up cheese, as described in `.ai/tasks/baby-rat-movement.md`. This part is unchanged.
2. It tries each action in `Config.BABY_ACTION_ORDER` in list order and stops after the first one that succeeds.
   - `ATTACK`: the existing bite of an adjacent cat, or of an enemy rat after a backstab. This part is unchanged.
   - `DIG_DIRT`: skipped when `BABY_CAT_RESPONSE_STRATEGY` is `BITE_THEN_BLOCK_AND_FLEE` and at least one cat is visible, so the action stays available for a dirt block. Otherwise `Dirt.digInVisionCone` as in `.ai/tasks/baby-rat-dirt-collection.md`.
3. It then applies `Config.BABY_CAT_RESPONSE_STRATEGY`:
   - `NONE`: no dirt block and no flee. The rest of the turn is unchanged.
   - `BITE_THEN_BLOCK_AND_FLEE` (default): if a cat is visible and no bite happened this turn, place dirt on the first Chebyshev step toward the nearest cat, then take one greedy step the opposite way. A successful bite suppresses both the dirt block and the flee; king, cheese, or wander movement runs as before.
4. Existing cat-trap placement still runs after that action phase. If dirt was placed, the trap does not fire that turn because the action is spent.
5. If the cat strategy already moved, the usual movement decision does not run.

Nearest cat is by squared distance. Equal distances keep the earlier `senseNearbyRobots` result, the same tie rule as cheese.

`Dirt.tryPlace` is the shared `canPlaceDirt` / `placeDirt` pair. A `GameActionException` from `placeDirt` reaches the existing `RobotPlayer.playTurn` boundary. The unit does not retry on that turn, and the next turn runs normally.

## Configs

| Config | Starting value | Role |
| --- | --- | --- |
| `Config.BABY_CAT_RESPONSE_STRATEGY` | `BITE_THEN_BLOCK_AND_FLEE` | Which visible-cat policy a baby rat uses. |
| `Config.DEFAULT_BABY_CAT_RESPONSE_STRATEGY` | `BITE_THEN_BLOCK_AND_FLEE` | Default `reset()` assigns. |

`Config.reset()` restores these values. Later policies can be added to `Config.CatResponseStrategy` and dispatched from `BabyRat.respondToCats`.

## Engine bytecode

`BabyRatCatAvoidanceEngineTest` runs one instrumented turn with a visible non-adjacent cat. The full turn must leave at least 1,000 bytecodes under the 17,500 baby-rat limit. LiveMap cat waypoints are required to construct a cat in the sandbox.
