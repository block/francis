package com.squareup.francis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BenchmarkArgsTest {
  @Test
  fun buildInstrumentationArgsList_includesResolvedAppPackageOverride() {
    val runnerVals = object : RunnerValues {
      override val base: BaseValues = object : BaseValues {
        override val verbosity = logcat.LogPriority.INFO
        override val devMode = false
      }
      override val appApkOrNull: String? = "/tmp/app.apk"
      override val appPackageOrNull: String? = "com.example.override"
      override val instrumentationApkOrNull: String? = "/tmp/instrumentation.apk"
      override val testSymbolOrNull: String? = "com.example.Benchmark#startup"
      override val runnerClass: String = "androidx.test.runner.AndroidJUnitRunner"
      override val suppressErrors: Boolean = false
      override val aot: Boolean = true
      override val dryRun: Boolean = false
      override val instrumentationArgs: Map<String, String> = emptyMap()
      override val hostOutputDir: String = "/tmp/output"
      override val overrideIterations: Int? = null
      override val profiler: String? = null
      override val simpleperfCallGraph: String? = null
      override val perfettoConfigPath: String? = null
    }

    val argsList = buildInstrumentationArgsList(
      runnerVals = runnerVals,
      deviceOutputDir = "/sdcard/output",
      simpleperfOutputDir = null,
      devicePerfettoConfigPath = null,
    )

    assertThat(argsList).containsAtLeast(
      "-e", "francis.overrideAppPackage", "com.example.override",
    )
  }
}
