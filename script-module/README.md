# script-module

Utilities for creating Kotlin CLIs, with a focus on subprocess execution and logging.

This module powers Francis internals, but it is designed to be usable in any
JVM CLI app. In the future, script-module may be separated into another repo.

## Core API

The main entry point is `SubProc` from `com.squareup.francis.process`.

Callers should create one or more shared instances and reuse them:

```kotlin
val subproc = SubProc(...)
```

## Logging Model

Logging is provided by `com.squareup.francis.logging` and built on top of [logcat](https://github.com/square/logcat).

`setupLogging(minPriority, logPath)` establishes two behaviors:

- all logs are written to the log file at `logPath`
- console output is filtered by `minPriority`

Before setupLogging is called, console output is filtered by `LogPriority.INFO`.

The long-term goal is Android-compatible logging behavior, but Android compatibility is not yet complete.

## Fail-Fast Process Execution

`SubProc` and `TeeProcess` are designed for fail-fast command execution.

- pass `allowedExitCodes = listOf(...)` to accept specific codes
- pass `allowedExitCodes = null` to allow any exit code
- otherwise non-allowed exits throw `FailedExecException`

`FailedExecException` includes the command line and exit code so callers can fail loudly and deterministically.

## Quickstart

```kotlin
import com.squareup.francis.process.SubProc

val subproc = SubProc()

// Fail if command exits non-zero.
subproc.run("git", "status")

// Capture stdout (newline chomped by default).
val branch = subproc.stdout("git", "branch", "--show-current")
println("branch=$branch")

// Accept non-zero codes explicitly.
subproc.run("grep", "needle", "file.txt", allowedExitCodes = listOf(0, 1))
```

## Advanced Configuration

When you need lower-level control, use `TeeProcessBuilder`.

You can configure:

- input source (`PIPE`, `NULL`, `INHERIT`, file, stream)
- output targets (pipe, file, stream)
- working directory and environment
- command display text via `commandRepr`
- environment overlays

### INHERIT Constraints

`INHERIT` cannot be combined with tee targets for the same stream.

Examples of invalid combinations:

- `stdout = INHERIT + ToFile(...)`
- `stderr = INHERIT + Pipe`
- `stdin = INHERIT` with tee outputs

These combinations throw `IllegalArgumentException` during process startup.

## Compatibility

- runtime target: Java 17+
- module bytecode target: JVM 17

## Tests

Run:

```bash
./gradlew :script:test
```

The tests cover subprocess redirection/tee behavior, fail-fast semantics, and logging behavior.
