# Project State & Decision Record

**Last updated:** 2026-10-12 (America/Los_Angeles).

This file records observed implementation and handoff state. It is not a source of additional task requirements. Use `AGENTS.md` §2 to select the authoritative task contract; feature-specific design belongs there.

## 1. Observed Implementation

| Feature | Contract | Observed state | Verification limits |
| --- | --- | --- | --- |
| Rat King Movement | Root `feature.md` | Movement helper and reusable direction table exist. Current-type dispatch calls the helper for kings and leaves non-king starter behavior inline. | Earlier review ran 15 passing tests and package verification before subsequent source edits. Current source and tests have not been rerun in this documentation task; full scenario and complete-turn resource verification remain unestablished. |

These observations describe the current working tree, not a required layout for later features. Consult source for implemented behavior and the task contract for intended behavior.

## 2. Approved Guidance Decision

On 2026-10-12, the user made root `feature.md` authoritative for feature scope, behavior, design, and acceptance. A feature supplied in the current prompt is sufficient when that file is absent. Stable workflow and lifecycle constraints remain in shared guides; the authority and conflict rules are owned by `AGENTS.md` §2.

## 3. Handoff Limits

- Do not treat earlier helper bytecode estimates as verified complete-turn measurements.
- The Battlecode skill file is empty, and the local method-cost reference differs from engine 1.2.6. Restore and verify these before claiming full engine-reference compliance.
- Future features require their own task contract; no backlog item or historical design note authorizes implementation.
