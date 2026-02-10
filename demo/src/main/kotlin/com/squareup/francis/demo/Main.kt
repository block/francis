package com.squareup.francis.demo

import com.squareup.francis.BaseConfig
import com.squareup.francis.RunnerOptions
import com.squareup.francis.releaseDir
import com.squareup.francis.runFrancis

class DemoRunnerOptions(
  private val releaseDir: String,
  config: BaseConfig,
) : RunnerOptions(config = config) {
  override val appApk: String by lazy { appApkOption ?: "$releaseDir/demo-app.apk" }
  override val instrumentationApk: String by lazy { instrumentationApkOption ?: "$releaseDir/demo-instrumentation.apk" }
  override val testSymbol: String by lazy { testSymbolOption ?: "com.squareup.francis.demoinstrumentation.DemoBenchmark#startup" }
}

fun main(rawArgs: Array<String>) = runFrancis(
  rawArgs = rawArgs,
  name = "francis-demo",
  runnerOptionsFactory = { config -> DemoRunnerOptions(releaseDir, config) },
)
