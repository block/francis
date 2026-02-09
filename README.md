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

Francis also provides commands to run
[A/B tests](https://en.wikipedia.org/wiki/A/B_testing) of macrobenchmarks and
determine whether differences are statistically meaningful.

## Usage
```
# Collect macrobenchmark results
francis bench --app app.apk --instrumentation benchmark-instrumentation.apk --test-symbol 'com.example.MyBenchmark#benchmarkMethod'

# Collect results of an A/B test of a macrobenchmark
francis ab --instrumentation benchmark-instrumentation.apk --test-symbol 'com.example.MyBenchmark#benchmarkMethod' \
  --baseline-opts baseline-version-of-app.apk \
  --treatment-opts treatment-version-of-app.apk

# Compare two sets of macrobenchmark results (e.g. result of `ab` command above)
francis compare baseline-results.json treatment-results.json
```

## Installation
There isn't a clean method to install francis yet. In the future, you'll be
able to install with `brew install block/tap/francis`.

## Development
You can use `scripts/bnr.sh` (build-and-run) to run francis during development.
