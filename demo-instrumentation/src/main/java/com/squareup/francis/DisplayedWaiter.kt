package com.squareup.francis

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Monitors logcat for the "Displayed" log message that indicates an activity launch is complete.
 * This ensures the Perfetto trace captures the complete "launching" slice from ActivityMetricsLogger.
 *
 * Uses UiAutomation.executeShellCommand() which runs as shell and streams output.
 *
 * Usage:
 * ```
 * measureBlock = {
 *     val waiter = DisplayedWaiter(packageName)
 *     startActivityAndWait()
 *     waiter.await()
 * }
 * ```
 */
class DisplayedWaiter(private val packageName: String) {
    private val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val pfd: ParcelFileDescriptor = automation.executeShellCommand(
        "logcat -T 1 -s ActivityTaskManager:I"
    )
    private val reader: BufferedReader = BufferedReader(
        InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(pfd))
    )

    fun await(timeoutMs: Long = 5000) {
        var found = false
        val thread = Thread {
            while (true) {
                val line = reader.readLine() ?: break
                if (line.contains("Displayed $packageName")) {
                    found = true
                    break
                }
            }
        }
        thread.start()
        thread.join(timeoutMs)
        reader.close()
        if (!found) {
            error("Timed out waiting for Displayed $packageName")
        }
    }
}
