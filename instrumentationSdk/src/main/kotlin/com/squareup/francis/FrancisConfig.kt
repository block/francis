package com.squareup.francis

import androidx.test.platform.app.InstrumentationRegistry

/** Current Francis instrumentation configuration. */
object FrancisConfig {
  private val instrumentation
    get() = InstrumentationRegistry.getInstrumentation()

  private val args
    get() = InstrumentationRegistry.getArguments()

  val appPackage: String
    get() = args.getString(OVERRIDE_APP_PACKAGE_ARG) ?: instrumentation.targetContext.packageName

  val instrumentationPackage: String
    get() = instrumentation.context.packageName

  val overrideDisableTarget: String?
    get() = args.getString(OVERRIDE_DISABLE_ARG)

  val overrideIterations: Int?
    get() = args.getString(ITERATIONS_ARG)?.toIntOrNull()

  val profiler: String?
    get() = args.getString(PROFILER_ARG)

  // https://developer.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args#additional-test-output
  val additionalTestOutputDir: String?
    get() = args.getString(ADDITIONAL_TEST_OUTPUT_DIR_ARG)

  val simpleperfOutputDir: String?
    get() = args.getString(SIMPLEPERF_OUTPUT_DIR_ARG)

  val simpleperfCallGraph: String?
    get() = args.getString(SIMPLEPERF_CALL_GRAPH_ARG)

  val perfettoConfigPath: String?
    get() = args.getString(PERFETTO_CONFIG_PATH_ARG)

  internal const val OVERRIDE_DISABLE_ARG = "francis.overrideDisable"
  internal const val OVERRIDE_APP_PACKAGE_ARG = "francis.overrideAppPackage"
  internal const val ITERATIONS_ARG = "francis.overrideIterations"
  internal const val PROFILER_ARG = "francis.profiler"
  internal const val ADDITIONAL_TEST_OUTPUT_DIR_ARG = "additionalTestOutputDir"
  internal const val SIMPLEPERF_OUTPUT_DIR_ARG = "simpleperfOutputDir"
  internal const val SIMPLEPERF_CALL_GRAPH_ARG = "simpleperfCallGraph"
  internal const val PERFETTO_CONFIG_PATH_ARG = "francis.perfettoConfigPath"
}
