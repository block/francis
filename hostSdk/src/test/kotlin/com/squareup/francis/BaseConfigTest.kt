package com.squareup.francis

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BaseConfigTest {
  @JvmField
  @Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun francisRunDir_startsAtZero_whenEmpty() {
    val runsDir = tempFolder.newFolder("runs")
    val config = createConfig(runsDir)

    assertThat(config.francisRunDir).isEqualTo("${runsDir.absolutePath}/000000")
    assertThat(File(config.francisRunDir).exists()).isTrue()
  }

  @Test
  fun francisRunDir_incrementsFromExisting() {
    val runsDir = tempFolder.newFolder("runs")
    File(runsDir, "000000").mkdir()
    File(runsDir, "000001").mkdir()
    File(runsDir, "000002").mkdir()
    val config = createConfig(runsDir)

    assertThat(config.francisRunDir).isEqualTo("${runsDir.absolutePath}/000003")
  }

  @Test
  fun francisRunDir_handlesGaps() {
    val runsDir = tempFolder.newFolder("runs")
    File(runsDir, "000000").mkdir()
    File(runsDir, "000005").mkdir()
    val config = createConfig(runsDir)

    assertThat(config.francisRunDir).isEqualTo("${runsDir.absolutePath}/000006")
  }

  @Test
  fun francisRunDir_ignoresNonNumericEntries() {
    val runsDir = tempFolder.newFolder("runs")
    File(runsDir, "000002").mkdir()
    File(runsDir, "not-a-number").mkdir()
    val config = createConfig(runsDir)

    assertThat(config.francisRunDir).isEqualTo("${runsDir.absolutePath}/000003")
  }

  @Test
  fun hostOutputDir_usesOutputSubdir() {
    val runsDir = tempFolder.newFolder("runs")
    val config = createConfig(runsDir)

    assertThat(config.hostOutputDir).isEqualTo("${config.francisRunDir}/output")
  }

  @Test
  fun withOutputSubdir_preservesFrancisRunDir() {
    val runsDir = tempFolder.newFolder("runs")
    val config = createConfig(runsDir)
    val newConfig = config.withOutputSubdir("baseline-output")

    assertThat(newConfig.francisRunDir).isEqualTo(config.francisRunDir)
    assertThat(newConfig.hostOutputDir).isEqualTo("${config.francisRunDir}/baseline-output")
  }

  private fun createConfig(runsDir: File): BaseConfig {
    runsDir.mkdirs()
    val nextNum = (runsDir.list()?.mapNotNull { it.toIntOrNull() }?.maxOrNull() ?: -1) + 1
    val runDir = File(runsDir, "%06d".format(nextNum))
    runDir.mkdirs()
    return BaseConfig(francisRunDir = runDir.absolutePath)
  }
}
