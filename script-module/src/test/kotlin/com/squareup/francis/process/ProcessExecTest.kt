package com.squareup.francis.script.process

import org.junit.Test

class ProcessExecTest {
  @Test
  fun run_allowsAnyExitCode_whenAllowedExitCodesIsNull() {
    SubProc().run("sh", "-c", "exit 7", allowedExitCodes = null)
  }
}
