package com.squareup.francis

/**
 * Represents a parsed logcat line in threadtime format.
 *
 * Example input: `01-13 03:00:25.346   603   762 W IPCThreadState: Sending oneway calls to frozen process.`
 */
data class LogcatLine(
  val date: String,
  val time: String,
  val pid: Int,
  val tid: Int,
  val level: Char,
  val tag: String,
  val message: String,
)

private val LOGCAT_REGEX = Regex(
  """^(\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFS])\s+([^:]+):\s*(.*)$"""
)

fun parseLogcatLine(line: String): LogcatLine? {
  val match = LOGCAT_REGEX.matchEntire(line) ?: return null
  val (date, time, pid, tid, level, tag, message) = match.destructured
  return LogcatLine(
    date = date,
    time = time,
    pid = pid.toInt(),
    tid = tid.toInt(),
    level = level[0],
    tag = tag.trim(),
    message = message,
  )
}
