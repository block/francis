package com.squareup.francis

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.enum
import com.squareup.francis.logging.setupLogging
import logcat.LogPriority


interface BaseValues {
  val verbosity: LogPriority
  val devMode: Boolean
}

/**
 * Runtime configuration that's not specified via CLI args.
 */
class BaseConfig(
  val francisRunDir: String = run {
    val runsDir = java.io.File(Xdg.francisData, "runs")
    runsDir.mkdirs()
    val nextNum = (runsDir.list()?.mapNotNull { it.toIntOrNull() }?.maxOrNull() ?: -1) + 1
    val runDir = java.io.File(runsDir, "%06d".format(nextNum))
    runDir.mkdirs()
    runDir.absolutePath
  },
  /**
   * Subdirectory within francisRunDir for output files. Empty string means output directly to
   * francisRunDir. Used by A/B comparisons to separate baseline and treatment outputs.
   */
  val outputSubdir: String = "",
  /**
   * Shared cache for APK resolution. Used by A/B comparisons to ensure baseline and treatment
   * resolve to the same APK when using dynamic sources (e.g., downloading from a URL that may
   * change between requests).
   */
  val resolutionCache: MutableMap<String, String> = mutableMapOf(),
) {
  val hostOutputDir: String get() = if (outputSubdir.isEmpty()) francisRunDir else "$francisRunDir/$outputSubdir"

  fun withOutputSubdir(subdir: String) = BaseConfig(francisRunDir, subdir, resolutionCache)
}

class BaseOptions(val config: BaseConfig = BaseConfig()) : OptionGroup(), BaseValues {
  private val verbosityFlags: List<LogPriority> by option()
    .switch(
      "--verbose" to LogPriority.VERBOSE,
      "--debug" to LogPriority.DEBUG,
      "--info"  to LogPriority.INFO,
      "--warn"  to LogPriority.WARN,
      "--error" to LogPriority.ERROR,
    )
    .multiple()

  override val verbosity: LogPriority by lazy {
    when (verbosityFlags.size) {
      0 -> LogPriority.INFO // default
      1 -> verbosityFlags[0]
      else -> throw PithyException(
        1,
        "Options --verbose/--debug/--info/--warn/--error are mutually exclusive."
      )
    }
  }

  override val devMode by option("--dev", help = "Enable dev mode").flag()

  fun setup() {
    setupLogging(verbosity, "${config.francisRunDir}/francis.log")
  }
}