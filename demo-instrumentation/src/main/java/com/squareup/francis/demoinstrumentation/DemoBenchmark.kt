package com.squareup.francis.demoinstrumentation

import com.squareup.francis.DisplayedWaiter
import com.squareup.francis.Disable
import com.squareup.francis.FrancisBenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class DemoBenchmark {
    companion object {
        private const val DEMO_APP_PACKAGE = "com.squareup.francis.demoapp"
        private const val OVERRIDE_DISABLE_ARG = "francis.overrideDisable"
    }

    @get:Rule
    val benchmarkRule = FrancisBenchmarkRule()

    @Test
    fun startup() = runStartupBenchmark(iterations = 5)

    @Test
    fun expectedFailure() {
        throw AssertionError("This test is expected to fail")
    }

    @Disable("Demonstrates francis.overrideDisable behavior")
    @Test
    fun disabledUnlessMethodTargeted() {
        val expectedTarget = "${this::class.java.name}#disabledUnlessMethodTargeted"
        val actualOverride = InstrumentationRegistry.getArguments().getString(OVERRIDE_DISABLE_ARG)
        assertEquals(expectedTarget, actualOverride)

        // Also run a real benchmark so `francis bench` can pull output artifacts successfully.
        runStartupBenchmark(iterations = 1)
    }

    private fun runStartupBenchmark(iterations: Int) = benchmarkRule.measureRepeated(
        packageName = DEMO_APP_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = iterations,
        startupMode = StartupMode.COLD,
        setupBlock = { /* Not needed for COLD */ }
    ) {
        val waiter = DisplayedWaiter(packageName)
        startActivityAndWait()
        waiter.await()
    }
}
