package com.squareup.francis

import logcat.LogPriority
import com.squareup.francis.process.OutputTarget
import com.squareup.francis.process.loggedStdoutRedirectSpec
import com.squareup.francis.process.loggedStderrRedirectSpec
import com.squareup.francis.process.OutputRedirectSpec
import com.squareup.francis.process.subproc
import java.io.File


class Benchmark(
  val baseVals: BaseValues,
  val runnerVals: RunnerValues,
) {
  val instrumentationApk = runnerVals.instrumentationApk
  val appApk = runnerVals.appApk

  val instrumentationPackage: String by lazy { packageNameFromApk(instrumentationApk) }
  val appPackage: String by lazy { packageNameFromApk(appApk) }
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
    val instrumentationArgs: Map<String, String?> = runnerVals.instrumentationArgs + mapOf(
      "class" to runnerVals.testSymbol,
      "additionalTestOutputDir" to deviceOutputDir,
      "simpleperfOutputDir" to simpleperfOutputDir,
      "simpleperfCallGraph" to runnerVals.simpleperfCallGraph,
      "androidx.benchmark.suppressErrors" to (if (runnerVals.suppressErrors) "LOW-BATTERY,DEBUGGABLE,EMULATOR" else ""),
      "androidx.benchmark.compilation.enabled" to runnerVals.aot.toString(),
      "androidx.benchmark.dryRunMode.enable" to runnerVals.dryRun.toString(),
      "francis.iterations" to runnerVals.iterations?.toString(),
      "francis.profiler" to runnerVals.profiler,
      "francis.perfettoConfigPath" to devicePerfettoConfigPath,
    )

    instrumentationArgs
      .flatMap { (k, v) -> if (v != null) listOf("-e", k, v) else emptyList() }
  }

  fun run() {
    adb.shellRun("rm", "-rf", deviceOutputDir) { logPriority = LogPriority.DEBUG }
    simpleperfOutputDir?.let {
      // Files created by simpleperf with su are owned by root, so we need su to delete them
      adb.shellRun("rm", "-rf", it, forceRoot = isRootAvailable) { logPriority = LogPriority.DEBUG }
      adb.shellRun("mkdir", "-p", it, forceRoot = isRootAvailable) { logPriority = LogPriority.DEBUG }
    }
    pushPerfettoConfigIfNeeded()
    ensureInstalled(appApk)
    ensureInstalled(instrumentationApk)

    val cmdArgs = arrayOf(
      "am",
      "instrument",
      "-w",
    ) + instrumentationArgsList + arrayOf(
      "$instrumentationPackage/${runnerVals.runnerClass}"
    )

    // Show logcat for the instrumentation process (UiDevice:d is absurdly verbose, so we silence
    // that)
    val packageLogcatHelper = PackageLogcatHelper.create(
      instrumentationPackage,
      filterSpecs = listOf("UiDevice:i", "*:v"),
    )

    val instrumentProc = adb.shellStart(*cmdArgs) {
      stdoutRedirect = OutputRedirectSpec(listOf(OutputTarget.Pipe)) + loggedStdoutRedirectSpec(LogPriority.INFO)
      stderrRedirect = OutputRedirectSpec(listOf(OutputTarget.Pipe)) + loggedStderrRedirectSpec(LogPriority.INFO)
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