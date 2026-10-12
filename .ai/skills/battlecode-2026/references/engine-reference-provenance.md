# Engine Reference Provenance

**Last updated:** 2026-10-12 (America/Los_Angeles).

Place this file at `.ai/skills/battlecode-2026/references/engine-reference-provenance.md`. Repository paths below are relative to the repository root. This is a project record, not an official Battlecode specification.

## 1. Reported Repository Versions

| Component | Version | Repository source | Evidence |
| --- | --- | --- | --- |
| Battlecode engine | `1.2.6` | `engine_version.txt` | Reported by Jacob Campbell on 2026-10-12. |
| Battlecode client | `1.0.7` | `client_version.txt` | Reported by Jacob Campbell on 2026-10-12. |

The assistant has not inspected these repository files or resolved build dependencies. These values record the user's current configuration report. The engine version identifies the target for rule/API/reference verification; the client version is recorded separately and does not determine bot sandbox rules.

## 2. Reported Build Evidence

| Field | Recorded value |
| --- | --- |
| Command | `./gradlew build` |
| Outcome | Successful, reported by Jacob Campbell. |
| Date | 2026-10-12 |
| Working directory | Not separately recorded; confirm repository root when reproducing. |
| Source commit / working-tree changes | Not recorded. |
| Java / Gradle versions | Not recorded. |
| Build log / task results | Not supplied. |

Do not infer which tests executed, whether tasks were cached, or whether a match ran from this success report alone. No instrumented-match evidence is recorded here. Add the reusable command and its prerequisites to `.ai/testing.md` when that guide is created; keep detailed run evidence with the relevant ticket or execution record.

## 3. Engine Reference Verification

The skill's documented specification baseline extends through engine v1.2.6. The reported version agrees with that baseline label; this does not by itself verify copied package lists, method costs, API signatures, or the resolved engine artifact.

The following files are intended to reside beside this document. Their presence in the team's repository has not been established.

| Reference | Upstream revision / artifact | Retrieval date | Verification status |
| --- | --- | --- | --- |
| `AllowedPackages.txt` | Not recorded. | Not recorded. | User supplied a candidate list; not checked against engine 1.2.6. |
| `DisallowedPackages.txt` | Not recorded. | Not recorded. | User supplied a candidate list; not checked against engine 1.2.6. |
| `MethodCosts.txt` | Not recorded. | Not recorded. | User supplied a candidate table; unresolved inconsistencies prevent adoption. |

The candidate method table includes flag/crumb APIs and duplicate `MapInfo/getSpawnZoneTeam` rows with costs 2 and 3. Preserve this as an unresolved verification issue; do not choose a value, infer its year, or treat the table as authoritative for engine 1.2.6 without matching source evidence.

To complete verification:

1. Inspect the build configuration and resolved dependency to confirm which engine artifact is actually used.
2. Obtain all three reference files from that artifact or its matching official source revision. Do not substitute current `master` or another Battlecode year.
3. Record the exact source URL/path, tag and commit where available, artifact identity, and retrieval date. Preserve upstream contents; checksums may be recorded for byte-for-byte identification.
4. Compare the candidate lists/table with those files and resolve the discrepancies using engine evidence. Record the reviewer, date, and comparison result before marking a reference verified.

## 4. Sources & Maintenance

- Jacob Campbell's repository-version and successful-build report, 2026-10-12.
- User-supplied package lists, method table, and restrictions, 2026-10-12; engine compatibility unverified.
- Battlecode skill revision 1.3.0, §§1.1, 4.3, and 5: version discipline and evidence requirements.
- `AGENTS.md` §§2 and 5: inspect repository facts and distinguish reported, executed, and unverified work.

Update this record when engine/client pins change or reference verification is completed. Retain the distinction between a user-reported result and independently inspected evidence. Never mark historical results as evidence for a new engine revision without revalidation.
