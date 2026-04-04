package com.squareup.francis

import logcat.LogPriority
import com.squareup.francis.script.process.OutputTarget
import com.squareup.francis.script.process.loggedStdoutRedirectSpec
import com.squareup.francis.script.process.loggedStderrRedirectSpec
import com.squareup.francis.script.process.OutputRedirectSpec
import java.io.File

// This lets instrumentation-side @Disable checks know what target Francis explicitly requested.
private const val OVERRIDE_DISABLE_ARG = "francis.overrideDisable"
private const val OVERRIDE_APP_PACKAGE_ARG = "francis.overrideAppPackage"

internal fun buildInstrumentationArgsList(
  runnerVals: RunnerValues,
  deviceOutputDir: String,
  simpleperfOutputDir: String?,
  devicePerfettoConfigPath: String?,
): List<String> {
  val instrumentationArgs: Map<String, String?> = runnerVals.instrumentationArgs + mapOf(
    "class" to runnerVals.testSymbol,
    // Auto-wire override from --symbol so users don't need to pass this manually.
    OVERRIDE_DISABLE_ARG to runnerVals.testSymbol,
    // Preserve the host-resolved app package even when it differs from targetContext.packageName.
    OVERRIDE_APP_PACKAGE_ARG to runnerVals.appPackageOrNull,
    "additionalTestOutputDir" to deviceOutputDir,
    "simpleperfOutputDir" to simpleperfOutputDir,
    "simpleperfCallGraph" to runnerVals.simpleperfCallGraph,
    "androidx.benchmark.suppressErrors" to (if (runnerVals.suppressErrors) "LOW-BATTERY,DEBUGGABLE,EMULATOR" else ""),
    "androidx.benchmark.compilation.enabled" to runnerVals.aot.toString(),
    "androidx.benchmark.dryRunMode.enable" to runnerVals.dryRun.toString(),
    "francis.overrideIterations" to runnerVals.overrideIterations?.toString(),
    "francis.profiler" to runnerVals.profiler,
    "francis.perfettoConfigPath" to devicePerfettoConfigPath,
  )

  return instrumentationArgs
    .flatMap { (k, v) -> if (v != null) listOf("-e", k, v) else emptyList() }
}

class Benchmark(
  val baseVals: BaseValues,
  val runnerVals: RunnerValues,
) {
  val instrumentationApk = runnerVals.instrumentationApk
  val appApkOrNull = runnerVals.appApkOrNull

  val instrumentationPackage: String by lazy { packageNameFromApk(instrumentationApk) }
  val deviceOutputDir: String by lazy { "/sdcard/Android/media/$instrumentationPackage/additional_test_output" }

  // Only used with simpleperf
  val simpleperfOutputDir: String? by lazy {
    if (runnerVals.profiler == "simpleperf") {
      "${FrancisConstants.DEVICE_FRANCIS_DIR}/$instrumentationPackage"
    } else {
      null
    }
  }

  // Check if su is available on the device (for simpleperf cleanup)
  private val isRootAvailable: Boolean by lazy {
    val result = adb.shellStdout("su", "0", "id", allowedExitCodes = listOf(0, 1, 255)) {
      logPriority = LogPriority.DEBUG
    }
    result.contains("uid=0")
  }

  // Path on device where custom perfetto config is pushed
  val devicePerfettoConfigPath: String? by lazy {
    runnerVals.perfettoConfigPath?.let {
      "${FrancisConstants.DEVICE_FRANCIS_DIR}/perfetto-config.textproto"
    }
  }

  val instrumentationArgsList: List<String> by lazy {
    buildInstrumentationArgsList(
      runnerVals = runnerVals,
      deviceOutputDir = deviceOutputDir,
      simpleperfOutputDir = simpleperfOutputDir,
      devicePerfettoConfigPath = devicePerfettoConfigPath,
    )
  }

  fun run() {
    adb.shellRun("rm", "-rf", deviceOutputDir) { logPriority = LogPriority.DEBUG }
    simpleperfOutputDir?.let {
      // Files created by simpleperf with su are owned by root, so we need su to delete them
      adb.shellRun("rm", "-rf", it, forceRoot = isRootAvailable) { logPriority = LogPriority.DEBUG }
      adb.shellRun("mkdir", "-p", it, forceRoot = isRootAvailable) { logPriority = LogPriority.DEBUG }
    }
    pushPerfettoConfigIfNeeded()
    appApkOrNull?.let(::ensureInstalled)
    ensureInstalled(instrumentationApk)

    val cmdArgs = arrayOf(
      "am",
      "instrument",
      "-w",
    ) + instrumentationArgsList + arrayOf(
      "$instrumentationPackage/${runnerVals.runnerClass}"
    )

    // Show logcat for the instrumentation process (UiDevice is absurdly verbose, so we silence
    // that)
    val packageLogcatHelper = PackageLogcatHelper.create(
      instrumentationPackage,
      filterSpecs = listOf("UiDevice:e", "*:v"),
    )

    val instrumentProc = adb.shellStart(*cmdArgs) {
      stdoutRedirect = OutputRedirectSpec(listOf(OutputTarget.Capture)) + loggedStdoutRedirectSpec(LogPriority.INFO)
      stderrRedirect = OutputRedirectSpec(listOf(OutputTarget.Capture)) + loggedStderrRedirectSpec(LogPriority.INFO)
    }

    val exitCode: Int
    packageLogcatHelper.use {
      packageLogcatHelper.awaitPidAndStartLogcat()
      exitCode = instrumentProc.waitFor()
    }

    val stdout = instrumentProc.stdoutReader.readText()
    val stderr = instrumentProc.stderrReader.readText()

    val result = InstrumentationResultParser.parse(stdout, stderr, exitCode)
    if (result is InstrumentationResult.Failure) {
      throw PithyException(1, "Instrumentation failed: ${result.message}")
    }

    pullDirFlattened(deviceOutputDir, runnerVals.hostOutputDir)
    simpleperfOutputDir?.let { pullDirFlattened(it, runnerVals.hostOutputDir) }
  }

  /**
   * Pull all files from a device directory to a host directory.
   * We pull the entire directory at once to avoid race conditions with temp files that may
   * be deleted between listing and pulling.
   */
  private fun pullDirFlattened(deviceDir: String, hostDir: String) {
    adb.cmdRun("pull", "$deviceDir/.", hostDir) { logPriority = LogPriority.DEBUG }
  }

  private fun pushPerfettoConfigIfNeeded() {
    val hostPath = runnerVals.perfettoConfigPath ?: return
    val devicePath = devicePerfettoConfigPath ?: return
    adb.shellRun("mkdir", "-p", File(devicePath).parent) { logPriority = LogPriority.DEBUG }
    adb.cmdRun("push", hostPath, devicePath) { logPriority = LogPriority.DEBUG }
  }

  private fun ensureInstalled(apk: String) {
    val sha256 = subproc.stdout("sha256sum", apk) { logPriority = LogPriority.DEBUG }
      .split("\\s+".toRegex())
      .first()

    val packageName = packageNameFromApk(apk)
    if (ApkCache.getDeviceSha256(packageName) != sha256) {
      adb.cmdRun("install", apk)
    }
  }

}
