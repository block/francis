package com.squareup.francis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InstrumentationResultParserTest {

  @Test
  fun success_singleTest() {
    val stdout = """
      com.squareup.francis.demoinstrumentation.DemoBenchmark:
      INSTRUMENTATION_STATUS: android.studio.v2display.benchmark.outputDirPath=/sdcard/Android/media/com.squareup.francis.demoinstrumentation/additional_test_output
      INSTRUMENTATION_STATUS_CODE: 2
      .

      Time: 8.397

      OK (1 test)
    """.trimIndent()

    val result = InstrumentationResultParser.parse(stdout, stderr = "", exitCode = 0)

    assertThat(result).isInstanceOf(InstrumentationResult.Success::class.java)
  }

  @Test
  fun success_multipleTests() {
    val stdout = """
      ....

      Time: 12.5

      OK (4 tests)
    """.trimIndent()

    val result = InstrumentationResultParser.parse(stdout, stderr = "", exitCode = 0)

    assertThat(result).isInstanceOf(InstrumentationResult.Success::class.java)
  }

  @Test
  fun failure_testFailure() {
    val stdout = """
      com.squareup.francis.demoinstrumentation.DemoBenchmark:
      Error in startup(com.squareup.francis.demoinstrumentation.DemoBenchmark):
      java.lang.AssertionError: ERRORS (not suppressed): EMULATOR

      Time: 0.157
      There was 1 failure:
      1) startup(com.squareup.francis.demoinstrumentation.DemoBenchmark)
      java.lang.AssertionError: ERRORS (not suppressed): EMULATOR

      FAILURES!!!
      Tests run: 1,  Failures: 1
    """.trimIndent()

    val result = InstrumentationResultParser.parse(stdout, stderr = "", exitCode = 0)

    assertThat(result).isInstanceOf(InstrumentationResult.Failure::class.java)
    val failure = result as InstrumentationResult.Failure
    assertThat(failure.message).contains("FAILURES")
  }

  @Test
  fun failure_classNotFound() {
    val stdout = """
      com.nonexistent.Class:
      Error in initializationError(com.nonexistent.Class):
      java.lang.RuntimeException: Failed loading specified test class 'com.nonexistent.Class'
      Caused by: java.lang.ClassNotFoundException: com.nonexistent.Class

      Time: 0.019
      There was 1 failure:
      1) initializationError(com.nonexistent.Class)
      java.lang.RuntimeException: Failed loading specified test class 'com.nonexistent.Class'

      FAILURES!!!
      Tests run: 1,  Failures: 1
    """.trimIndent()

    val result = InstrumentationResultParser.parse(stdout, stderr = "", exitCode = 0)

    assertThat(result).isInstanceOf(InstrumentationResult.Failure::class.java)
  }

  @Test
  fun failure_instrumentationFailed() {
    val stdout = """
      android.util.AndroidException: INSTRUMENTATION_FAILED: com.nonexistent.package/androidx.test.runner.AndroidJUnitRunner
              at com.android.commands.am.Instrument.run(Instrument.java:543)
      INSTRUMENTATION_STATUS: Error=Unable to find instrumentation info for: ComponentInfo{com.nonexistent.package/androidx.test.runner.AndroidJUnitRunner}
      INSTRUMENTATION_STATUS: id=ActivityManagerService
      INSTRUMENTATION_STATUS_CODE: -1
    """.trimIndent()

    val result = InstrumentationResultParser.parse(stdout, stderr = "", exitCode = 1)

    assertThat(result).isInstanceOf(InstrumentationResult.Failure::class.java)
    val failure = result as InstrumentationResult.Failure
    assertThat(failure.message).contains("INSTRUMENTATION_FAILED")
  }

  @Test
  fun failure_earlyError_badArgument() {
    val stdout = """
      Activity manager (activity) commands:
        help
            Print this help text.
        start-activity [-D] [-N] [-W] [-P <FILE>] [--start-profiler <FILE>]
      ...
    """.trimIndent()

    val stderr = "Error: Invalid userId -2"

    val result = InstrumentationResultParser.parse(stdout, stderr, exitCode = 0)

    assertThat(result).isInstanceOf(InstrumentationResult.Failure::class.java)
    val failure = result as InstrumentationResult.Failure
    assertThat(failure.message).contains("Invalid userId")
  }

  @Test
  fun failure_noInstrumentationOutput() {
    val stdout = ""
    val stderr = ""

    val result = InstrumentationResultParser.parse(stdout, stderr, exitCode = 0)

    assertThat(result).isInstanceOf(InstrumentationResult.Failure::class.java)
  }

  @Test
  fun failure_instrumentationStatusCodeNegative() {
    val stdout = """
      INSTRUMENTATION_STATUS: stream=
      INSTRUMENTATION_STATUS: test=myTest
      INSTRUMENTATION_STATUS_CODE: -1
    """.trimIndent()

    val result = InstrumentationResultParser.parse(stdout, stderr = "", exitCode = 0)

    assertThat(result).isInstanceOf(InstrumentationResult.Failure::class.java)
  }
}
