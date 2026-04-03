package com.squareup.francis

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Snapshot of the current Francis instrumentation configuration.
 */
class FrancisConfig internal constructor(
    val appPackage: String,
    val instrumentationPackage: String,
    args: Bundle,
) {
    val overrideDisableTarget: String? = args.getString(OVERRIDE_DISABLE_ARG)
    val overrideIterations: Int? = args.getString(ITERATIONS_ARG)?.toIntOrNull()
    val profiler: String? = args.getString(PROFILER_ARG)

    // https://developer.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args#additional-test-output
    val additionalTestOutputDir: String? = args.getString(ADDITIONAL_TEST_OUTPUT_DIR_ARG)
    val simpleperfOutputDir: String? = args.getString(SIMPLEPERF_OUTPUT_DIR_ARG)
    val simpleperfCallGraph: String? = args.getString(SIMPLEPERF_CALL_GRAPH_ARG)
    val perfettoConfigPath: String? = args.getString(PERFETTO_CONFIG_PATH_ARG)

    companion object {
        internal const val OVERRIDE_DISABLE_ARG = "francis.overrideDisable"
        internal const val ITERATIONS_ARG = "francis.overrideIterations"
        internal const val PROFILER_ARG = "francis.profiler"
        internal const val ADDITIONAL_TEST_OUTPUT_DIR_ARG = "additionalTestOutputDir"
        internal const val SIMPLEPERF_OUTPUT_DIR_ARG = "simpleperfOutputDir"
        internal const val SIMPLEPERF_CALL_GRAPH_ARG = "simpleperfCallGraph"
        internal const val PERFETTO_CONFIG_PATH_ARG = "francis.perfettoConfigPath"

        val current: FrancisConfig
            get() {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                return FrancisConfig(
                    appPackage = instrumentation.targetContext.packageName,
                    instrumentationPackage = instrumentation.context.packageName,
                    args = InstrumentationRegistry.getArguments(),
                )
            }
    }
}
