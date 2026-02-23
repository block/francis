package com.squareup.francis.demo

import com.squareup.francis.BaseConfig
import com.squareup.francis.RunnerOptions
import com.squareup.francis.releaseDir
import com.squareup.francis.runFrancis

class DemoRunnerOptions(
  private val releaseDir: String,
  config: BaseConfig,
) : RunnerOptions(config = config) {
  override val appApkOrNull: String? by lazy { super.appApkOrNull ?: "$releaseDir/demo-app.apk" }
  override val instrumentationApkOrNull: String? by lazy { super.instrumentationApkOrNull ?: "$releaseDir/demo-instrumentation.apk" }
  override val testSymbolOrNull: String? by lazy { super.testSymbolOrNull ?: "com.squareup.francis.demoinstrumentation.DemoBenchmark#startup" }
}

fun main(rawArgs: Array<String>) = runFrancis(
  rawArgs = rawArgs,
  name = "francis-demo",
  runnerOptionsFactory = { config -> DemoRunnerOptions(releaseDir, config) },
)
