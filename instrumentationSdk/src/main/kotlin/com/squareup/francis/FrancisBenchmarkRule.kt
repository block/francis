package com.squareup.francis

import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.ExperimentalConfig
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class FrancisBenchmarkRule : TestRule {
    private val macrobenchmarkRule = MacrobenchmarkRule()

    override fun apply(base: Statement, description: Description): Statement {
        return macrobenchmarkRule.apply(base, description)
    }

    fun measureRepeated(
        packageName: String,
        metrics: List<Metric>,
        compilationMode: CompilationMode = CompilationMode.DEFAULT,
        startupMode: StartupMode? = null,
        iterations: Int,
        setupBlock: MacrobenchmarkScope.() -> Unit = {},
        measureBlock: MacrobenchmarkScope.() -> Unit
    ) {
        macrobenchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = metrics,
            compilationMode = compilationMode,
            startupMode = startupMode,
            iterations = francisIterations ?: iterations,
            setupBlock = setupBlock,
            measureBlock = wrapMeasureBlock(measureBlock)
        )
    }

    @ExperimentalBenchmarkConfigApi
    fun measureRepeated(
        packageName: String,
        metrics: List<Metric>,
        iterations: Int,
        experimentalConfig: ExperimentalConfig,
        compilationMode: CompilationMode = CompilationMode.DEFAULT,
        startupMode: StartupMode? = null,
        setupBlock: MacrobenchmarkScope.() -> Unit = {},
        measureBlock: MacrobenchmarkScope.() -> Unit
    ) {
        @OptIn(ExperimentalPerfettoCaptureApi::class)
        macrobenchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = metrics,
            iterations = francisIterations ?: iterations,
            experimentalConfig = experimentalConfig,
            compilationMode = compilationMode,
            startupMode = startupMode,
            setupBlock = setupBlock,
            measureBlock = wrapMeasureBlock(measureBlock)
        )
    }

    private fun wrapMeasureBlock(
        measureBlock: MacrobenchmarkScope.() -> Unit
    ): MacrobenchmarkScope.() -> Unit = when (francisProfiler) {
        "simpleperf" -> {
            {
                SimpleperfProfiler(simpleperfOutputDir, simpleperfCallGraph).use { profiler ->
                    profiler.start()
                    measureBlock()
                }
            }
        }
        else -> measureBlock
    }

    companion object {
        private val args by lazy { InstrumentationRegistry.getArguments() }

        private val francisIterations: Int? by lazy {
            args.getString("francis.iterations")?.toIntOrNull()
        }

        private val francisProfiler: String? by lazy {
            args.getString("francis.profiler")
        }

        // https://developer.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args#additional-test-output
        private val additionalTestOutputDir: String by lazy {
            args.getString("additionalTestOutputDir")
                ?: throw IllegalStateException("additionalTestOutputDir not set")
        }

        private val simpleperfOutputDir: String by lazy {
            args.getString("simpleperfOutputDir")
                ?: throw IllegalStateException("simpleperfOutputDir not set")
        }

        private val simpleperfCallGraph: String? by lazy {
            args.getString("simpleperfCallGraph")
        }
    }
}
