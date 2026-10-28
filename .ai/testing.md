# Battlecode 2026 Testing Guidance

Store this file at `.ai/testing.md`. Run commands from the repository root. Follow `AGENTS.md` for scope, instruction precedence, and handoffs; use the Battlecode skill for engine rules and `.ai/style/java.md` for style. This guide supplies commands and evidence procedures. The task contract selected under AGENTS.md §2 owns feature scenarios and expected outcomes; this guide does not add feature requirements.

**Use this guide:** New setup: §§1 and 7. Daily feature work: focused tests in §3, relevant match/scenario checks in §§4-5, then the evidence record in §6. Read sources in §8 only when verifying a detail.

## 1. Evidence & Environment

**Research date:** 2026-10-12. The official scaffold was inspected at commit `f69e2ab872a0061c9d4a684aa1dd798a0829da85`. Its configuration is evidence about that upstream revision, not proof that the team's modified repository is identical. Compare your local build files before adopting commands. [S1-S4]

| Item | Known state | Evidence |
| --- | --- | --- |
| Team engine | `1.2.6` in root `engine_version.txt` | User-reported, 2026-10-12. |
| Team client | `1.0.7` in root `client_version.txt` | User-reported, 2026-10-12. |
| Team build | `./gradlew build` succeeded | User-reported; task report and logs not supplied. |
| Upstream Java | Source/target 21; build rejects a Gradle JVM older than 21 | Inspected `build.gradle`. |
| Upstream wrapper | Gradle 8.10 | Inspected wrapper configuration. |
| Upstream Java tests | JUnit 4.13.2; sources under `test/` | Inspected dependency and source-set configuration. |
| Existing test | `examplefuncsplayer.RobotPlayerTest.testSanity` | Checks arithmetic only; no movement coverage. |
| JaCoCo | 0.8.12 in root `jacoco.gradle` | Measures and enforces minimum code coverage |
| Formatter | Spotless 8.10.4 with google-java-format 1.36.1 in root `spotless.gradle` | `./gradlew spotlessCheck` fails when Java files that differ from `origin/main` do not match the formatter. |
| Engine reference tables | Not verified against 1.2.6 | See engine-reference provenance record. |

The inspected upstream pins are engine 1.2.4/client 1.0.6. Preserve the team's reported 1.2.6/1.0.7 pins; do not replace them with upstream defaults. The research environment had Java 17, so build, JUnit, verifier, and match commands were **not run during preparation**. Commands below were checked against source/configuration and Gradle documentation, not executed against the team's repo. [S1-S4, S9, P1]

Use the existing wrapper and a compatible JDK; Java 21 is the proposed common team baseline for this scaffold. Record the actual vendor and patch version. In WSL Ubuntu use `./gradlew`; in Windows PowerShell use `./gradlew.bat`. Avoid switching environments within a comparison without recording it.

Initial inspection:

```bash
git rev-parse HEAD
git status --short
java -version
./gradlew --version
./gradlew version
./gradlew tasks --all
./gradlew dependencyInsight --dependency battlecode26-java --configuration runtimeClasspath
```

Keep the resolved engine at the intended version. Do not run `./gradlew update` during routine validation: that task rewrites version pins from the release service. A version change is a separate reviewed change requiring renewed reference and runtime checks. `runLocal` does not mean offline; uncached dependencies may still need downloading. [S1]

## 2. What Each Check Establishes

| Layer | Purpose | Limit |
| --- | --- | --- |
| Style review/checker | Formatting and documented conventions. | Does not prove Java compiles or actions are legal. |
| Compilation | Source and dependency/API compatibility. | Does not execute gameplay. |
| JUnit | Specific expected decisions and regressions. | Ordinary tests do not reproduce engine instrumentation. |
| Engine verifier | Basic validation of the selected bot package. | Does not replace scenarios exercising actual behavior. |
| Instrumented match | Real geometry, cooldowns, sandbox behavior, and observed runtime costs. | A completed match does not prove every acceptance scenario occurred. |
| Strategy comparison | Outcomes against a fixed baseline/opponent. | Winning does not establish rule compliance. |

Choose evidence proportional to the change. Documentation-only changes need document/reference review. Gameplay changes need focused behavior checks and instrumented scenarios. Performance or strategy changes also need controlled comparisons. Expand the suite for concrete risks, not every feature listed in the specification. [P2-P3]

## 3. Build & JUnit Commands

Use these commands after confirming the local scaffold configuration matches the relevant inspected tasks:

| Command | Use |
| --- | --- |
| `./gradlew classes` | Compile production classes and process resources. |
| `./gradlew spotlessCheck` | Fail if Java files that differ from `origin/main` do not match google-java-format 1.36.1. Does not rewrite files, compile, or run tests. |
| `./gradlew spotlessApply` | Rewrite those files to match the formatter, including expanding wildcard imports. |
| `./gradlew test --tests 'examplefuncsplayer.RobotPlayerTest'` | Run the existing starter test class. |
| `./gradlew test` | Run the configured Java unit-test suite. Also writes the JaCoCo report and fails when line coverage is below threshold. |
| `./gradlew check` | Run configured verification tasks, including `spotlessCheck` and the coverage gate through `test`; not a substitute for a match. |
| `./gradlew build` | Run the normal build lifecycle, including tests, unless skipped/reused; this scaffold also downloads/unpacks the client. |
| `./gradlew verify -Pteam=examplefuncsplayer` | Invoke the engine verifier; the scaffold makes this depend on `build`. |
| `./gradlew jacocoTestReport` | Write the HTML/XML line-coverage report. `test` already runs this afterward. |
| `./gradlew jacocoTestCoverageVerification` | Fail when the JaCoCo LINE covered ratio is below threshold. |

Spotless lives in `spotless.gradle` and is applied from `build.gradle` after `sourceSets`. Pins are Spotless 8.10.4 and google-java-format 1.36.1. The Java target is `src/**/*.java` and `test/**/*.java`; `.ai` API copies are not formatted. `ratchetFrom 'origin/main'` limits the gate to Java files that differ from main so a repo-wide reformat can wait until in-flight branches merge. After that apply, remove `ratchetFrom` so every Java file is checked. `spotlessCheck` is attached to `check`, so `./gradlew check` and `./gradlew build` fail on format drift in ratcheted files. CI runs `./gradlew spotlessCheck`. `./gradlew build -x test` still runs `spotlessCheck`.

JaCoCo lives in `jacoco.gradle` and is applied from `build.gradle` after `sourceSets`. The enforced metric is the LINE covered ratio. Reports are `build/reports/jacoco/test/html/index.html` and `build/reports/jacoco/test/jacocoTestReport.xml`. The `test` task finalizes the report and `jacocoTestCoverageVerification`, so `./gradlew test`, `./gradlew check`, and `./gradlew build` fail when the ratio is below threshold. `./gradlew build -x test` skips that gate with the test task.

Replace the package/class with the actual existing target. For a new feature test, create it first and then use its fully qualified name with `--tests`. Preserve JUnit 4 unless the team explicitly migrates; do not add JUnit 5 imports or a mocking dependency without a corresponding scoped build change. [S1, S4-S6]

Inspect test counts and failures, not only `BUILD SUCCESSFUL`. Default results are `build/reports/tests/test/index.html` and `build/test-results/test/`; verify local overrides. `NO-SOURCE` or zero discovered tests is not feature coverage. `UP-TO-DATE` and `FROM-CACHE` mean results were reused, not freshly executed. For a deliberate fresh run, add `--rerun-tasks`; do not make repeated clean rebuilds the default. Do not use `-x test` as acceptance evidence. [S5-S7]

Write tests under `test/<package path>/`, matching package declarations. Give each test explicit setup, one behavior to check, and observable expected results. Increase the use of USEFUL comments (e.g., scenario description, controlling rules, and arrange-act-assert phases) so junior developers can quickly scan and understand the test suite and feature changes. Reset robot-local static state and RNG state where tests share a JVM; production robot isolation does not automatically isolate JUnit methods. Avoid dependence on test execution order. [S1, P2-P3]

Test an existing bounded behavior entry point or decision function. Do not call the infinite `RobotPlayer.run()` loop directly in a normal unit test. A small fake can exercise controller interactions, but must not pretend to prove engine collision rules. Do not create an architecture or broad abstraction layer just to write tests; agree on the smallest test seam the implementation needs. Regression coverage for unchanged behavior does not authorize unrelated production refactoring; use existing entry points or instrumented engine observations. [P2-P3]

## 4. Instrumented Match Workflow

The match runner executes bots in the game engine. The client displays a replay; it is not required to run a headless Java match. The inspected scaffold exposes `run` and `runLocal`, plus map/player discovery. [S1-S2]

```bash
./gradlew listPlayers
./gradlew listMaps
```

After confirming `examplefuncsplayer` and `DefaultSmall` are available, this is a Java-only smoke-run example:

```bash
mkdir -p matches
./gradlew classes
./gradlew runLocal -PteamA=examplefuncsplayer -PteamB=examplefuncsplayer -PlanguageA=java -PlanguageB=java -Pmaps=DefaultSmall -PwaitForClient=false -PalternateOrder=false -PvalidateMaps=true -PoutputVerbose=true -Pdebug=false -PenableProfiler=false -PshowIndicators=true -Preplay=matches/smoke-001.bc26
```

Choose a new replay filename for every retained run; the default name can be reused across runs. Use the team's candidate and opponent package names for feature evaluation. A self-match is a smoke check, not a strategy comparison. Inspect the runner's completion/result, bot errors, and the saved replay before recording success. [S1, P2]

For a feature, select or construct a valid map and setup that actually reaches the required state. Record the map file/checksum, bot setup, round, robot ID, and observed outcome. Do not claim a blocked-footprint scenario passed just because a random match finished. If no reproducible fixture reaches a scenario, mark it **not run** and document the missing fixture. A minimal fixture and replay review are sufficient initially; no advanced match runner is required. [P2-P3]

Preserve logs when collecting evidence. For Bash/WSL, `set -o pipefail` before piping a command through `tee` preserves a failing process status. Record the command's exit status as well as the log. A nonzero result, timeout, or missing replay requires investigation; do not silently count it as a pass or a loss in a strategy sample.

## 5. Derive Coverage from the Task Contract

Use the contract selected under `AGENTS.md` §2 for scenarios, strategy parameters, action ordering, and expected outcomes. Do not copy a previous feature's checklist into the current task. If the contract omits a material acceptance detail, derive a check from the requested outcome or clarify the missing decision; do not invent strategy. [P1, P3]

Map each relevant criterion to the evidence it needs:

| Criterion type | Appropriate evidence | Limit |
| --- | --- | --- |
| Selection, ordering, bounded work, or deterministic decisions | Focused tests through the production path with explicit inputs and expected results. | Avoid copying the implementation into expected results. |
| Geometry, collision, or map limits | An instrumented fixture reaching the specified state. | A fake legality result does not prove engine geometry. |
| Readiness or cooldown effects | Boundary tests using verified engine values and engine observations where affected. | Hardcoded fake state does not independently establish engine rules. |
| Turn completion, recovery, or type changes | Relevant integration observations or a minimal bounded test seam. | Do not call the infinite run loop in ordinary JUnit. |
| Preservation of existing behavior | Targeted regression evidence for affected paths. | Does not authorize restructuring unrelated code. |

A legality query returning false is an expected branch, not an attempted illegal action. Choose only scenarios required by the contract or a concrete risk introduced by the change. Derive numeric engine boundaries from the pinned engine and strategy values from the task contract. [P1-P3]

Measure the complete turn where feasible, not only a helper. Keep legality checks close to actions. Bytecode counters reset across rounds: if a measurement spans a round change, flag a cutoff/invalid measurement rather than subtracting counters as if it were one turn. Record diagnostics overhead, peak observed costs, and limitations. Neither a small code diff nor a lack of visible errors proves every runtime limit is satisfied. [P2]

## 6. Repeatable Runs & Evidence Record

Hold code, engine/toolchain, map, opponent, settings, and RNG behavior fixed when reproducing a failure. Compare candidate and baseline using the same chosen map set, including explicit A/B swaps where relevant. Record actual sides; do not assume that `alternateOrder` alone supplies the intended comparison. Preserve established RNG seeds unless the task changes them. [P1-P2]

The inspected Gradle runner does not forward a `-Pseed` property. Do not invent one. Record map identity and any known embedded/default engine seed, plus bot RNG configuration; mark unknown seed details as unknown until verified against the pinned engine. Do not claim byte-identical reproducibility from an unsupported flag. [S1]

Retain this record in the feature ticket or existing evidence location:

```text
Ticket / acceptance criteria:
Run date and tester:
Candidate commit; saved patch/new files if dirty:
Baseline and opponent revisions (if applicable):
Guidance revision:
Engine/client versions; Java vendor/version; Gradle wrapper version; OS:
Exact commands and working directory:
Map names/files/checksums; side assignments:
Engine/map seed status; bot RNG configuration:
Debug/profiler/indicator settings:
Test counts: passed / failed / skipped; reused or freshly executed:
Scenario → expected → observed → evidence path and round/robot ID:
Exceptions/cutoffs; peak measured bytecodes and measurement scope:
Heap/team execution-time evidence, or not measured:
Replay/log/report links:
Unverified criteria, blockers, and reviewer:
```

Keep full logs and replays outside routine model context. Share concise findings with exact excerpts and evidence links. The scaffold ignores `matches/`, build output, and `*.log`; attach selected evidence to the ticket or retain it as CI artifacts before local cleanup. Never assume an ignored file is shared with teammates. [S8, P1]

## 7. Team Adoption & Automation

Before using this guide as a merge checklist:

1. Compare local `build.gradle`, wrapper properties, and `gradle.properties` with the researched scaffold. Record intentional differences and actual Java/Gradle versions.
2. Confirm the resolved engine dependency is 1.2.6; retain client 1.0.7. Complete the package/method-cost provenance check separately.
3. Run the starter JUnit class and confirm its report lists a test. Then implement meaningful tests for the active feature; the arithmetic test is only a discovery check.
4. Run `verify` for the actual bot package and a Java-only smoke match; retain their results. Create fixtures required for the task contract's remaining scenarios.
5. Have a teammate review the relevant replay/trace and acceptance results. Unexecuted required checks remain **not run**; record pre-existing failures separately.
6. Run `./gradlew spotlessCheck` (or rely on CI / `./gradlew check`) for google-java-format. Use `.ai/style/java.md` for naming, Javadoc, sandbox legality, and other rules the formatter does not check.

For later CI integration, use the same wrapper, Java baseline, pins, and verified commands. Start with unit tests and normal build/package verification, then add a bounded smoke match once it works locally. Retain test reports and selected logs/replays on failures; set explicit job timeouts and preserve actual failures. Avoid simultaneous matches writing the same replay. A completed engine process still needs a defined check for bot exceptions and acceptance outcomes. CI configuration and match-result assertions are not installed by this document.

Stop extending verification once the ticket's checks and concrete risks are covered. Broader strategy tournaments, coverage tooling, and new mocking libraries are separate scoped improvements. [P1]

## 8. Guidance Ownership & Sources

This guide owns command recipes, evidence selection procedures, measurements, and the run-record format. Feature-specific scenarios and expected outcomes belong to the task contract; named test cases belong in `.ai/test-cases.md`; versioned rules and sandbox constraints belong to the Battlecode skill. Do not duplicate those requirements here.

Record confirmed decisions and outstanding work in `.ai/project-state.md` when created. Keep numeric game rules and versioned package/method-cost references in the Battlecode skill. This guide does not expand or replace the task contract's acceptance criteria.

Sources were reviewed on 2026-10-12. The official scaffold was read directly from the pinned Git checkout where web rendering of individual files was unavailable.

- **S1:** [Scaffold build.gradle, pinned revision](https://github.com/battlecode/battlecode26-scaffold/blob/f69e2ab872a0061c9d4a684aa1dd798a0829da85/build.gradle): source sets, dependencies, Java requirement, custom task definitions, and runner properties.
- **S2:** [Scaffold gradle.properties](https://github.com/battlecode/battlecode26-scaffold/blob/f69e2ab872a0061c9d4a684aa1dd798a0829da85/gradle.properties): default teams/map and match settings.
- **S3:** [Gradle wrapper properties](https://github.com/battlecode/battlecode26-scaffold/blob/f69e2ab872a0061c9d4a684aa1dd798a0829da85/gradle/wrapper/gradle-wrapper.properties): Gradle 8.10.
- **S4:** [Starter RobotPlayerTest.java](https://github.com/battlecode/battlecode26-scaffold/blob/f69e2ab872a0061c9d4a684aa1dd798a0829da85/test/examplefuncsplayer/RobotPlayerTest.java): JUnit sanity test.
- **S5:** [Gradle Java testing documentation](https://docs.gradle.org/current/userguide/java_testing.html): filtering and result reporting. Current documentation is supplemental; do not adopt newer-only APIs into the 8.10 wrapper.
- **S6:** [Gradle Java plugin documentation](https://docs.gradle.org/current/userguide/java_plugin.html): lifecycle tasks and default test output locations.
- **S7:** [Gradle command-line documentation](https://docs.gradle.org/current/userguide/command_line_interface.html): rerunning tasks and execution status; use wrapper-compatible options.
- **S8:** [Scaffold .gitignore](https://github.com/battlecode/battlecode26-scaffold/blob/f69e2ab872a0061c9d4a684aa1dd798a0829da85/.gitignore): generated evidence exclusions.
- **S9:** [Upstream engine pin](https://github.com/battlecode/battlecode26-scaffold/blob/f69e2ab872a0061c9d4a684aa1dd798a0829da85/engine_version.txt) and [upstream client pin](https://github.com/battlecode/battlecode26-scaffold/blob/f69e2ab872a0061c9d4a684aa1dd798a0829da85/client_version.txt): upstream defaults, distinct from the user's reported versions.
- **P1:** `AGENTS.md` §§2-5; `.ai/style/java.md`; user-reported versions/build and `references/engine-reference-provenance.md` under the Battlecode skill.
- **P2:** Battlecode skill revision 1.3.0 §§2, 3.3, 4.2-4.3; its `references/specs.md`, physical pp. 9-14, 18, 20-22, 25-26 for relevant mechanics and runtime constraints.
- **P3:** The active task contract selected under `AGENTS.md` §2: the current user request and any task documents it references. Feature scenarios are task requirements, not an official engine test suite.
