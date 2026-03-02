package com.squareup.francis.script.process

import com.google.common.truth.Truth.assertThat
import com.squareup.francis.script.logging.logFile
import com.squareup.francis.script.logging.setupLogging
import logcat.LogPriority.DEBUG
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LineBufferedLogOutputStreamTest {
  companion object {
    @JvmField
    @ClassRule
    val tempFolder = TemporaryFolder()

    @JvmStatic
    @BeforeClass
    fun setupClassLogging() {
      setupLogging(DEBUG, tempFolder.newFile("line-buffered.log").absolutePath)
    }
  }

  @Before
  fun clearLogFile() {
    logFile!!.writeText("")
  }

  @Test
  fun decodesUtf8AcrossWrites() {
    val stream = LineBufferedLogOutputStream(formatter = ProcessOutputFormatter(streamType = "test"))

    val message = "héllo 世界"
    val bytes = message.toByteArray(Charsets.UTF_8)
    stream.write(bytes, 0, 2)
    stream.write(bytes, 2, bytes.size - 2)
    stream.write('\n'.code)
    stream.close()

    assertThat(logFile!!.readText()).contains(message)
  }

  @Test
  fun closeFlushesIncompleteLineAsUtf8() {
    val stream = LineBufferedLogOutputStream(formatter = ProcessOutputFormatter(streamType = "test"))

    val message = "日本語"
    val bytes = message.toByteArray(Charsets.UTF_8)
    stream.write(bytes, 0, 4)
    stream.write(bytes, 4, bytes.size - 4)
    stream.close()

    assertThat(logFile!!.readText()).contains("${message}⏎")
  }
}
