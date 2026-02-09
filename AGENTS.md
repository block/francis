# AGENTS.md

## Build & Test Commands

- **Build**: `./gradlew build`
- **Unit tests**: `./gradlew :hostSdk:test` or `scripts/test.sh`
- **Single test**: `./gradlew :hostSdk:test --tests "com.squareup.francis.AbArgsTest"`
- **Run locally**: `scripts/francis [args]` or `scripts/francis-demo [args]`
- **Release**: `./gradlew releaseArtifacts` (outputs to `build/release/`)

## Architecture

- `hostSdk/` - Core library (`com.squareup.francis`) with CLI, benchmarking, stats, and A/B comparison
- `main/` - The `francis` executable JAR entry point
- `demo/`, `demo-app/`, `demo-instrumentation/` - Demo customization and bundled Android benchmarks
- `prebuilt/` - Wrapper scripts copied into releases
- `scripts/` - Build/run/test helpers

## Code Style

- Kotlin with JVM 17 target, kotlinx-serialization-json for JSON
- Clikt for CLI parsing (see `Commands.kt` for subcommand patterns: `CliktCommand`, `subcommands()`)
- JUnit + Truth assertions: `assertThat(...).containsExactly(...).inOrder()`
- Logging via logcat: `log { }`, `log(WARN) { }`

## Scratchpad

Plans and temporary files can be stored in `.agents/knowledge/` directories (gitignored).
Review these directories for persistent context about ongoing work.