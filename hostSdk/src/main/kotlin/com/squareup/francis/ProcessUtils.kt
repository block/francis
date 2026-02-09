package com.squareup.francis

import com.squareup.francis.process.FailedExecException
import com.squareup.francis.process.OutputRedirectSpec
import com.squareup.francis.process.SubProc
import com.squareup.francis.process.subproc
import com.squareup.francis.process.TeeProcess
import com.squareup.francis.process.TeeProcessBuilder
import com.squareup.francis.process.shellEscape

val adb by lazy { Adb(subproc) }

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
      stdoutRedirect += OutputRedirectSpec.PIPE
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
      stdoutRedirect += OutputRedirectSpec.PIPE
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
