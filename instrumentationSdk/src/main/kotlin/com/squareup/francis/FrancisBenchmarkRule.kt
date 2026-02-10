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

    override fun apply(base: Statement, description: Description): Statement {
        return macrobenchmarkRule.apply(base, description)
    }

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
        if (francisProfiler == "perfetto") {
            @OptIn(ExperimentalMetricApi::class)
            macrobenchmarkRule.measureRepeated(
                packageName = packageName,
                metrics = listOf(NoOpMetric()),
                iterations = francisIterations ?: iterations,
                experimentalConfig = ExperimentalConfig(
                    perfettoConfig = createPerfettoConfig(packageName)
                ),
                compilationMode = compilationMode,
                startupMode = startupMode,
                setupBlock = setupBlock,
                measureBlock = measureBlock
            )
        } else {
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
        if (francisProfiler == "perfetto") {
            @OptIn(ExperimentalMetricApi::class)
            macrobenchmarkRule.measureRepeated(
                packageName = packageName,
                metrics = listOf(NoOpMetric()),
                iterations = francisIterations ?: iterations,
                experimentalConfig = ExperimentalConfig(
                    perfettoConfig = createPerfettoConfig(packageName)
                ),
                compilationMode = compilationMode,
                startupMode = startupMode,
                setupBlock = setupBlock,
                measureBlock = measureBlock
            )
        } else {
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

        @OptIn(ExperimentalPerfettoCaptureApi::class)
        private fun createPerfettoConfig(packageName: String): PerfettoConfig {
            return PerfettoConfig.Text(DEFAULT_PERFETTO_CONFIG.replace("{PACKAGE}", packageName))
        }

        private val DEFAULT_PERFETTO_CONFIG = """
            buffers {
                size_kb: 65536
                fill_policy: RING_BUFFER
            }
            buffers {
                size_kb: 4096
                fill_policy: RING_BUFFER
            }
            data_sources {
                config {
                    name: "linux.ftrace"
                    target_buffer: 0
                    ftrace_config {
                        ftrace_events: "sched/sched_switch"
                        ftrace_events: "sched/sched_wakeup"
                        ftrace_events: "sched/sched_waking"
                        ftrace_events: "sched/sched_blocked_reason"
                        ftrace_events: "power/cpu_frequency"
                        ftrace_events: "power/cpu_idle"
                        ftrace_events: "power/suspend_resume"
                        atrace_categories: "am"
                        atrace_categories: "dalvik"
                        atrace_categories: "gfx"
                        atrace_categories: "input"
                        atrace_categories: "pm"
                        atrace_categories: "res"
                        atrace_categories: "sched"
                        atrace_categories: "view"
                        atrace_categories: "wm"
                        atrace_apps: "{PACKAGE}"
                    }
                }
            }
            data_sources {
                config {
                    name: "linux.process_stats"
                    target_buffer: 1
                    process_stats_config {
                        scan_all_processes_on_start: true
                        proc_stats_poll_ms: 1000
                    }
                }
            }
            data_sources {
                config {
                    name: "android.packages_list"
                    target_buffer: 1
                }
            }
            write_into_file: true
            file_write_period_ms: 2500
            flush_period_ms: 5000
        """.trimIndent()
    }
}
