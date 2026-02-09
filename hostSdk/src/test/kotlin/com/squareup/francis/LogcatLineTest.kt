package com.squareup.francis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LogcatLineTest {

  @Test
  fun parseLogcatLine_validLine_parsesProperly() {
    val line = "01-13 03:00:25.346   603   762 W IPCThreadState: Sending oneway calls to frozen process."

    val result = parseLogcatLine(line)

    assertThat(result).isNotNull()
    assertThat(result!!.date).isEqualTo("01-13")
    assertThat(result.time).isEqualTo("03:00:25.346")
    assertThat(result.pid).isEqualTo(603)
    assertThat(result.tid).isEqualTo(762)
    assertThat(result.level).isEqualTo('W')
    assertThat(result.tag).isEqualTo("IPCThreadState")
    assertThat(result.message).isEqualTo("Sending oneway calls to frozen process.")
  }

  @Test
  fun parseLogcatLine_debugLevel() {
    val line = "12-25 14:30:00.123  1234  5678 D MyTag: Debug message"

    val result = parseLogcatLine(line)

    assertThat(result).isNotNull()
    assertThat(result!!.level).isEqualTo('D')
    assertThat(result.tag).isEqualTo("MyTag")
    assertThat(result.message).isEqualTo("Debug message")
  }

  @Test
  fun parseLogcatLine_errorLevel() {
    val line = "06-15 08:45:12.999 99999 11111 E CrashHandler: Fatal exception occurred"

    val result = parseLogcatLine(line)

    assertThat(result).isNotNull()
    assertThat(result!!.pid).isEqualTo(99999)
    assertThat(result.tid).isEqualTo(11111)
    assertThat(result.level).isEqualTo('E')
    assertThat(result.tag).isEqualTo("CrashHandler")
  }

  @Test
  fun parseLogcatLine_emptyMessage() {
    val line = "01-01 00:00:00.000     1     1 I Tag: "

    val result = parseLogcatLine(line)

    assertThat(result).isNotNull()
    assertThat(result!!.message).isEmpty()
  }

  @Test
  fun parseLogcatLine_messageWithColons() {
    val line = "01-13 12:00:00.000  1000  1000 I NetworkInfo: state: CONNECTED, type: WIFI"

    val result = parseLogcatLine(line)

    assertThat(result).isNotNull()
    assertThat(result!!.tag).isEqualTo("NetworkInfo")
    assertThat(result.message).isEqualTo("state: CONNECTED, type: WIFI")
  }

  @Test
  fun parseLogcatLine_invalidLine_returnsNull() {
    assertThat(parseLogcatLine("not a logcat line")).isNull()
    assertThat(parseLogcatLine("")).isNull()
    assertThat(parseLogcatLine("01-13 03:00:25.346")).isNull()
  }

  @Test
  fun parseLogcatLine_allLogLevels() {
    val levels = listOf('V', 'D', 'I', 'W', 'E', 'F', 'S')
    for (level in levels) {
      val line = "01-01 00:00:00.000     1     1 $level Tag: msg"
      val result = parseLogcatLine(line)
      assertThat(result).isNotNull()
      assertThat(result!!.level).isEqualTo(level)
    }
  }
}
