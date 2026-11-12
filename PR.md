# Add Spawner baby role and explicitly requested king formation

Adds `BabyRole.SPAWNER` and the coordination needed for seven newborn babies to form a Rat King.
This extends the original baby-role work into recruitment, placement, communication, and promotion.
**Strategy does not trigger it automatically:** a king must call `RatKing.requestKingFormation()`.

## Changes

- [BabyRole.java](src/ratplayer/BabyRole.java) and [BabyRat.java](src/ratplayer/BabyRat.java): add role dispatch with Spawner as the only specialized role. Unassigned babies keep existing behavior.
- [KingFormation.java](src/ratplayer/KingFormation.java): handles recruitment, site selection, assigned tiles, squeaks, and promotion.
- [RatKing.java](src/ratplayer/RatKing.java): exposes the request method and connects successful births and promoted kings to formation handling.
- [Controller tests](test/ratplayer/KingFormationTest.java), [engine fixtures](test/ratplayer/KingFormationEngineTest.java), and `.ai` notes cover behavior, failures, and validation.

Existing king strategy, spawning caps/reserves, registry behavior, and the shared turn lifecycle remain in place.

## How formation works

1. A king explicitly requests formation. Its next successful newborn is chosen; the following six are fodder. Failed births do not advance recruitment.
2. Birth squeaks assign the Spawner role and rendezvous. Spawners skip ordinary gathering, delivery, combat, traps, and digging.
3. The chosen rat selects a fully sensed, passable, empty 3×3 site. It favors separation from known kings, then open approaches, then shorter travel distance.
4. The chosen occupies the center and announces readiness. Fodder forwards announcements, moves to assigned tiles, and reports arrival:

   ```text
   · 1 2
   · C 3
   6 5 4
   ```

5. After six fresh arrival reports and a visible-nonmember check, the chosen calls `canBecomeRatKing()` before promoting. The surrounding fodder is consumed.
6. Next-turn type dispatch runs normal king behavior, releases the formation lock, and publishes an independent registry entry.

## Communication details

- **Only shared slot 52 is used:** `0` means free; `1–1023` identifies the active group. It stores no IDs, coordinates, or recruitment count. **Slots 53–63 remain free.**
- Birth squeaks carry newborn position, chosen/fodder index, and rendezvous. Ready/arrival squeaks carry group token, index, and center. Sender IDs, positions, and rounds come from engine message metadata.
- Failed birth sends pause recruitment and retry through an exact-ID invitation followed by `HELLO`/`ASSIGN`. Normal successful sends need no handshake.
- Chosen and fodder share the same Spawner role; index `0` identifies the chosen rat. All persistent role/group state is robot-local.

## Validation

```sh
./gradlew spotlessApply classes test spotlessCheck
```

Passed on engine **1.2.6**, Java **21.0.13.0.3**, Gradle **8.10**: **266 passed, 2 existing manual BFS cases skipped**, including 20 formation controller tests and three engine fixtures. Coverage and formatting gates passed. Tests verify reclaimed slots remain untouched and failed-squeak recovery works.

| Observed full-turn peak | Bytecodes | Limit |
| --- | ---: | ---: |
| Baby | **9,730** | 17,500 |
| King | **7,766** | 20,000 |

The baby peak includes chosen-rat site selection; it is not a recurring cost for every fodder rat. These are fixture observations, not universal bounds.

On the same `DefaultSmall` matchup against `examplefuncsplayer`, formation occurred on **round 31**, versus round 53 before placement improvements. The one-slot protocol retained round-31 formation. Temporary triggers/logging were removed; no general speed or win-rate claim is made.

Replay and exact commands: `matches/spawner-one-slot-20261111/README.md` and `default-small.bc26`.
Test reports: `build/test-results/test/TEST-ratplayer.KingFormation*.xml`.
These artifacts are Git-ignored and need attaching separately for teammates.

## Limits

Only one group can be active. Site selection is a local heuristic; centers stay fixed, and relocation, dead-member replacement, cancellation, and dead-parent recovery remain deferred. A failed group can hold the lock indefinitely. Unseen nonmembers entering the footprint can also be consumed by promotion.

Choosing **when** to request expansion and adding other specialized roles are future strategy work.
