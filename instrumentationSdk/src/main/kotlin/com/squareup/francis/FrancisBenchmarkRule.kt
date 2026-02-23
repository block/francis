package com.squareup.francis

import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.ExperimentalConfig
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric.Measurement
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.benchmark.perfetto.PerfettoConfig
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.traceprocessor.TraceProcessor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@OptIn(ExperimentalMetricApi::class)
private class NoOpMetric : TraceMetric() {
    override fun getMeasurements(
        captureInfo: Metric.CaptureInfo,
        traceSession: TraceProcessor.Session
    ): List<Measurement> = listOf(Measurement("francis.placeholder", 0.0))
}

class FrancisBenchmarkRule : TestRule {
    private val macrobenchmarkRule = MacrobenchmarkRule()
    private lateinit var testDescription: Description

    override fun apply(base: Statement, description: Description): Statement {
        testDescription = description
        return macrobenchmarkRule.apply(base, description)
    }

    private val testName: String
        get() = "${testDescription.testClass.simpleName}_${testDescription.methodName}"

    @OptIn(ExperimentalBenchmarkConfigApi::class, ExperimentalPerfettoCaptureApi::class)
    fun measureRepeated(
        packageName: String,
        metrics: List<Metric>,
        compilationMode: CompilationMode = CompilationMode.DEFAULT,
        startupMode: StartupMode? = null,
        iterations: Int,
        setupBlock: MacrobenchmarkScope.() -> Unit = {},
        measureBlock: MacrobenchmarkScope.() -> Unit
    ) {
        measureRepeatedInternal(
            packageName = packageName,
            metrics = metrics,
            iterations = iterations,
            experimentalConfig = ExperimentalConfig(),
            compilationMode = compilationMode,
            startupMode = startupMode,
            setupBlock = setupBlock,
            measureBlock = measureBlock
        )
    }

    @OptIn(ExperimentalPerfettoCaptureApi::class)
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
        measureRepeatedInternal(
            packageName = packageName,
            metrics = metrics,
            iterations = iterations,
            experimentalConfig = experimentalConfig,
            compilationMode = compilationMode,
            startupMode = startupMode,
            setupBlock = setupBlock,
            measureBlock = measureBlock
        )
    }

    @OptIn(ExperimentalBenchmarkConfigApi::class, ExperimentalMetricApi::class, ExperimentalPerfettoCaptureApi::class)
    private fun measureRepeatedInternal(
        packageName: String,
        metrics: List<Metric>,
        iterations: Int,
        experimentalConfig: ExperimentalConfig,
        compilationMode: CompilationMode,
        startupMode: StartupMode?,
        setupBlock: MacrobenchmarkScope.() -> Unit,
        measureBlock: MacrobenchmarkScope.() -> Unit
    ) {
        var effectiveMetrics = metrics
        var effectiveConfig = experimentalConfig
        var effectiveMeasureBlock = measureBlock

        when (francisProfiler) {
            "perfetto" -> {
                effectiveMetrics = listOf(NoOpMetric())
                effectiveConfig = ExperimentalConfig(perfettoConfig = createPerfettoConfig(packageName))
            }
            "simpleperf" -> {
                effectiveMeasureBlock = {
                    SimpleperfProfiler(simpleperfOutputDir, testName, packageName, simpleperfCallGraph).use { profiler ->
                        profiler.start()
                        measureBlock()
                    }
                }
            }
        }

        macrobenchmarkRule.measureRepeated(
            packageName = packageName,
            metrics = effectiveMetrics,
            iterations = francisIterations ?: iterations,
            experimentalConfig = effectiveConfig,
            compilationMode = compilationMode,
            startupMode = startupMode,
            setupBlock = setupBlock,
            measureBlock = effectiveMeasureBlock
        )
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

        private val francisPerfettoConfigPath: String? by lazy {
            args.getString("francis.perfettoConfigPath")
        }

        @OptIn(ExperimentalPerfettoCaptureApi::class)
        private fun createPerfettoConfig(packageName: String): PerfettoConfig {
            val configText = francisPerfettoConfigPath?.let { path ->
                java.io.File(path).readText()
            } ?: PerfettoConfigTemplate.forPackage(packageName)
            return PerfettoConfig.Text(configText)
        }
    }
}
