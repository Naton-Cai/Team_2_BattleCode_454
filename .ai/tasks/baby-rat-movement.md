# Baby Rat Movement

These requirements describe what `ratplayer` does. `examplefuncsplayer` is outside this behavior and keeps its current wander.

A baby rat whose raw cheese is at or below `Config.RAW_CHEESE_MINIMUM` (currently 0) takes the closest cheese it can currently see, or steps toward it. A baby rat whose raw cheese is above that minimum ignores further piles and carries that cheese to the allied king. The king publishes its center so a baby rat can read that location from anywhere.

## King location

Each king publishes its center before it wanders.

| Slot | Meaning |
| --- | --- |
| 0 | Center x |
| 1 | Center y |
| 2 | `1` once a king has published |

Slot 2 equal to `0` means unpublished. A later king overwrites these three slots. A dead king leaves the last write in place. No other slots are used.

A baby rat reads this location only once its raw cheese is above `Config.RAW_CHEESE_MINIMUM`, including after it picks up cheese on that same turn.

## Baby rat

On each turn, a baby rat does the following.

1. If its raw cheese is above `Config.RAW_CHEESE_MINIMUM` and a king location has been published, and the game allows the handoff, it gives that king the entire stash.
2. If its raw cheese is at or below `Config.RAW_CHEESE_MINIMUM`, it looks at the cheese it can currently see. It chooses the closest pile. Equal distances keep the earlier sense result. If the game allows pickup, it takes the whole pile. A baby rat above the minimum does not look for more cheese.
3. It then makes one movement decision, using whatever raw cheese it holds after any pickup or handoff:
   - Raw cheese above `Config.RAW_CHEESE_MINIMUM`, and a king location is published: one step toward that king.
   - Otherwise, if it chose a pile and did not pick it up: one step toward that pile.
   - Otherwise: wander.

After a successful handoff that leaves raw cheese at or below the minimum, the rat looks for cheese on that same turn.

If no king has published, a rat above the minimum does not invent a location. It wanders.

## One step

Cheese policy is two configs. The starting pair is nearest-visible targeting plus greedy walking (`NEAREST_VISIBLE` + `GREEDY`). King-return pathing is a third config so DistBug on the king does not change cheese.

| Config | Starting value | Role |
| --- | --- | --- |
| `Config.BABY_CHEESE_TARGET_STRATEGY` | `NEAREST_VISIBLE` | Which visible pile to chase. Only finder implemented: closest by squared distance; ties keep the earlier sense result. |
| `Config.BABY_PATH_TO_CHEESE_STRATEGY` | `GREEDY` | One step toward that pile. |
| `Config.BABY_PATH_TO_KING_STRATEGY` | `GREEDY` | One step toward the published king. |

`ratplayer.navigation.Navigation.stepToward` takes the pathing config the caller passes and `switch`es to `GreedyPath`, `DistBugPath`, or `VisibleBfsPath` in the same package. Shared follow state and BFS buffers stay on `Navigation`. Baby rats and kings can call it; RatKing still uses its own move loop. A later change may pick configs from game state.

| Pathing helper | When it helps | What it does |
| --- | --- | --- |
| `GREEDY` | Open ground; current cheese and king-return baseline | Direction toward the goal, then left/right pairs, then the opposite. First legal step. If none, turn to face the goal. |
| `VISIBLE_BFS` | Visible cheese, or a king already in the vision cone | BFS only over this turn's sensed passable tiles. Unknown tiles are not walkable. Falls back to greedy when the visible graph has no path. |
| `DIST_BUG` | Published king behind unseen walls | Seek until a *known* wall, dirt, or map edge blocks the heading. Follow with a fixed hand. Leave only when the heading is open and squared distance is strictly less than at the hit. Unsensed and occupied-but-passable tiles do not start follow. |

Standing on the goal already means no move and no turn. A goal turn does not also wander.

Skipped for now: Bug0 (loops in U-shapes), Bug1 (walks the whole perimeter), classic Bug2 m-line (weaker when the king moves), tangent bug, potential fields, and full-map A* (they plan through tiles the rat cannot sense). Extra cheese finders are not implemented.

## Wander

Wander is the existing rule. The rat moves forward when the game allows it. Otherwise it turns to one random facing from the seeded sequence.

## Limits

- No search past the cheese the rat can currently see.
- Obstacle paths use only the pathing helper selected for that goal (`BABY_PATH_TO_CHEESE_STRATEGY` or `BABY_PATH_TO_KING_STRATEGY`). DistBug and visible BFS treat a tile as known only when it appears in this turn's `senseNearbyMapInfos` or is off the map.
- No separate published location per king.
