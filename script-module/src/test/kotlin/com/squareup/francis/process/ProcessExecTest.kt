package com.squareup.francis.script.process

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProcessExecTest {
  @Test
  fun run_allowsAnyExitCode_whenAllowedExitCodesIsNull() {
    SubProc().run("sh", "-c", "exit 7", allowedExitCodes = null)
  }

  @Test
  fun outputs_capturesStdoutAndStderr() {
    val result = SubProc().outputs("sh", "-c", "echo out; echo err >&2")

    assertThat(result.exitCode).isEqualTo(0)
    assertThat(result.stdout).isEqualTo("out\n")
    assertThat(result.stderr).isEqualTo("err\n")
  }

  @Test
  fun outputs_allowsAnyExitCode_whenAllowedExitCodesIsNull() {
    val result = SubProc().outputs("sh", "-c", "echo out; echo err >&2; exit 9", allowedExitCodes = null)

    assertThat(result.exitCode).isEqualTo(9)
    assertThat(result.stdout).isEqualTo("out\n")
    assertThat(result.stderr).isEqualTo("err\n")
  }
}
