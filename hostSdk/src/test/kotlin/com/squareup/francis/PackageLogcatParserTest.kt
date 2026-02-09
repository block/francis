package com.squareup.francis

import com.google.common.truth.Truth.assertThat
import logcat.LogPriority
import org.junit.Test

class PackageLogcatParserTest {

  data class LogEntry(val level: LogPriority, val formatted: String, val message: String)

  private fun createParserWithCapture(): Pair<PackageLogcatParser, MutableList<LogEntry>> {
    val entries = mutableListOf<LogEntry>()
    val parser = PackageLogcatParser { level, formatter, message ->
      val msg = message()
      entries.add(LogEntry(level, formatter(msg), msg))
    }
    return parser to entries
  }

  @Test
  fun processesValidLogcatLine() {
    val (parser, entries) = createParserWithCapture()
    val logcatLine = "01-13 03:00:25.346   603   762 W IPCThreadState: Sending oneway calls to frozen process.\n"

    parser.write(logcatLine.toByteArray())
    parser.close()

    assertThat(entries).hasSize(1)
    assertThat(entries[0].level).isEqualTo(LogPriority.WARN)
    assertThat(entries[0].formatted).contains("logcat")
    assertThat(entries[0].formatted).contains("IPCThreadState")
    assertThat(entries[0].formatted).contains("03:00:25.346")
    assertThat(entries[0].formatted).contains("W")
    assertThat(entries[0].message).isEqualTo("Sending oneway calls to frozen process.")
  }

  @Test
  fun processesMultipleLines() {
    val (parser, entries) = createParserWithCapture()
    val lines = """
      01-13 03:00:25.346   603   762 I FirstTag: First message
      01-13 03:00:25.347   603   762 D SecondTag: Second message
    """.trimIndent() + "\n"

    parser.write(lines.toByteArray())
    parser.close()

    assertThat(entries).hasSize(2)
    assertThat(entries[0].formatted).contains("logcat")
    assertThat(entries[0].formatted).contains("FirstTag")
    assertThat(entries[0].message).isEqualTo("First message")
    assertThat(entries[1].formatted).contains("logcat")
    assertThat(entries[1].formatted).contains("SecondTag")
    assertThat(entries[1].message).isEqualTo("Second message")
  }

  @Test
  fun handlesUnparsableLines() {
    val (parser, entries) = createParserWithCapture()
    val invalidLine = "This is not a valid logcat line\n"

    parser.write(invalidLine.toByteArray())
    parser.close()

    assertThat(entries).hasSize(1)
    assertThat(entries[0].level).isEqualTo(LogPriority.DEBUG)
    assertThat(entries[0].formatted).isEqualTo("[logcat] This is not a valid logcat line")
  }

  @Test
  fun handlesPartialWritesThenNewline() {
    val (parser, entries) = createParserWithCapture()

    parser.write("01-13 03:00:25.346   603   762 W ".toByteArray())
    parser.write("IPCThreadState: Partial message".toByteArray())
    parser.write("\n".toByteArray())
    parser.close()

    assertThat(entries).hasSize(1)
    assertThat(entries[0].formatted).contains("logcat")
    assertThat(entries[0].formatted).contains("IPCThreadState")
    assertThat(entries[0].message).isEqualTo("Partial message")
  }

  @Test
  fun flushesRemainingBufferOnClose() {
    val (parser, entries) = createParserWithCapture()
    val lineWithoutNewline = "01-13 03:00:25.346   603   762 I Tag: No newline"

    parser.write(lineWithoutNewline.toByteArray())
    parser.close()

    assertThat(entries).hasSize(1)
    assertThat(entries[0].formatted).contains("logcat")
    assertThat(entries[0].formatted).contains("Tag")
    assertThat(entries[0].message).isEqualTo("No newline")
  }

  @Test
  fun mapsLogcatLevelsCorrectly() {
    val (parser, entries) = createParserWithCapture()
    val expectedLevels = mapOf(
      'V' to LogPriority.VERBOSE,
      'D' to LogPriority.DEBUG,
      'I' to LogPriority.INFO,
      'W' to LogPriority.WARN,
      'E' to LogPriority.ERROR,
      'F' to LogPriority.ERROR,
      'S' to LogPriority.ERROR,
    )

    for ((logcatLevel, _) in expectedLevels) {
      val line = "01-01 00:00:00.000     1     1 $logcatLevel Tag$logcatLevel: msg\n"
      parser.write(line.toByteArray())
    }
    parser.close()

    assertThat(entries).hasSize(7)
    for ((index, entry) in expectedLevels.entries.withIndex()) {
      assertThat(entries[index].level).isEqualTo(entry.value)
      assertThat(entries[index].formatted).contains("logcat")
      assertThat(entries[index].formatted).contains("Tag${entry.key}")
    }
  }

  @Test
  fun ignoresEmptyLines() {
    val (parser, entries) = createParserWithCapture()

    parser.write("\n".toByteArray())
    parser.write("\n".toByteArray())
    parser.close()

    assertThat(entries).isEmpty()
  }

  @Test
  fun writesSingleBytes() {
    val (parser, entries) = createParserWithCapture()
    val line = "01-13 03:00:25.346   603   762 I Tag: Byte by byte\n"

    for (byte in line.toByteArray()) {
      parser.write(byte.toInt())
    }
    parser.close()

    assertThat(entries).hasSize(1)
    assertThat(entries[0].formatted).contains("logcat")
    assertThat(entries[0].formatted).contains("Tag")
    assertThat(entries[0].message).isEqualTo("Byte by byte")
  }

  @Test
  fun formatLogcatLine_formatsCorrectly() {
    val parsed = LogcatLine(
      date = "01-13",
      time = "03:00:25.346",
      pid = 603,
      tid = 762,
      level = 'W',
      tag = "MyTag",
      message = "Test message",
    )

    val formatted = PackageLogcatParser.formatLogcatLine(parsed)

    assertThat(formatted).isEqualTo("[03:00:25.346 W (logcat 603 762 MyTag)] Test message")
  }

  @Test
  fun formatUnparsedLogcat_formatsCorrectly() {
    val formatted = PackageLogcatParser.formatUnparsedLogcat("raw line")

    assertThat(formatted).isEqualTo("[logcat] raw line")
  }
}
