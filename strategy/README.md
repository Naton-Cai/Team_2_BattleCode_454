---
description: Current Battlecode 2026 bot behavior and priorities.
---

# Strategy

## Strategy overview

The bot prioritizes survival, resource collection, and controlled expansion.

Rat kings establish the team’s anchor. They publish their location, collect nearby cheese, and create baby rats while preserving a cheese reserve. Baby rats collect cheese, return toward the published king location, and react to nearby cats before pursuing routine objectives.

### Rat king priorities

Each turn, a rat king follows this order:

1. Publish its current center in shared memory.
2. Pick up legal nearby cheese.
3. Respond to nearby cats when its configured health policy requires it.
4. Attack enemy rats only when trapped during backstabbing.
5. Spawn a baby rat, when under the population cap and reserve permits.
6. Dig for dirt when spawning does not occur.
7. Move toward the nearest sensed cheese, or continue safe exploration.

Kings maintain a reserve of 800 cheese. This protects recovery and replacement spawns after casualties. Spawning stops at the configured baby-rat cap.

### Baby rat priorities

Baby rats follow a simpler role:

1. Pick up reachable cheese.
2. Bite an adjacent cat when a legal attack is available.
3. Block and flee from a visible cat when they cannot bite.
4. Dig only when the cat response does not need the action.
5. Move toward cheese or return toward the king.

The default cat response is `BITE_THEN_BLOCK_AND_FLEE`. A baby rat places dirt on the first step toward the nearest cat. It then takes one legal greedy step away. It does not bury an allied trap.

### Navigation

The bot uses a lightweight navigation mix:

* **Greedy movement** chases cheese within the current view.
* **DistBug** returns baby rats toward the published king location.
* **Visible BFS** remains available for local detours through known terrain.

Movement checks current legality before each step. Kings account for their full footprint when moving and spawning. When blocked, they select a legal alternative rather than forcing an illegal move.

### Combat and cooperation

Cats are the primary threat. Babies attack adjacent cats before fleeing. Kings flee cats at or below their configured health threshold. A king otherwise preserves its turn for spawning and resource collection.

Enemy rats are attacked only during backstabbing. A king bites an enemy rat on the Chebyshev-two ring only when it cannot flee. This avoids disrupting normal cooperation behavior.

### Current limits

The behavior has focused controller and engine coverage. Several engine scenarios retain at least 1,000 bytecodes of headroom. Match-level evidence is not yet available, so win rate and long-game performance remain unverified.
