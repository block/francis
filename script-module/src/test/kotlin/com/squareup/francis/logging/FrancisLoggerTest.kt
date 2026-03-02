package com.squareup.francis.script.logging

import com.google.common.truth.Truth.assertThat
import logcat.LogPriority.DEBUG
import logcat.LogPriority.WARN
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class FrancisLoggerTest {
  @Test
  fun println_writesEscapedMessageToWrappedAndFileStreams() {
    val wrappedOutput = ByteArrayOutputStream()
    val fileOutput = ByteArrayOutputStream()
    val fileStream = PrintStream(fileOutput, true)
    val logger = FrancisLogger(
      wrapped = PrintStream(wrappedOutput, true),
      defaultFormatter = { it },
      minLogPriority = DEBUG,
      logPath = null,
      logFileStream = fileStream,
    )

    logger.println("a\u0001b\tc")

    assertThat(wrappedOutput.toString()).isEqualTo("a\\x01b\tc\n")
    assertThat(fileOutput.toString()).isEqualTo("a\\x01b\tc\n")
  }

  @Test
  fun println_logFileOnly_skipsWrappedStream() {
    val wrappedOutput = ByteArrayOutputStream()
    val fileOutput = ByteArrayOutputStream()
    val fileStream = PrintStream(fileOutput, true)
    val logger = FrancisLogger(
      wrapped = PrintStream(wrappedOutput, true),
      defaultFormatter = { it },
      minLogPriority = DEBUG,
      logPath = null,
      logFileStream = fileStream,
    )

    logger.println("only-file", logFileOnly = true)

    assertThat(wrappedOutput.toString()).isEmpty()
    assertThat(fileOutput.toString()).isEqualTo("only-file\n")
  }

  @Test
  fun log_belowMinPriority_goesToFileOnly() {
    val wrappedOutput = ByteArrayOutputStream()
    val fileOutput = ByteArrayOutputStream()
    val fileStream = PrintStream(fileOutput, true)
    val logger = FrancisLogger(
      wrapped = PrintStream(wrappedOutput, true),
      defaultFormatter = { it },
      minLogPriority = WARN,
      logPath = null,
      logFileStream = fileStream,
    )

    logger.log(DEBUG, "tag", "debug message")

    assertThat(wrappedOutput.toString()).isEmpty()
    assertThat(fileOutput.toString()).contains("debug message")
    assertThat(fileOutput.toString()).contains(" D] ")
  }
}
