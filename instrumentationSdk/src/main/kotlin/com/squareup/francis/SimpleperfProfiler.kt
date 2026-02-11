package com.squareup.francis

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable

/**
 * Profiler that uses simpleperf to capture CPU profiles during benchmarks.
 *
 * @param outputDir Directory to write perf data files. Must be writable by shell (e.g. /data/local/tmp)
 *   since simpleperf runs as a shell process, not as the app.
 */
internal class SimpleperfProfiler(
  private val outputDir: String,
  private val testName: String,
  private val callGraph: String? = null,
) : Closeable {
  private val shell = ShellExecutor()
  private val iteration = nextIteration()
  private var simpleperfPid: Int = -1
  private var process: ShellProcess? = null

  fun start() {
    checkForExistingSimpleperf()

    val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", java.util.Locale.US).format(java.util.Date())
    val outputPath = "$outputDir/${testName}_iter${iteration.toString().padStart(3, '0')}_$timestamp.simpleperf.data"
    val targetPackage = getTargetPackage()

    val eventArgs = if (supportsCpuCycles) emptyList() else listOf("-e", "cpu-clock")
    val callGraphArgs = callGraph?.let { listOf("--call-graph", it) } ?: emptyList()
    process = shell.execute(
      "simpleperf", "record", *callGraphArgs.toTypedArray(), *eventArgs.toTypedArray(), "--app", targetPackage, "-o", outputPath
    )
    simpleperfPid = process!!.pid()

    // This doesn't necessarily mean that simpleperf is actually capturing data yet. We could be
    // notified of that with the `--start_profiling_fd` option, but it happens so quickly that I'm
    // not sure it's actually an issue.
    Log.d(TAG, "simpleperf started with pid=$simpleperfPid")

    startOutputLogger()
  }

  private fun checkForExistingSimpleperf() {
    val pgrep = shell.execute("pgrep", "simpleperf")
    val existing = pgrep.readText()
    if (existing.isNotBlank()) {
      Log.w(TAG, "simpleperf process already running: $existing")
    }
  }

  private fun startOutputLogger() {
    Thread {
      process?.forEachLine { line ->
        Log.d(TAG, line)
      }
    }.start()
  }

  override fun close() {
    if (simpleperfPid <= 0) {
      Log.w(TAG, "No simpleperf process to stop")
      return
    }
    process?.let {
      if (!it.isAlive) {
        Log.w(TAG, "simpleperf already exited: ${it.exitCode()}")
        return
      }
    }

    val kill = shell.execute("kill", "-INT", simpleperfPid.toString())
    val exitCode = kill.exitCode()
    if (exitCode != 0) {
      Log.e(TAG, "Failed to signal simpleperf pid=$simpleperfPid")
    } else {
      Log.d(TAG, "Sent SIGINT to simpleperf pid=$simpleperfPid")
    }

    process?.let {
      // It can take a while for simpleperf to dump all of its data to disk
      for (i in 0..50) {
        if (!it.isAlive) {
          break
        }
        Thread.sleep(100)
      }
      if (it.isAlive) {
        Log.w(TAG, "simpleperf still running 5s after sending SIGINT")
      } else {
        Log.d(TAG, "simpleperf exited with code=${it.exitCode()}")
      }
    }
  }

  private fun getTargetPackage(): String {
    return InstrumentationRegistry.getInstrumentation().targetContext.packageName
  }

  private val supportsCpuCycles: Boolean by lazy {
    val simpleperfList = shell.execute("simpleperf", "list").readText()
    Log.d(TAG, "simpleperfList: $simpleperfList")
    val result = simpleperfList.lines().any { it.trim() == "cpu-cycles" }
    Log.d(TAG, "supportsCpuCycles: $result")
    result
  }

  companion object {
    private const val TAG = "SimpleperfProfiler"
    private var iterationCounter = 0

    @Synchronized
    private fun nextIteration(): Int = iterationCounter++
  }
}
