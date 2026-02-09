package com.squareup.francis

import com.squareup.francis.log.logFormatted
import logcat.LogPriority
import java.io.OutputStream

class PackageLogcatParser(
  // injectable log function for easier testing
  private val logFn: (LogPriority, (String) -> String, () -> String) -> Unit = ::logFormatted
) : OutputStream() {
  private val lineBuffer = StringBuilder()

  override fun write(b: Int) {
    val char = b.toChar()
    if (char == '\n') {
      processLine(lineBuffer.toString())
      lineBuffer.clear()
    } else {
      lineBuffer.append(char)
    }
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    val str = String(b, off, len)
    for (char in str) {
      if (char == '\n') {
        processLine(lineBuffer.toString())
        lineBuffer.clear()
      } else {
        lineBuffer.append(char)
      }
    }
  }

  override fun close() {
    if (lineBuffer.isNotEmpty()) {
      processLine(lineBuffer.toString())
      lineBuffer.clear()
    }
  }

  private fun processLine(line: String) {
    if (line.isEmpty()) return

    val parsed = parseLogcatLine(line)
    if (parsed != null) {
      val level = logcatLevelToLogPriority(parsed.level)
      logFn(level, { formatLogcatLine(parsed) }) { parsed.message }
    } else {
      logFn(LogPriority.DEBUG, { formatUnparsedLogcat(it) }) { line }
    }
  }

  companion object {
    fun logcatLevelToLogPriority(level: Char): LogPriority = when (level) {
      'V' -> LogPriority.VERBOSE
      'D' -> LogPriority.DEBUG
      'I' -> LogPriority.INFO
      'W' -> LogPriority.WARN
      'E', 'F', 'S' -> LogPriority.ERROR
      else -> LogPriority.DEBUG
    }

    fun formatLogcatLine(parsed: LogcatLine): String {
      return "[${parsed.time} ${parsed.level} (logcat ${parsed.pid} ${parsed.tid} ${parsed.tag})] ${parsed.message}"
    }

    fun formatUnparsedLogcat(message: String): String {
      return "[logcat] $message"
    }
  }
}
