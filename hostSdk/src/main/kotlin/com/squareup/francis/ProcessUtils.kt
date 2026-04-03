package com.squareup.francis

import com.squareup.francis.script.process.FailedExecException
import com.squareup.francis.script.process.OutputRedirectSpec
import com.squareup.francis.script.process.SubProc
import com.squareup.francis.script.process.TeeProcess
import com.squareup.francis.script.process.TeeProcessBuilder
import com.squareup.francis.script.process.shellEscape
import logcat.LogPriority
import java.io.File

val adb by lazy { Adb(subproc) }



private val buildTools = File("${System.getenv("ANDROID_HOME")}/build-tools")
private val latestBuildTools = "$buildTools/${buildTools.list()!!.max()}"
private val aapt2 = "$latestBuildTools/aapt2"

private fun apkBadging(apk: String): String {
  return subproc.stdout(aapt2, "dump", "badging", apk) { logPriority = LogPriority.DEBUG }
}

private fun parseBadgingAttribute(badging: String, linePrefix: String, attribute: String): String? {
  return badging.lineSequence()
    .firstOrNull { it.startsWith(linePrefix) }
    ?.substringAfter("$attribute='", missingDelimiterValue = "")
    ?.substringBefore("'")
    ?.takeIf { it.isNotEmpty() }
}

internal fun parsePackageNameFromBadging(badging: String): String? {
  return parseBadgingAttribute(badging, "package:", "name")
}

internal fun parseTargetPackageFromBadging(badging: String): String? {
  return parseBadgingAttribute(badging, "instrumentation:", "targetPackage")
}

fun packageNameFromApk(apk: String): String {
  return parsePackageNameFromBadging(apkBadging(apk))
    ?: throw PithyException(1, "Unable to determine package name from APK: $apk")
}

fun targetPackageFromInstrumentationApk(apk: String): String {
  return parseTargetPackageFromBadging(apkBadging(apk))
    ?: throw PithyException(
      1,
      "Instrumentation APK does not declare android:targetPackage: $apk. Use --app to specify the app under test."
    )
}

class Adb(
  private val subproc: SubProc,
  val serial: String = detectSerial(subproc),
) {

  fun shellStart(
    vararg args: String,
    forceRoot: Boolean = false,
    evalArgs: Boolean = false,
    configure: TeeProcessBuilder.() -> Unit = {}
  ): TeeProcess {
    val (procArgs, commandRepr) = buildShellCommand(args.toList(), forceRoot, evalArgs)
    return subproc.start(*procArgs.toTypedArray(), commandRepr = commandRepr, configure = configure)
  }

  fun shellRun(
    vararg args: String,
    forceRoot: Boolean = false,
    evalArgs: Boolean = false,
    expectedExitCodes: List<Int> = listOf(0),
    configure: TeeProcessBuilder.() -> Unit = {}
  ) {
    shellStart(*args, forceRoot = forceRoot, evalArgs = evalArgs, configure = configure)
      .checkExitCode(expectedExitCodes)
  }

  fun shellStdout(
    vararg args: String,
    forceRoot: Boolean = false,
    evalArgs: Boolean = false,
    chomp: Boolean = true,
    allowedExitCodes: List<Int> = listOf(0),
    configure: TeeProcessBuilder.() -> Unit = {}
  ): String {
    return shellStart(*args, forceRoot = forceRoot, evalArgs = evalArgs) {
      configure()
      stdoutRedirect += OutputRedirectSpec.CAPTURE
    }.stdoutText(chomp, allowedExitCodes)
  }

  fun cmdStart(
    vararg args: String,
    configure: TeeProcessBuilder.() -> Unit = {}
  ): TeeProcess {
    val procArgs = listOf("adb", "-s", serial) + args
    return subproc.start(*procArgs.toTypedArray(), configure = configure)
  }

  fun cmdRun(
    vararg args: String,
    allowedExitCodes: List<Int> = listOf(0),
    configure: TeeProcessBuilder.() -> Unit = {}
  ) {
    cmdStart(*args, configure = configure).checkExitCode(allowedExitCodes)
  }

  fun cmdStdout(
    vararg args: String,
    chomp: Boolean = true,
    allowedExitCodes: List<Int> = listOf(0),
    configure: TeeProcessBuilder.() -> Unit = {}
  ): String {
    return cmdStart(*args) {
      configure()
      stdoutRedirect += OutputRedirectSpec.CAPTURE
    }.stdoutText(chomp, allowedExitCodes)
  }

  private fun buildShellCommand(
    args: List<String>,
    forceRoot: Boolean,
    evalArgs: Boolean
  ): Pair<List<String>, String> {
    val maybeRoot = if (forceRoot) listOf("su", "0") else listOf()
    val maybeEval = if (evalArgs) listOf("eval \"$*\"") else listOf("\"$@\"")

    val procArgs = listOf(
      "sh",
      "-c",
      """adb -s $serial shell $(printf " %q" "$@")""",
      "--"
    ) + maybeRoot + listOf("sh", "-c") + maybeEval + listOf("--") + args

    val logArgs: List<String> = if (evalArgs) {
      maybeRoot + listOf("sh", "-c") + maybeEval + listOf("--") + args
    } else if (forceRoot) {
      maybeRoot + args
    } else {
      args
    }

    val prefix = if (forceRoot) "$serial:/ #" else "$serial:/ $"
    return procArgs to "$prefix ${shellEscape(logArgs)}"
  }

}

private fun detectSerial(subproc: SubProc): String {
  val serialFromEnv = System.getenv("ANDROID_SERIAL")
  if (serialFromEnv != null) return serialFromEnv

  return try {
    subproc.stdout("adb", "get-serialno")
  } catch (e: FailedExecException) {
    val stderr = e.stderrText
    if (stderr != null) {
      throw PithyException(e.exitCode, stderr)
    } else {
      throw e
    }
  }
}
