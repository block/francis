package com.squareup.francis.demoinstrumentation

import com.squareup.francis.DisplayedWaiter
import com.squareup.francis.FrancisBenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class DemoBenchmark {
    @get:Rule
    val benchmarkRule = FrancisBenchmarkRule()

    @Test
    fun startup() = benchmarkRule.measureRepeated(
        packageName = "com.squareup.francis.demoapp",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = { /* Not needed for COLD */ }
    ) {
        val waiter = DisplayedWaiter(packageName)
        startActivityAndWait()
        waiter.await()
    }

    @Test
    fun expectedFailure() {
        throw AssertionError("This test is expected to fail")
    }
}
