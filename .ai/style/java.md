# Java Style Guidance

Use this file at `.ai/style/java.md`. Apply Google Java Style to new and modified Java code wherever it is compatible with repository instructions, the active task, and verified Battlecode constraints. This is the proposed team baseline requested on 2026-10-12; it does not claim prior team approval. All repository paths below are relative to the repository root.

## 1. Authority & Conflict Handling

Follow the root `AGENTS.md` for instruction precedence, scope, and approval requirements. Use `.ai/skills/battlecode-2026/SKILL.md` for game rules, sandbox restrictions, and runtime budgets. Use the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) for remaining style decisions.

When a concrete conflict exists, follow the applicable higher-priority instruction or verified engine requirement. Briefly identify the conflicting style rule, controlling requirement, and smallest necessary exception in the change report. A personal preference or an existing inconsistency is not automatically an exception.

Preserve required engine entry points, signatures, package compatibility, and public behavior. Do not change architecture, dependencies, or Java language level to satisfy a style preference. Formatting does not justify changing direction order, RNG seeds, action order, or other strategy behavior.

Source: `AGENTS.md` §§1-4; Battlecode skill §§1, 2, 4; user's conditional Google-style request dated 2026-10-12.

## 2. Google Style Defaults

Use the full official guide for details and exceptions. The following is a short review checklist, not a replacement for it:

| Area | Default | Google guide section |
| --- | --- | --- |
| Encoding | UTF-8; spaces for indentation. | §2 |
| File structure | One top-level class per ordinary source file; matching filename. | §§2.1, 3.4 |
| Imports | Explicit imports; no wildcard imports, including static imports. | §3.3 |
| Import order | Static group first, then non-static; ASCII ordering within each, with one blank line between groups. | §3.3.3 |
| Blocks | Two-space indentation; braces for `if`, `else`, `for`, `do`, and `while`. | §§4.1-4.2 |
| Statements | One statement per line. | §4.3 |
| Wrapping | 100-character limit with documented exceptions; continuation indentation of at least four spaces. | §§4.4-4.5 |
| Naming | Follow Google's category-specific naming rules and constant definition. | §5 |
| Documentation | Follow Google's Javadoc requirements and exceptions. | §7 |

Source: [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html), reviewed 2026-10-12. The upstream guide can change; record a reviewed source revision when the team formally adopts or updates this baseline.

## 3. Applying Style to Existing Code

Apply these defaults to new files and changed code. Preserve unrelated code rather than reformatting the repository during feature work. If a configured formatter affects a whole file, inspect the diff and keep the formatting scope reviewable; separate a broad formatting migration from behavior changes.

The supplied starter `RobotPlayer.java` and JUnit test use wildcard imports. For imports changed as part of a scoped edit, use explicit classes or static members. Do not rename the starter package, replace the test framework, or modify behavior merely to modernize the scaffold.

Document non-obvious game constraints with specification page/version references. Explain why an exception or performance-sensitive implementation exists. Avoid comments that merely repeat the code, unsupported performance claims, and mandatory documentation templates that add no useful information.

Keep Java code examples in shared documentation consistent with this guide when those examples are edited. Existing examples do not silently override the style baseline.

Source: `AGENTS.md` §§4-5; Google guide §3.3; supplied starter files; project review policy.

## 4. Sandbox Lists & Method Costs

Allowed packages, disallowed classes, restricted methods, and bytecode costs are engine constraints. Maintain their canonical references with the Battlecode skill, not as duplicated lists in this style file.

Recommended destinations, to be populated from the repository's pinned engine:

| Reference | Intended repository location |
| --- | --- |
| Allowed packages | `.ai/skills/battlecode-2026/references/AllowedPackages.txt` |
| Disallowed classes | `.ai/skills/battlecode-2026/references/DisallowedPackages.txt` |
| Method costs | `.ai/skills/battlecode-2026/references/MethodCosts.txt` |
| Provenance and verification status | `.ai/skills/battlecode-2026/references/engine-reference-provenance.md` |

These paths are planned locations, not confirmation that the files exist or are verified. Keep their exact upstream contents and record engine version, upstream commit or artifact identity, source path/URL, retrieval date, and verification status. Use the same engine revision for all three files. Link them from the skill when added. Do not substitute current `master` for the installed version.

Apply these checks when selecting imports or dependencies:

- An allowed package does not authorize a disallowed class within it, nor every method on an allowed class. Check the class exclusions and method-specific restrictions as well.
- Slash-separated entries such as `java/io/File` are engine reference notation; Java imports use dotted names such as `java.io.File`. Preserve the original notation in reference files.
- Check code reached through helpers and dependencies, not just visible imports. Compilation does not prove sandbox legality.
- Sandbox permission does not establish runtime efficiency. Follow the skill's bytecode and memory guidance; an allowed stream, regex, or collection may still be unsuitable for a hot path.
- Bot restrictions apply inside the instrumented game, including bot dependencies. Host-side tests and development tools have a separate environment; keep test-only dependencies out of bot runtime code.
- Do not interpret the boolean column in a method-cost file without the matching instrumenter documentation. A zero listed charge does not mean unlimited calls or zero total computation cost.

The user's package lists and method table supplied on 2026-10-12 have **not been matched to the repository's pinned engine**. The method table contains flag/crumb APIs and lists `MapInfo/getSpawnZoneTeam` twice with different costs (2 and 3). Do not promote it to an authoritative 2026 cost table, silently resolve duplicates, or infer supported APIs from it. Retrieve and verify the matching engine files first.

The supplied restrictions also prohibit `Object.wait`, `Object.notify`, `Object.notifyAll`, `Class.forName`, and `String.intern`; restrict `System` to `out`, `arraycopy`, and `getProperty` for `bc.testing.*`; and prohibit opening files through `PrintStream`. Keep these restrictions under the skill's sandbox guidance and verify them with the pinned engine. Broad package permission does not override them.

Sources: Battlecode skill §§1.1, 2.1-2.2, 4.2; user-supplied lists and restrictions dated 2026-10-12. Engine compatibility remains unverified.

## 5. Enforcement & Review

This document supplies review rules; Markdown alone does not install an automated checker.

Use existing repository formatting and style-check commands when available. Record their exact commands, tool versions, configuration, and scope in `.ai/testing.md`. Do not invent a Gradle task or claim a check passed without running it. If no checker exists, perform a manual review against §2 and report that limitation.

When the team adopts automated enforcement, pin the formatter/checker versions and configuration, use the same configuration locally and in CI, and make intentional exceptions reviewable. Select tools compatible with the existing toolchain; do not add plugins or dependencies as part of an unrelated feature. Automated formatting does not establish compliance with naming, documentation, sandbox rules, or gameplay requirements.

Before completing a Java change, check its style, necessary exceptions, import legality, and diff scope. Report style checks separately from compilation, unit tests, and engine matches. Leave architecture decisions to the active ticket and team agreement.

Source: `AGENTS.md` §§2, 4-5; `.ai/README.md` §§4-6; project policy for reproducible validation.
