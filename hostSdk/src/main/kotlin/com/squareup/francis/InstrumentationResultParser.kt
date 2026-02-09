package com.squareup.francis

sealed class InstrumentationResult {
  data class Success(val testsRun: Int) : InstrumentationResult()
  data class Failure(val message: String) : InstrumentationResult()
}

object InstrumentationResultParser {
  private val okPattern = Regex("""OK \((\d+) tests?\)""")
  private val failuresPattern = Regex("""FAILURES!!!""")
  private val instrumentationFailedPattern = Regex("""INSTRUMENTATION_FAILED""")
  private val instrumentationStatusCodePattern = Regex("""INSTRUMENTATION_STATUS_CODE:\s*(-?\d+)""")
  private val stderrErrorPattern = Regex("""^Error:""", RegexOption.MULTILINE)

  fun parse(stdout: String, stderr: String, exitCode: Int): InstrumentationResult {
    // Check for early errors in stderr (e.g., bad arguments causing help text)
    if (stderr.isNotBlank() && stderrErrorPattern.containsMatchIn(stderr)) {
      return InstrumentationResult.Failure(stderr.trim())
    }

    // Check for INSTRUMENTATION_FAILED (package not found, etc.)
    if (instrumentationFailedPattern.containsMatchIn(stdout)) {
      val errorLine = stdout.lines().firstOrNull { it.contains("INSTRUMENTATION_FAILED") }
      return InstrumentationResult.Failure(errorLine ?: "INSTRUMENTATION_FAILED")
    }

    // Check for test failures (FAILURES!!! marker)
    if (failuresPattern.containsMatchIn(stdout)) {
      val summary = stdout.lines()
        .filter { it.contains("FAILURES") || it.startsWith("Tests run:") }
        .joinToString("\n")
      return InstrumentationResult.Failure(summary.ifBlank { "Test failures detected" })
    }

    // Check for negative INSTRUMENTATION_STATUS_CODE (indicates error)
    val statusCodes = instrumentationStatusCodePattern.findAll(stdout)
      .map { it.groupValues[1].toInt() }
      .toList()
    if (statusCodes.any { it == -1 }) {
      return InstrumentationResult.Failure("INSTRUMENTATION_STATUS_CODE: -1 (error)")
    }

    // Check for success marker
    val okMatch = okPattern.find(stdout)
    if (okMatch != null) {
      val testsRun = okMatch.groupValues[1].toInt()
      return InstrumentationResult.Success(testsRun)
    }

    // No recognizable output - likely didn't run at all
    return InstrumentationResult.Failure("No instrumentation output detected")
  }
}
