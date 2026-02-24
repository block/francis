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

## Development
You can use `scripts/francis` to build and run francis during development. If you don't have a specific app/instrumentation that you want to test it with, you can use `scripts/francis-demo` - it's the same as `scripts/francis` but it includes predefined app/instrumentation apks.
