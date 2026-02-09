package com.squareup.francis

import logcat.LogPriority
import com.squareup.francis.process.OutputTarget
import com.squareup.francis.process.loggedStdoutRedirectSpec
import com.squareup.francis.process.loggedStderrRedirectSpec
import com.squareup.francis.process.OutputRedirectSpec
import com.squareup.francis.process.subproc
import java.io.File

class Benchmark(
  val base: BaseValues,
  val instrumentation: RunnerValues,
) {
  val instrumentationApk = instrumentation.instrumentationApk
  val appApk = instrumentation.appApk

  val instrumentationPackage: String by lazy { packageNameFromApk(instrumentationApk) }
  val appPackage: String by lazy { packageNameFromApk(appApk) }
  val deviceOutputDir: String by lazy { "/sdcard/Android/media/$instrumentationPackage/additional_test_output" }

  // Only used with simpleperf
  val simpleperfOutputDir: String? by lazy {
    if (instrumentation.profiler == "simpleperf") {
      "/data/local/tmp/francis/$instrumentationPackage"
    } else {
      null
    }
  }

  val instrumentationArgsList: List<String> by lazy {
    val instrumentationArgs: Map<String, String?> = instrumentation.instrumentationArgs + mapOf(
      "class" to instrumentation.testSymbol,
      "additionalTestOutputDir" to deviceOutputDir,
      "simpleperfOutputDir" to simpleperfOutputDir,
      "simpleperfCallGraph" to instrumentation.simpleperfCallGraph,
      "androidx.benchmark.suppressErrors" to (if (instrumentation.suppressErrors) "LOW-BATTERY,DEBUGGABLE,EMULATOR" else ""),
      "androidx.benchmark.compilation.enabled" to instrumentation.aot.toString(),
      "francis.iterations" to instrumentation.iterations?.toString(),
      "francis.profiler" to instrumentation.profiler,
    )

    instrumentationArgs
      .flatMap { (k, v) -> if (v != null) listOf("-e", k, v) else emptyList() }
  }

  fun run() {
    adb.shellRun("rm", "-rf", deviceOutputDir) { logPriority = LogPriority.DEBUG }
    simpleperfOutputDir?.let {
      adb.shellRun("rm", "-rf", it) { logPriority = LogPriority.DEBUG }
      adb.shellRun("mkdir", "-p", it) { logPriority = LogPriority.DEBUG }
    }
    ensureInstalled(appApk)
    ensureInstalled(instrumentationApk)

    val cmdArgs = arrayOf(
      "am",
      "instrument",
      "-w",
    ) + instrumentationArgsList + arrayOf(
      "$instrumentationPackage/${instrumentation.runnerClass}"
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

    adb.cmdRun("pull", deviceOutputDir, instrumentation.hostOutputDir)
    simpleperfOutputDir?.let { dir ->
      val files = adb.shellStdout("ls", dir) { logPriority = LogPriority.DEBUG }
        .lines()
        .filter { it.isNotBlank() }
      for (file in files) {
        adb.cmdRun("pull", "$dir/$file", instrumentation.hostOutputDir)
      }
    }
  }

  private fun packageNameFromApk(apk: String): String {
    return subproc.stdout(aapt2, "dump", "badging", apk) { logPriority = LogPriority.DEBUG }
      .lines()
      .first { it.startsWith("package:") }
      .substringAfter("name='")
      .substringBefore("'")
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

  companion object {
    private val buildTools = File("${System.getenv("ANDROID_HOME")}/build-tools")
    private val latestBuildTools = "$buildTools/${buildTools.list()!!.max()}"
    private val aapt2 = "$latestBuildTools/aapt2"
  }
}