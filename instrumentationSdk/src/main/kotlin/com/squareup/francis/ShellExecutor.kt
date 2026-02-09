package com.squareup.francis

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Executes shell commands via UiAutomation with streaming output.
 *
 * ## The Problem
 * `UiAutomation.executeShellCommand()` uses `Runtime.exec(String)` internally which:
 * - Splits the command on whitespace (quotes are not interpreted)
 * - Does not use a shell interpreter (no pipes, redirects, semicolons)
 * - Only returns stdout; stderr goes to device log
 *
 * ## The Solution
 * Write a wrapper script that:
 * 1. Starts the command in background with stderr merged to stdout
 * 2. Emits `___PID=<pid>` immediately
 * 3. Waits for the process and exits with its exit code
 *
 * ## Usage
 * ```kotlin
 * val executor = ShellExecutor()
 * val process = executor.execute("ls", "-la", "/sdcard")
 *
 * // Stream output as it arrives
 * process.forEachLine { line -> println(line) }
 *
 * // Get exit code (blocks until complete)
 * val exitCode = process.exitCode()
 * ```
 */
class ShellExecutor {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val automation = instrumentation.uiAutomation

  private val scriptDir: File by lazy {
    instrumentation.context.externalMediaDirs.firstOrNull()
      ?: throw IllegalStateException(
        "No external media dir available. Ensure app has storage permissions."
      )
  }

  /**
   * Executes a command with stderr merged into stdout.
   *
   * @param command The command and arguments to execute
   * @return [ShellProcess] for streaming output and getting exit code
   */
  fun execute(vararg command: String): ShellProcess {
    require(command.isNotEmpty()) { "Command must not be empty" }
    Log.d("ShellExecutor", command.joinToString(" "))

    val wrapperScript = File(scriptDir, "shell_executor_${System.nanoTime()}.sh")
    val escapedCommand = command.joinToString(" ") { escapeArg(it) }

    wrapperScript.writeText("""
      #!/bin/sh
      $escapedCommand 2>&1 &
      echo "___PID=$!"
      wait $!
      echo "___EXIT=$?"
    """.trimIndent())
    wrapperScript.setReadable(true, false)

    val pfd = automation.executeShellCommand("sh ${wrapperScript.absolutePath}")
    return ShellProcess(pfd, wrapperScript)
  }

  private fun escapeArg(arg: String): String {
    if (arg.all { it.isLetterOrDigit() || it in "-_./=@:" }) return arg
    return "'${arg.replace("'", "'\\''")}'"
  }

  /**
   * Executes a simple command (no shell features needed).
   * Faster than [execute] but doesn't capture stderr or exit code.
   */
  fun executeSimple(command: String): String {
    return automation.executeShellCommand(command).readAndClose()
  }

  private fun ParcelFileDescriptor.readAndClose(): String {
    return BufferedReader(InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(this)))
      .use { it.readText().trim() }
  }
}

/**
 * A running shell process with streaming output access.
 *
 * The process output (combined stdout/stderr) can be consumed via:
 * - [forEachLine] - iterate lines as they arrive
 * - [readText] - read all output at once (blocks until complete)
 *
 * Call [exitCode] to block until the process completes and get the exit code.
 */
class ShellProcess internal constructor(
  private val pfd: ParcelFileDescriptor,
  private val scriptFile: File
) : AutoCloseable {
  private val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
  private val reader = BufferedReader(InputStreamReader(stream))

  private var pidValue: Int? = null
  private val pidLatch = CountDownLatch(1)
  private val bufferedLines = mutableListOf<String>()

  private var exitCodeValue: Int? = null
  private val completionLatch = CountDownLatch(1)
  private var consumed = false

  /**
   * Iterates over output lines as they arrive.
   * Blocks until the process completes.
   */
  fun forEachLine(action: (String) -> Unit) {
    check(!consumed) { "Output has already been consumed" }
    consumed = true

    try {
      for (line in bufferedLines) {
        action(line)
      }
      bufferedLines.clear()

      reader.forEachLine { line ->
        when {
          line.startsWith("___PID=") -> {
            pidValue = line.removePrefix("___PID=").toIntOrNull()
            pidLatch.countDown()
          }
          line.startsWith("___EXIT=") -> {
            exitCodeValue = line.removePrefix("___EXIT=").toIntOrNull() ?: -1
          }
          else -> action(line)
        }
      }
    } finally {
      pidLatch.countDown()
      completionLatch.countDown()
      cleanup()
    }
  }

  /**
   * Returns the PID of the spawned process, blocking until available.
   */
  fun pid(): Int {
    while (pidLatch.count > 0) {
      val line = reader.readLine() ?: break
      when {
        line.startsWith("___PID=") -> {
          pidValue = line.removePrefix("___PID=").toIntOrNull()
          pidLatch.countDown()
        }
        line.startsWith("___EXIT=") -> {
          exitCodeValue = line.removePrefix("___EXIT=").toIntOrNull() ?: -1
        }
        else -> bufferedLines.add(line)
      }
    }
    return pidValue ?: -1
  }

  /**
   * Returns the PID if already available, or null.
   */
  fun pidOrNull(): Int? = pidValue

  /**
   * Reads all output as text. Blocks until process completes.
   */
  fun readText(): String {
    val builder = StringBuilder()
    forEachLine { line ->
      if (builder.isNotEmpty()) builder.append('\n')
      builder.append(line)
    }
    return builder.toString()
  }

  /**
   * Returns the exit code, blocking until the process completes.
   * If output hasn't been consumed yet, drains and discards it.
   */
  fun exitCode(): Int {
    if (!consumed) {
      forEachLine { }
    }
    completionLatch.await()
    return exitCodeValue ?: -1
  }

  /**
   * Returns the exit code if available, or null if still running.
   */
  fun exitCodeOrNull(): Int? = if (completionLatch.count == 0L) exitCodeValue else null

  /**
   * Waits for the process to complete with a timeout.
   * @return true if completed, false if timed out
   */
  fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
    if (!consumed) {
      forEachLine { }
    }
    return completionLatch.await(timeout, unit)
  }

  val isAlive: Boolean get() = completionLatch.count != 0L

  override fun close() {
    cleanup()
  }

  private fun cleanup() {
    runCatching { reader.close() }
    runCatching { stream.close() }
    runCatching { pfd.close() }
    scriptFile.delete()
  }
}
