package com.squareup.francis.log

import logcat.LogPriority
import logcat.LogcatLogger
import logcat.logcat as logcatImpl
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val LogPriority.prefix: String
  get() = name.first().toString()

val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
val realStdErr: PrintStream = System.err
val realStdOut: PrintStream = System.out

private var minLogPriority: LogPriority = LogPriority.INFO
private var rawArgs: Array<String>? = null
var logFile: File? = null
  private set
private val logFileStream: PrintStream by lazy {
  val inner = logFile?.let { FileOutputStream(it) } ?: OutputStream.nullOutputStream()
  PrintStream(inner, true)
}

val stdOut: LogStream by lazy {
  val isTty = System.console() != null
  val formatter: (String) -> String = if (isTty) {
    { message ->
      val timestamp = LocalDateTime.now().format(timeFormatter)
      "[$timestamp stdout] $message"
    }
  } else {
    { message -> message }
  }
  LogStream(realStdOut, formatter)
}

val stdErr: LogStream by lazy {
  LogStream(realStdErr) { message ->
    val timestamp = LocalDateTime.now().format(timeFormatter)
    "[$timestamp stderr] $message"
  }
}

class LogStream(val wrapped: PrintStream, val defaultFormatter: (String) -> String) {
  fun println(
    s: String,
    formatter: (String) -> String = defaultFormatter,
    logFileOnly: Boolean = false
  ) {
    val escaped = s.escapeControlChars()
    val formatted = formatter(escaped)
    if (!logFileOnly) {
      wrapped.println(formatted)
    }
    logFileStream.println(formatted)
  }
}

fun String.escapeControlChars(): String {
  val sb = StringBuilder()
  for (c in this) {
    when {
      c == '\n' -> sb.append(c)
      c == '\t' -> sb.append(c)
      c.isISOControl() -> {
        sb.append("\\x")
        sb.append(c.code.toString(16).padStart(2, '0'))
      }
      else -> sb.append(c)
    }
  }
  return sb.toString()
}

private var initializedLogPriority: LogPriority? = null
private var initializedLogPath: String? = null

fun setupLogging(minPriority: LogPriority, logPath: String) {
  if (initializedLogPriority != null) {
    if (initializedLogPriority != minPriority || initializedLogPath != logPath) {
      throw IllegalStateException(
        "setupLogging called with different args: " +
          "was ($initializedLogPriority, $initializedLogPath), " +
          "now ($minPriority, $logPath)"
      )
    }
    return
  }

  initializedLogPriority = minPriority
  initializedLogPath = logPath
  minLogPriority = minPriority
  logFile = File(logPath)

  LogcatLogger.install()
  LogcatLogger.loggers += FrancisLogger

  val timestamp = LocalDateTime.now().format(timeFormatter)
  stdErr.println("Execution started at [$timestamp]", logFileOnly = true)
  rawArgs?.let {
    stdErr.println("raw-args: ${it.joinToString(" ")}", logFileOnly = true)
  }
}

fun setRawArgs(args: Array<String>) {
  rawArgs = args
}

private object FrancisLogger : LogcatLogger {
  override fun isLoggable(priority: LogPriority, tag: String): Boolean = true

  override fun log(priority: LogPriority, tag: String, message: String) {
    val logFileOnly = priority.priorityInt < minLogPriority.priorityInt
    val prefix = when (priority) {
      LogPriority.VERBOSE -> "V"
      LogPriority.DEBUG -> "D"
      LogPriority.INFO -> "I"
      LogPriority.WARN -> "W"
      LogPriority.ERROR -> "E"
      LogPriority.ASSERT -> "A"
    }
    val formatter: (String) -> String = { msg ->
      val timestamp = LocalDateTime.now().format(timeFormatter)
      "[$timestamp $prefix] $msg"
    }
    stdErr.println(message, formatter = formatter, logFileOnly = logFileOnly)
  }
}

fun logFormatted(
  level: LogPriority,
  formatter: (String) -> String = { msg ->
    val timestamp = LocalDateTime.now().format(timeFormatter)
    "[$timestamp ${level.prefix}] $msg"
  },
  message: () -> String,
) {
  val logFileOnly = level.priorityInt < minLogPriority.priorityInt
  stdErr.println(message(), formatter = formatter, logFileOnly = logFileOnly)
}

const val DEFAULT_TAG = "francis"

inline fun log(
  priority: LogPriority = LogPriority.DEBUG,
  message: () -> String
) = logcatImpl(tag = DEFAULT_TAG, priority = priority, message = message)
