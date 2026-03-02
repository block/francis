package com.squareup.francis.process

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset

class FailedExecException(
  val exitCode: Int,
  val commandLine: List<String>,
  private val stdoutCapture: BlockingCapturedOutput?,
  private val stderrCapture: BlockingCapturedOutput?,
) : RuntimeException() {
  val stdout: ProcessOutputCopy? by lazy { stdoutCapture?.awaitClosedOutputCopy() }
  val stderr: ProcessOutputCopy? by lazy { stderrCapture?.awaitClosedOutputCopy() }
  val stdoutText: String? get() = stdout?.asText()
  val stderrText: String? get() = stderr?.asText()

  override val message: String by lazy {
    buildMessage(exitCode, commandLine, stdoutText, stderrText)
  }

  companion object {
    private fun buildMessage(
      exitCode: Int,
      commandLine: List<String>,
      stdoutText: String?,
      stderrText: String?
    ): String {
      val stderrOutput = stderrText?.takeIf { it.isNotBlank() }
      val stdoutOutput = stdoutText?.takeIf { it.isNotBlank() }
      return listOfNotNull(
        "(exit code $exitCode) ${shellEscape(commandLine)}",
        stderrOutput?.let { "stderr:\n$it" },
        stdoutOutput?.let { "stdout:\n$it" },
      ).joinToString("\n")
    }
  }
}

class ProcessOutputCopy(private val bytes: ByteArray) {

  fun openStream(): InputStream = ByteArrayInputStream(bytes)

  fun asText(charset: Charset = Charsets.UTF_8): String = bytes.toString(charset)
}
