package com.squareup.francis.process

class FailedExecException(
  val exitCode: Int,
  val commandLine: List<String>,
  val stdoutText: String?,
  val stderrText: String?,
) : RuntimeException(buildMessage(exitCode, commandLine)) {
  companion object {
    private fun buildMessage(exitCode: Int, commandLine: List<String>): String {
      val cmdStr = shellEscape(commandLine)
      return "(exit code $exitCode) $cmdStr"
    }
  }
}
