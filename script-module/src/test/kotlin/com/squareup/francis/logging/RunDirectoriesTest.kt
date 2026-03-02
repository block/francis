package com.squareup.francis.script.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RunDirectoriesTest {
  @JvmField
  @Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun nextNumberedRunDir_startsAtZero_whenEmpty() {
    val runsDir = tempFolder.newFolder("runs")

    val runDir = nextNumberedRunDir(runsDir)

    assertThat(runDir.absolutePath).isEqualTo("${runsDir.absolutePath}/000000")
    assertThat(runDir.exists()).isTrue()
  }

  @Test
  fun nextNumberedRunDir_incrementsFromExisting() {
    val runsDir = tempFolder.newFolder("runs")
    File(runsDir, "000000").mkdir()
    File(runsDir, "000001").mkdir()
    File(runsDir, "000002").mkdir()

    val runDir = nextNumberedRunDir(runsDir)

    assertThat(runDir.absolutePath).isEqualTo("${runsDir.absolutePath}/000003")
  }

  @Test
  fun nextNumberedRunDir_handlesGaps() {
    val runsDir = tempFolder.newFolder("runs")
    File(runsDir, "000000").mkdir()
    File(runsDir, "000005").mkdir()

    val runDir = nextNumberedRunDir(runsDir)

    assertThat(runDir.absolutePath).isEqualTo("${runsDir.absolutePath}/000006")
  }

  @Test
  fun nextNumberedRunDir_ignoresNonNumericEntries() {
    val runsDir = tempFolder.newFolder("runs")
    File(runsDir, "000002").mkdir()
    File(runsDir, "not-a-number").mkdir()

    val runDir = nextNumberedRunDir(runsDir)

    assertThat(runDir.absolutePath).isEqualTo("${runsDir.absolutePath}/000003")
  }
}
