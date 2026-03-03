# script-module

Utilities for creating Kotlin CLIs, with a focus on subprocess execution and logging.

This module powers Francis internals, but it is designed to be usable in any
JVM CLI app. In the future, script-module may be separated into another repo.

## Core API

The main entry point is `SubProc` from `com.squareup.francis.script.process`.

Callers should create one or more shared instances and reuse them:

```kotlin
val subproc = SubProc(...)
```

## Logging Model

Logging is provided by `com.squareup.francis.script.logging` and built on top of [logcat](https://github.com/square/logcat).

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

`FailedExecException` includes the command line, exit code, and captured stdout/stderr (when configured) so callers can fail loudly and deterministically.

## Quickstart

```kotlin
import com.squareup.francis.script.process.SubProc

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

- input source (`Pipe`, `Null`, `Inherit`, file, stream)
- output targets (`Capture`, `Inherit`, file, stream)
- working directory and environment
- command display text via `commandRepr`
- environment overlays

### ProcessBuilder Pipe vs TeeProcessBuilder Capture

In Java `ProcessBuilder`, `Redirect.PIPE` is the `Pipe` mode used for process streams.

In `TeeProcessBuilder`, the equivalent for `stdout` / `stderr` is `OutputTarget.Capture`, not `Pipe`.

- `ProcessBuilder.redirectOutput(Redirect.PIPE)` -> `TeeProcessBuilder.stdoutRedirect = OutputRedirectSpec(listOf(OutputTarget.Capture))`
- `ProcessBuilder.redirectError(Redirect.PIPE)` -> `TeeProcessBuilder.stderrRedirect = OutputRedirectSpec(listOf(OutputTarget.Capture))`
- `ProcessBuilder.redirectInput(Redirect.PIPE)` -> `TeeProcessBuilder.stdinRedirect = InputRedirectSpec.PIPE`

`Pipe` still exists in `TeeProcessBuilder`, but only for **stdin** input via `InputRedirectSpec.PIPE`. For process output, use `Capture`.

The naming is intentional because semantics differ:

- `ProcessBuilder` `Pipe` means "leave this stream as a raw pipe endpoint" and the caller is responsible for draining it.
- `TeeProcessBuilder` `Capture` means "actively drain output and store a copy in memory" so output can be read later, read multiple times, and included in fail-fast exceptions.
- `Capture` can be combined with other output targets (for example file/stream teeing), while plain `Pipe` does not express that higher-level tee behavior.

```kotlin
val builder = TeeProcessBuilder("cat").apply {
  stdinRedirect = InputRedirectSpec.PIPE
  stdoutRedirect = OutputRedirectSpec(listOf(OutputTarget.Capture))
}

val proc = builder.start()
proc.stdinWriter.use { it.write("hello\n") }
val output = proc.stdoutText()
```

### Inherit Constraints

`Inherit` cannot be combined with tee targets for the same stream.

Examples of invalid combinations:

- `stdout = Inherit + ToFile(...)`
- `stderr = Inherit + Pipe`
- `stdin = Inherit` with tee outputs

These combinations throw `IllegalArgumentException` during process startup.

## Compatibility

- runtime target: Java 17+
- module bytecode target: JVM 17

## XDG Paths

`com.squareup.francis.script.xdg.Xdg` exposes XDG base directory helpers for CLIs:

- `Xdg.dataHome`
- `Xdg.configHome`
- `Xdg.cacheHome`
- `Xdg.stateHome`

## Tests

Run:

```bash
./gradlew :script:test
```

The tests cover subprocess redirection/tee behavior, fail-fast semantics, and logging behavior.
