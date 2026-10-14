# francis
> "Observation and experiment for gathering material, induction and deduction for elaborating it: these are our only good intellectual tools."

*- Francis Bacon (1561-1626), English philosopher and statesman, regarded as the father of empiricism*

A CLI for rigorous A/B performance testing on Android.

## Why Francis?

Francis wraps
[Android Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview),
providing a simple CLI that:
- installs APKs, configures instrumentation args, runs benchmarks, and
  retrieves results
- monitors logcat and parses instrumentation output to provide actionable error
  messages

Francis also provides commands to
- run [A/B tests](https://en.wikipedia.org/wiki/A/B_testing) of macrobenchmarks and
  determine whether differences are statistically meaningful.
- collect simpleperf/perfetto traces

## Usage
```sh
# See list of supported commands:
francis --help

# See detailed docs for a specific command (e.g. bench):
francis bench --help

# Collect macrobenchmark results
francis bench --app app.apk --instrumentation benchmark.apk --test-symbol 'com.example.Example#benchmarkMethod'

# Collect results of an A/B test of a macrobenchmark
francis ab --instrumentation benchmark.apk --test-symbol 'com.example.Example#benchmarkMethod' \
  --baseline-opts baseline-version-of-app.apk \
  --treatment-opts treatment-version-of-app.apk

# Compare two sets of macrobenchmark results (e.g. result of `ab` command above)
francis compare baseline-results.json treatment-results.json

# Collect a manual perfetto trace (no instrumentation)
francis perfetto

# Collect a manual simpleperf trace (no instrumentation)
francis simpleperf

# Collect a perfetto trace of an instrumentation scenario
francis perfetto --app app.apk --instrumentation benchmark.apk --test-symbol 'com.example.Example#benchmarkMethod'

# Collect a simpleperf trace of an instrumentation scenario
francis simpleperf --app app.apk --instrumentation benchmark.apk --test-symbol 'com.example.Example#benchmarkMethod'
```

## Installation
```sh
brew install block/tap/francis
```

## Integrating with the Instrumentation SDK
Basic benchmark and A/B test usage works without the instrumentation SDK, but some features (modifying iteration count, perfetto/simpleperf trace collection of instrumentation scenarios) require modifying your instrumentation apk. Assuming you created your macrobenchmark in the style of [the official docs](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview#create-macrobenchmark), you'll make the following changes:

1. Add a dependency on `com.squareup.francis:instrumentation-sdk`. In your instrumentation apk's `build.gradle` (Groovy syntax):
```groovy
dependencies {
  androidTestImplementation "com.squareup.francis:instrumentation-sdk"
}
```

or `build.gradle.kts` (Kotlin syntax):

```kotlin
dependencies {
  androidTestImplementation("com.squareup.francis:instrumentation-sdk")
}
```

2. Replace `MacrobenchmarkRule` with `FrancisBenchmarkRule`:

```diff
- import androidx.benchmark.macro.junit4.MacrobenchmarkRule
+ import com.squareup.francis.FrancisBenchmarkRule

...

    @get:Rule
-   val benchmarkRule = MacrobenchmarkRule()
+   val benchmarkRule = FrancisBenchmarkRule()
```

This allows Francis to hook into [measureRepeated](https://developer.android.com/reference/kotlin/androidx/benchmark/macro/junit4/MacrobenchmarkRule#measureRepeated(kotlin.String,kotlin.collections.List,androidx.benchmark.macro.CompilationMode,androidx.benchmark.macro.StartupMode,kotlin.Int,kotlin.Function1,kotlin.Function1)) invocations and modify arguments to, e.g:
- change iteration count
- surround the measureBlock parameter with code to start/stop perfetto/simpleperf in order to capture traces

3. Optionally use `@Disable` (from the instrumentation SDK) instead of `@Ignore`:

```kotlin
import com.squareup.francis.Disable

@Disable("TRACKER-123")
@Test
fun expensiveBenchmark() {
  // ...
}
```

`@Disable` skips the test by default, but Francis automatically sets `francis.overrideDisable`
from `--symbol`. This way you disable a test in CI, but still run it manually with Francis.

If `@Disable` is on a class, target that class (`com.example.BenchmarkClass`) to override it.
If `@Disable` is on a method, target that method
(`com.example.BenchmarkClass#expensiveBenchmark`) to override it.

## Development
You can use `scripts/francis` to build and run francis during development. If you don't have a specific app/instrumentation that you want to test it with, you can use `scripts/francis-demo` - it's the same as `scripts/francis` but it includes predefined app/instrumentation apks.

### Trailblaze demo

`scripts/francis-trailblaze-demo` bundles the demo app and a separate self-instrumenting
benchmark APK that drives it with recorded [Trailblaze](https://github.com/block/trailblaze)
trails. It requires an Android 9 (API 28) or newer device, with the screen unlocked.
No LLM provider, API key, or Trailblaze CLI is required. LLM calls and self-healing
are disabled, and a missing or broken recording fails the test.

Generate or refresh recordings with a configured Trailblaze LLM, outside the
benchmark, after installing the demo app:

```sh
demo_device=emulator-5554
demo_trails=demo-trailblaze-instrumentation/src/main/assets/trails
# NEW_TASK | CLEAR_TASK: reset the activity while retaining the app process.
adb -s "$demo_device" shell am start -W -f 0x10008000 \
  -n com.squareup.francis.demoapp/.MainActivity
trailblaze run "$demo_trails/setup.trail.yaml" --device "android/$demo_device" \
  --no-use-recorded-steps --self-heal --save-recording --max-llm-calls 5
trailblaze run "$demo_trails/show-details.trail.yaml" --device "android/$demo_device" \
  --no-use-recorded-steps --self-heal --save-recording --max-llm-calls 5
```

Trailblaze saves its generated actions into each YAML's `recording:` block. Steps
without recordings use initial AI execution; self-heal recovers from failures in
existing recordings. The benchmark replays the saved actions with both disabled.

On API 28, the benchmark grants the legacy storage permissions Trailblaze needs
for screenshots and logs.

The Trailblaze version is pinned in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml). That Maven release
(`0.1.0-2026.10.06`) omits Android variants of some shared Trailblaze libraries,
causing `NoClassDefFoundError` before replay. Build against a source checkout
compatible with tag `v2026.10.06` using Gradle's composite-build substitution.
The checkout must include Trailblaze's
[accessibility service cleanup](https://github.com/block/trailblaze/commit/bcbc625b669dca91b1f0f76fc78767c7aec00fa2)
and [API 28 snapshot refresh](https://github.com/block/trailblaze/commit/d2d7a7fc5a65a96f142760c0e79aacaf949247b9)
fixes, so repeated runs can reconnect and snapshots reflect the current screen.

Point Francis at that checkout:

```sh
export ORG_GRADLE_PROJECT_trailblazeCheckout=/path/to/trailblaze
```

This checkout needs Trailblaze's build prerequisites, including Bun for its
bundled scripted tools. The option also supports testing subsequent source changes.

```sh
# Smoke-test the integration on an emulator.
scripts/francis-trailblaze-demo bench --dev --dry-run

# Run multiple measured iterations on an emulator (integration validation only).
scripts/francis-trailblaze-demo bench --dev --iterations 3

# Collect results on a physical device.
scripts/francis-trailblaze-demo bench --iterations 30
```

Each iteration resets the demo activity to its home screen while retaining the
app process, then ensures Trailblaze's accessibility service is ready in
`setupBlock`. The setup trail taps **Browse items** and selects **Coffee**;
the measured trail taps **Show details** and verifies **Coffee details**. The
selection is a prerequisite for the measured interaction, so it belongs outside
measurement. A process reset would only be needed for a scenario that deliberately
measures behavior in a fresh process.
`TraceSectionMetric` reports the app's `Demo.showDetails` handler duration, so
the reported metric excludes the replay harness's screenshots, selectors, and
logging. That work still runs during measurement and may affect the app, so this
demo proves the integration; it does not validate measurement equivalence with
UiAutomator. Kotlin owns the benchmark settings and metric; the UI flow lives in
`demo-trailblaze-instrumentation/src/main/assets/trails/`.

The demo initializes AndroidX Benchmark's shell before binding the accessibility
service and restores the service after iteration setup. Francis's shell executor
also preserves accessibility services, including during simpleperf collection.
The Trailblaze dependency is pinned because its `0.x` API can change between releases.

This repo provides a repo-local pre-push hook at `.hooks/pre-push`. In environments with shared hook dispatch configured (for example via `core.hooksPath`), it checks only the Kotlin files introduced by the push with `ktfmt`, so it won't block on older unformatted files elsewhere in the repo.

CI also enforces formatting because `build` runs `check`, and `check` depends on `ktfmtCheck`.
