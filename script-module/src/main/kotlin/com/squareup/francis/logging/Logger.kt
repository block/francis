package com.squareup.francis.script.logging

import logcat.LogPriority
import logcat.LogcatLogger
import logcat.logcat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val LogPriority.prefix: String
  get() = name.first().toString()

val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

var logFile: File? = null
  private set
private val nullLogFileStream = PrintStream(OutputStream.nullOutputStream(), true)

class FrancisLogger(
  val wrapped: PrintStream,
  val defaultFormatter: (String) -> String,
  var minLogPriority: LogPriority,
  val logPath: String?,
  val logFileStream: PrintStream,
) : LogcatLogger {
  fun println(
    s: String,
    formatter: (String) -> String = defaultFormatter,
    logFileOnly: Boolean = false,
  ) {
    val escaped = s.escapeControlChars()
    val formatted = formatter(escaped)
    if (!logFileOnly) {
      wrapped.println(formatted)
    }
    logFileStream.println(formatted)
  }

  // We always log, but (depending on minLogPriority) we might only log to
  // file.
  override fun isLoggable(priority: LogPriority, tag: String): Boolean = true

  override fun log(priority: LogPriority, tag: String, message: String) {
    val logFileOnly = priority.priorityInt < minLogPriority.priorityInt
    println(
      message,
      formatter = priorityFormatter(priority),
      logFileOnly = logFileOnly
    )
  }
}

private fun priorityFormatter(priority: LogPriority): (String) -> String = { msg ->
  val timestamp = LocalDateTime.now().format(timeFormatter)
  "[$timestamp ${priority.prefix}] $msg"
}

private fun installLogcatLogger(logger: FrancisLogger) {
  // repeated invocations of LogcatLogger.install cause noisy logging
  if (!LogcatLogger.isInstalled) {
    LogcatLogger.install()
  }
  LogcatLogger.loggers.removeAll { it is FrancisLogger }
  LogcatLogger.loggers += logger
}

private val stderrFormatter: (String) -> String = { message ->
  val timestamp = LocalDateTime.now().format(timeFormatter)
  "[$timestamp stderr] $message"
}

private val stdoutFormatter: (String) -> String = if (System.console() != null) {
  { message ->
    val timestamp = LocalDateTime.now().format(timeFormatter)
    "[$timestamp stdout] $message"
  }
} else {
  { message -> message }
}

private val stderrLogger = FrancisLogger(
  wrapped = System.err,
  defaultFormatter = stderrFormatter,
  minLogPriority = LogPriority.INFO,
  logPath = null,
  logFileStream = nullLogFileStream,
)

private var activeStdErrLogger: FrancisLogger = stderrLogger

private var activeStdOutLogger: FrancisLogger = FrancisLogger(
  wrapped = System.out,
  defaultFormatter = stdoutFormatter,
  minLogPriority = LogPriority.INFO,
  logPath = null,
  logFileStream = nullLogFileStream,
)

// Install a bootstrap logger at class init so logcat.logcat calls don't vanish before setup.
@Suppress("unused")
private val bootstrapLoggingInstalled: Unit = run {
  installLogcatLogger(activeStdErrLogger)
}

val stdOut: FrancisLogger
  get() = activeStdOutLogger

val stdErr: FrancisLogger
  get() = activeStdErrLogger

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

/**
 * Configures logging sinks and verbosity.
 *
 * This must be called from the main thread during process startup.
 */
fun setupLogging(minPriority: LogPriority, logPath: String) {
  if (activeStdErrLogger.logPath != null) {
    if (activeStdErrLogger.minLogPriority != minPriority || activeStdErrLogger.logPath != logPath) {
      throw IllegalStateException(
        "setupLogging called with different args: " +
          "was (${activeStdErrLogger.minLogPriority}, ${activeStdErrLogger.logPath}), " +
          "now ($minPriority, $logPath)"
      )
    }
    return
  }

  val configuredLogFile = File(logPath)
  val logFileStream = PrintStream(FileOutputStream(configuredLogFile), true)
  activeStdErrLogger = FrancisLogger(
    wrapped = System.err,
    defaultFormatter = stderrFormatter,
    minLogPriority = minPriority,
    logPath = logPath,
    logFileStream = logFileStream,
  )

  logFile = configuredLogFile
  activeStdOutLogger = FrancisLogger(
    wrapped = System.out,
    defaultFormatter = stdoutFormatter,
    minLogPriority = minPriority,
    logPath = logPath,
    logFileStream = logFileStream,
  )

  installLogcatLogger(activeStdErrLogger)
}

fun logFormatted(
  level: LogPriority,
  formatter: (String) -> String = { msg ->
    val timestamp = LocalDateTime.now().format(timeFormatter)
    "[$timestamp ${level.prefix}] $msg"
  },
  message: () -> String,
) {
  val logFileOnly = level.priorityInt < activeStdErrLogger.minLogPriority.priorityInt
  stdErr.println(
    message(),
    formatter = formatter,
    logFileOnly = logFileOnly
  )
}

const val DEFAULT_TAG = "com.squareup.francis.script"

fun log(
  priority: LogPriority = LogPriority.DEBUG,
  message: () -> String
) = logcat(tag = DEFAULT_TAG, priority = priority, message = message)
