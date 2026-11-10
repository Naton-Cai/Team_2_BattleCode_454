# Rat King last-resort bite of an adjacent cat

A rat king bites a cat only when the cat is on the king's attack ring and the king cannot step away, place blocking dirt, or place a cat trap. Fleeing and dirt still come first. Baby-rat cat policy is unchanged.

## Engine rules

Rules come from the supplied `specs.md`, with amendments through engine v1.2.6. Page numbers are original PDF pages.

- A king bites a location with `r² <= 8` from the 3x3 center (`RAT_KING_ATTACK_DISTANCE_SQUARED`). That is the 16-tile ring around the body (pp. 24–25).
- A bite with no cheese deals 10 damage (p. 10).
- Biting a cat does not end cooperation (p. 23).
- Placing dirt and placing a cat trap share the action cooldown with the bite (p. 10).
- A cat trap costs 10 cheese and is legal during cooperation, or for the backstabbed team within 100 rounds of the backstab (p. 13). `canPlaceCatTrap` is the legality check.

## King

On each turn, after the existing cat response:

1. If the king can take any step, it does not bite a cat. Movement cooling down, or every heading illegal, counts as unable to move away. A legal sidestep is still an escape.
2. If the king cannot step, and a backstab is active, the existing enemy-rat bite still runs first.
3. Otherwise the king scans the attack ring clockwise. For the first cat it finds, it looks at the spawn-ring tile toward that cat (the same tile used for blocking dirt).
4. If that tile can take dirt, or can take a cat trap, the king does not bite that cat and keeps scanning.
5. If neither can be placed, and `canAttack(location, 0)` is true, the king bites that ring tile with zero cheese.
6. An issued bite skips spawn and generic digging, same as an enemy-rat bite.

This change does not place the cat trap. A legal trap only suppresses the bite. Dirt placement stays in the existing cat policy.

A cat that is visible but not on the attack ring is not bitten.

## Acceptance

- Stuck king, cat on the north ring, dirt and cat trap illegal: one zero-cheese bite, including during cooperation.
- Cat on a knight-offset ring tile (`r²` of 5): one bite.
- Cat beyond the ring: no bite.
- Any legal step: no bite.
- Blocking dirt legal: dirt is placed by the existing policy, and there is no bite.
- Cat trap legal on the blocking tile: no bite and no trap is placed.
- A thrown bite is contained at the turn boundary and the next turn can bite once.
- One instrumented turn leaves at least 1,000 bytecodes under the 20,000 king limit.
