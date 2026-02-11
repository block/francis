package com.squareup.francis

import com.squareup.francis.process.OutputRedirectSpec
import com.squareup.francis.process.OutputTarget
import com.squareup.francis.process.TeeProcess
import com.squareup.francis.logging.log
import logcat.LogPriority.VERBOSE
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Helper to capture logcat output for a package from the moment it starts.
 *
 * Usage:
 * ```
 * PackageLogcatHelper.create("com.example.app").use { logcat ->
 *   // Launch the app...
 *   logcat.awaitPidAndStartLogcat()
 *   // App runs...
 * } // logcat stops automatically
 * ```
 */
class PackageLogcatHelper private constructor(
  private val packageName: String,
  private val deviceTime: String,
  private val timeoutMs: Long,
  private val stdout: OutputRedirectSpec,
  private val filterSpecs: List<String>,
) : Closeable {

  private var logcatProc: TeeProcess? = null

  /**
   * Wait for the package to start and begin streaming logcat.
   * Returns the PID of the package.
   *
   * Uses logcat to detect when AndroidJUnitRunner logs its onCreate message,
   * which reliably indicates the instrumentation process has started.
   * This is more reliable than pidof which can find stale PIDs.
   */
  fun awaitPidAndStartLogcat(): Long {
    val pid = awaitInstrumentationPid()
    log { "Found $packageName with pid $pid, starting logcat from $deviceTime" }

    val logcatArgs = arrayOf("logcat", "-T", deviceTime, "--pid", pid.toString()) + filterSpecs
    logcatProc = adb.shellStart(*logcatArgs) { stdoutRedirect = stdout }

    return pid
  }

  private fun awaitInstrumentationPid(): Long {
    log(VERBOSE) { "Starting logcat watcher for AndroidJUnitRunner" }
    val watcher = adb.cmdStart("logcat", "-T", "1", "-s", "AndroidJUnitRunner:*") {
      stdoutRedirect = OutputRedirectSpec.PIPE
      stderrRedirect = OutputRedirectSpec.DISCARD
    }

    val pidFuture = CompletableFuture<Long>()

    val readerThread = Thread {
      try {
        val reader = watcher.stdoutReader
        while (!Thread.currentThread().isInterrupted) {
          val line = reader.readLine() ?: break
          val parsed = parseLogcatLine(line) ?: continue

          if (parsed.tag == "AndroidJUnitRunner" && parsed.message.startsWith("onCreate")) {
            log(VERBOSE) { "Found AndroidJUnitRunner onCreate: $line" }
            pidFuture.complete(parsed.pid.toLong())
            return@Thread
          }
        }
      } catch (_: Exception) {
      }
    }.also {
      it.isDaemon = true
      it.start()
    }

    try {
      return pidFuture.get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
      throw PithyException(1, "Timed out waiting for AndroidJUnitRunner to start")
    } finally {
      watcher.destroy()
    }
  }

  override fun close() {
    logcatProc?.let {
      log { "Stopping logcat for $packageName" }
      it.destroy()
    }
  }

  companion object {
    /**
     * Create a new PackageLogcatHelper, capturing the current device time.
     *
     * @param packageName The package to monitor
     * @param timeoutMs How long to wait for the package to start (default 5 seconds)
     * @param stdout Where to send logcat output (default: PackageLogcatParser that formats lines to match existing logging)
     * @param filterSpecs Logcat filter specifications (default: "UiDevice:i", "*:v")
     */
    fun create(
      packageName: String,
      timeoutMs: Long = 30000,
      stdout: OutputRedirectSpec = OutputRedirectSpec(listOf(OutputTarget.ToStream(PackageLogcatParser()))),
      filterSpecs: List<String> = listOf("UiDevice:i", "*:v"),
    ): PackageLogcatHelper {
      val deviceTime = adb.shellStdout("date", "+%Y-%m-%d %H:%M:%S.%3N")
      log { "Captured device time: $deviceTime" }
      return PackageLogcatHelper(packageName, deviceTime, timeoutMs, stdout, filterSpecs)
    }
  }
}
