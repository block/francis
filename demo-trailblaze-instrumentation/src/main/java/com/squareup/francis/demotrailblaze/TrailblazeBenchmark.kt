package com.squareup.francis.demotrailblaze

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.benchmark.Shell
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.squareup.francis.FrancisBenchmarkRule
import com.squareup.francis.FrancisConfig
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.Description
import org.junit.runner.RunWith
import xyz.block.trailblaze.android.AndroidTrailblazeRule
import xyz.block.trailblaze.android.accessibility.OnDeviceAccessibilityServiceSetup
import xyz.block.trailblaze.http.NoOpLlmClient
import xyz.block.trailblaze.llm.TrailblazeLlmModel
import xyz.block.trailblaze.llm.TrailblazeLlmProvider
import xyz.block.trailblaze.model.TrailblazeConfig

@RunWith(AndroidJUnit4::class)
@LargeTest
class TrailblazeBenchmark {
  private val benchmarkRule = FrancisBenchmarkRule()
  private val trailblazeRule =
    object :
      AndroidTrailblazeRule(
        trailblazeLlmModel =
          TrailblazeLlmModel.fallback(TrailblazeLlmProvider.NONE, "recordings-only"),
        llmClient = NoOpLlmClient(),
        config = TrailblazeConfig(selfHeal = false),
      ) {
      @SuppressLint("RestrictedApi")
      override fun onBeforeTest(description: Description) {
        // Benchmark's shell caches a UiAutomation handle with the default (suppressing) flags.
        // Initialize it before Trailblaze binds its accessibility service.
        Shell.connectUiAutomation()
      }
    }

  private val storagePermissions =
    if (Build.VERSION.SDK_INT < 29) {
      // Trailblaze writes screenshots and logs to shared storage on older devices.
      GrantPermissionRule.grant(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
      )
    } else {
      null
    }

  @get:Rule
  val rules: RuleChain =
    RuleChain.outerRule(benchmarkRule)
      .let { if (storagePermissions != null) it.around(storagePermissions) else it }
      .around(trailblazeRule)

  @Test
  @OptIn(ExperimentalMetricApi::class)
  fun showDetails() =
    benchmarkRule.measureRepeated(
      packageName = FrancisConfig.appPackage,
      metrics = listOf(TraceSectionMetric("Demo.showDetails", TraceSectionMetric.Mode.First)),
      compilationMode = CompilationMode.None(),
      iterations = 5,
      setupBlock = {
        // Each iteration starts from the home screen, outside measurement.
        killProcess()
        startActivityAndWait()
        // Shell/UiDevice operations can reconnect UiAutomation and unbind the service.
        OnDeviceAccessibilityServiceSetup.ensureAccessibilityServiceReady()
        trail("setup")
      },
    ) {
      trail("show-details")
    }

  private fun trail(name: String) {
    trailblazeRule.runFromAsset(
      yamlAssetPath = "trails/$name.trail.yaml",
      forceStopApp = false,
      useRecordedSteps = true,
    )
  }
}
