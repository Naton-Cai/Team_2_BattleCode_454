---
description: >-
  How the Battlecode 2026 Java scaffold separates bot code, validation, runtime
  assets, and project guidance.
---

# Repository design layout

This repository separates gameplay code from validation, local runtime assets, and team guidance. Keep changes within the layer that owns the concern.

```
repository root/
├── .ai/                 # Shared engineering guidance and project state
├── src/                 # Java bot source packages
├── test/                # Player test code
├── client/              # Battlecode client executables
├── maps/                # Custom maps for local matches
├── matches/             # Generated match outputs
├── gradle/              # Gradle wrapper support files
├── build.gradle         # Build and run configuration
├── gradle.properties    # Project-wide configuration
├── gradlew              # Gradle wrapper for macOS and Linux
├── gradlew.bat          # Gradle wrapper for Windows
└── README.md            # Project entry point
```

### Gameplay code

Place each bot in its own Java package under `src/`. Start from `examplefuncsplayer` when creating a bot. Keep the turn loop in `RobotPlayer`. Put bounded behavior in handlers and helpers.

Read the applicable guidance before changing behavior:

* `.ai/README.md` indexes the repository guidance.
* `.ai/skills/battlecode-2026/SKILL.md` defines engine and bot constraints.
* `.ai/style/java.md` defines Java conventions.

### Validation and local runs

Put player tests in `test/`. Use the Gradle wrapper for builds, tests, matches, and submission archives. Configuration shared by local commands belongs in `gradle.properties`.

`client/` contains the executable used for local games. Do not move it. Store custom maps in `maps/`. Generated replays and match artifacts go in `matches/`.

### Generated and managed files

Treat `build/` and `matches/` as generated output. Do not edit them as source. The Gradle wrapper scripts and `gradle/` directory are managed build infrastructure. Change them only when updating the build toolchain.

### Change boundaries

Keep feature changes local to their owning package. Add or update focused tests with the behavior change. Update `.ai/project-state.md` only when a decision or handoff state materially changes.

For the repository workflow and constraints, see [AGENTS.md](../AGENTS.md "mention").
