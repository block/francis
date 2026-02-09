package com.squareup.francis.process

import logcat.LogPriority
import com.squareup.francis.log.log
import com.squareup.francis.log.logFormatted
import com.squareup.francis.log.prefix
import com.squareup.francis.log.timeFormatter
import java.io.File
import java.io.OutputStream
import java.time.LocalDateTime

class LineBufferedLogOutputStream(
  val formatter: ProcessOutputFormatter
) : OutputStream(), PidAware {
  override fun setPid(pid: Long) {
    formatter.initialize(pid)
  }
  private val lineBuffer = StringBuilder()

  override fun write(b: Int) {
    val c = b.toChar()
    if (c == '\n') {
      flushLine()
    } else {
      lineBuffer.append(c)
    }
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    for (i in off until off + len) {
      write(b[i].toInt() and 0xFF)
    }
  }

  private fun flushLine() {
    logFormatted(formatter.logLevel, formatter = formatter) { lineBuffer.toString() }
    lineBuffer.clear()
  }

  override fun close() {
    if (lineBuffer.isNotEmpty()) {
      lineBuffer.append("⏎")
      flushLine()
    }
  }
}

class ProcessOutputFormatter(
  val logLevel: LogPriority = LogPriority.DEBUG,
  private var streamType: String = "?"
) : (String) -> String {
  private var pid: Long = -1

  override fun invoke(message: String): String {
    val timestamp = LocalDateTime.now().format(timeFormatter)
    return "[$timestamp ${logLevel.prefix} ($pid $streamType)] $message"
  }

  fun initialize(pid: Long) {
    this.pid = pid
  }
}

private val loggingProcessTemplate = TeeProcessBuilder(emptyList()).apply {
  stdinRedirect = InputRedirectSpec.NULL
  stdoutRedirect = loggedStdoutRedirectSpec()
  stderrRedirect = loggedStderrRedirectSpec()
  logPriority = LogPriority.INFO
}

val subproc = SubProc()

class SubProc(private val template: TeeProcessBuilder = loggingProcessTemplate) {

  fun start(
    vararg command: String,
    commandRepr: String? = null,
    configure: TeeProcessBuilder.() -> Unit = {}
  ): TeeProcess {
    val builder = template.copy().apply(configure)
    builder.command = command.toList()
    commandRepr?.let { builder.commandRepr = it }
    return builder.start()
  }

  fun run(
    vararg command: String,
    commandRepr: String? = null,
    allowedExitCodes: List<Int> = listOf(0),
    configure: TeeProcessBuilder.() -> Unit = {}
  ) {
    start(*command, commandRepr = commandRepr, configure = configure)
      .checkExitCode(allowedExitCodes)
  }

  fun stdout(
    vararg command: String,
    commandRepr: String? = null,
    chomp: Boolean = true,
    allowedExitCodes: List<Int> = listOf(0),
    configure: TeeProcessBuilder.() -> Unit = {}
  ): String {
    return start(*command, commandRepr = commandRepr) {
      configure()
      stdoutRedirect += OutputRedirectSpec.PIPE
    }.stdoutText(chomp, allowedExitCodes)
  }
}

fun loggedStdoutRedirectSpec(logLevel: LogPriority = LogPriority.DEBUG): OutputRedirectSpec {
  val formatter = ProcessOutputFormatter(logLevel, "stdout")
  val target = OutputTarget.ToStream(
    LineBufferedLogOutputStream(formatter), autoClose = true
  )
  return OutputRedirectSpec(listOf(target))
}

fun loggedStderrRedirectSpec(logLevel: LogPriority = LogPriority.DEBUG): OutputRedirectSpec {
  val formatter = ProcessOutputFormatter(logLevel, "stderr")
  val target = OutputTarget.ToStream(
    LineBufferedLogOutputStream(formatter), autoClose = true
  )
  return OutputRedirectSpec(listOf(target))
}

fun loggedStdinRedirectSpec(logLevel: LogPriority = LogPriority.DEBUG): InputRedirectSpec {
  val formatter = ProcessOutputFormatter(logLevel, "stdin")
  val target = OutputTarget.ToStream(
    LineBufferedLogOutputStream(formatter), autoClose = true
  )
  return InputRedirectSpec(InputSource.Pipe, listOf(target))
}

private val SAFE_ARG_PATTERN = Regex("^[a-zA-Z0-9_/.=:-]+$")

fun shellEscape(args: List<String>): String = args.joinToString(" ") { shellEscapeArg(it) }

fun shellEscapeArg(arg: String): String = when {
  arg.isEmpty() -> "''"
  SAFE_ARG_PATTERN.matches(arg) -> arg
  !arg.contains('\'') -> "'$arg'"
  else -> "\"" + arg.replace("""[\$`"\\!]""".toRegex()) { "\\${it.value}" } + "\""
}
