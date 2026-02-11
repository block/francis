package com.squareup.francis

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.squareup.francis.log.log
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import java.io.File

class FrancisEntrypoint(
  name: String,
  private val helpText: String = "Francis - Android benchmark runner.",
) : CliktCommand(name = name) {
  override fun help(context: Context) = helpText

  override fun aliases() = mapOf(
    "benchmark" to listOf("bench"),
    "macrobenchmark" to listOf("bench"),
  )

  override fun run() = Unit
}

open class BenchCommand(
  runnerOpts: RunnerOptions,
) : CliktCommand(name = "bench") {
  override fun help(context: Context) = "Run a benchmark"

  protected val baseOpts by runnerOpts.base
  protected val runnerOpts by runnerOpts

  override fun run() {
    baseOpts.setup()
    runBenchmark(baseOpts, runnerOpts)
  }

  open fun runBenchmark(baseVals: BaseValues, runnerVals: RunnerValues) {
    Benchmark(baseVals, runnerVals).run()
  }
}

/** A do-nothing command used to parse and resolve options without running. */
private class ResolveCommand(
  runnerOpts: RunnerOptions,
) : CliktCommand(name = "resolve") {
  val baseOpts by runnerOpts.base
  val runnerOpts by runnerOpts

  override fun run() {
    baseOpts.setup()
    // Force resolution of lazy properties (populates resolution cache)
    runnerOpts.appApk
    runnerOpts.instrumentationApk
  }
}

class AbCommand(
  private val baseConfig: BaseConfig,
  private val runnerOptsFactory: (BaseConfig) -> RunnerOptions,
  private val benchCommandFactory: (RunnerOptions) -> BenchCommand = { opts -> BenchCommand(opts) },
  private val rawArgs: List<String> = emptyList(),
  runnerOpts: RunnerOptions = runnerOptsFactory(baseConfig),
  private val entrypointName: String = "francis",
) : CliktCommand(name = "ab") {
  private val abArgs: AbArgs by lazy {
    val abIdx = rawArgs.indexOf("ab")
    val argsAfterAb = if (abIdx >= 0) rawArgs.subList(abIdx + 1, rawArgs.size) else rawArgs
    preprocessAbArgs(argsAfterAb)
  }
  override fun help(context: Context) = """
    Run an A/B benchmark comparison.

    Options before --baseline-options or --treatment-options are shared.
    Options after --baseline-options apply only to the baseline run.
    Options after --treatment-options apply only to the treatment run.

    Example: $entrypointName ab --debug --baseline-options --no-aot --treatment-options --aot
  """.trimIndent()

  @Suppress("unused")
  private val baseOpts by runnerOpts.base
  @Suppress("unused")
  private val runnerOpts by runnerOpts

  override fun run() {
    val parsed = abArgs

    val baselineConfig = baseConfig.withOutputSubdir("baseline")
    val baselineRunnerOpts = runnerOptsFactory(baselineConfig)

    val treatmentConfig = baseConfig.withOutputSubdir("treatment")
    val treatmentRunnerOpts = runnerOptsFactory(treatmentConfig)

    // Resolve both before running either (resolution cache ensures --mr resolves consistently)
    log { "Resolving baseline options..." }
    ResolveCommand(baselineRunnerOpts).parse(parsed.baselineArgs())
    log { "Resolving treatment options..." }
    ResolveCommand(treatmentRunnerOpts).parse(parsed.treatmentArgs())

    val baselineVerbosity = baselineRunnerOpts.base.verbosity
    val treatmentVerbosity = treatmentRunnerOpts.base.verbosity
    if (baselineVerbosity != treatmentVerbosity) {
      throw PithyException(1, "Verbosity must be the same for baseline and treatment: $baselineVerbosity vs $treatmentVerbosity")
    }

    // Run both benchmarks
    log { "Running baseline benchmark..." }
    benchCommandFactory(baselineRunnerOpts).runBenchmark(baselineRunnerOpts.base, baselineRunnerOpts)
    log { "Baseline results: ${baselineRunnerOpts.hostOutputDir}" }

    log { "Running treatment benchmark..." }
    benchCommandFactory(treatmentRunnerOpts).runBenchmark(treatmentRunnerOpts.base, treatmentRunnerOpts)
    log { "Treatment results: ${treatmentRunnerOpts.hostOutputDir}" }

    log { "A/B comparison complete." }

    emitCompareCommands(
      baselineDir = File(baselineRunnerOpts.hostOutputDir),
      treatmentDir = File(treatmentRunnerOpts.hostOutputDir),
      entrypointName = entrypointName,
    )
  }
}

private fun emitCompareCommands(baselineDir: File, treatmentDir: File, entrypointName: String) {
  val baselineJsonFiles = baselineDir.walkTopDown()
    .filter { it.isFile && it.extension == "json" }
    .map { it.relativeTo(baselineDir).path }
    .toSet()

  val treatmentJsonFiles = treatmentDir.walkTopDown()
    .filter { it.isFile && it.extension == "json" }
    .map { it.relativeTo(treatmentDir).path }
    .toSet()

  val paired = baselineJsonFiles.intersect(treatmentJsonFiles)
  val baselineOnly = baselineJsonFiles - treatmentJsonFiles
  val treatmentOnly = treatmentJsonFiles - baselineJsonFiles

  for (relativePath in baselineOnly) {
    log(WARN) { "Baseline-only JSON file (no treatment match): ${File(baselineDir, relativePath).absolutePath}" }
  }
  for (relativePath in treatmentOnly) {
    log(WARN) { "Treatment-only JSON file (no baseline match): ${File(treatmentDir, relativePath).absolutePath}" }
  }

  if (paired.isEmpty()) {
    log { "No matching JSON benchmark file pairs found" }
    return
  }

  log(INFO) { "To compare results, run:" }
  for (relativePath in paired.sorted()) {
    val baselineJson = File(baselineDir, relativePath)
    val treatmentJson = File(treatmentDir, relativePath)
    log(INFO) { "  $entrypointName compare ${baselineJson.absolutePath} ${treatmentJson.absolutePath}" }
  }
}

open class CompareCommand : CliktCommand(name = "compare") {
  override fun help(context: Context) = """
    Compare previously collected benchmark results.

    Takes two benchmark JSON files and performs statistical analysis to determine
    if there is a significant performance difference between them.
  """.trimIndent()

  private val file1: File by argument(help = "First benchmark JSON file (baseline)")
    .file(mustExist = true, canBeDir = false, mustBeReadable = true)

  private val file2: File by argument(help = "Second benchmark JSON file (treatment)")
    .file(mustExist = true, canBeDir = false, mustBeReadable = true)

  private val bootstrap: Boolean by option("-b", "--bootstrap", help = "Use bootstrapping for statistical analysis")
    .flag()

  override fun run() {
    val analysis1 = parseBenchmarkJson(file1)
    val analysis2 = parseBenchmarkJson(file2)
    val comparison = compare(analysis1, analysis2)
    printComparisonResults(comparison, bootstrap)
  }
}

open class PerfettoCommand(
  runnerOpts: RunnerOptions,
  private val benchCommandFactory: (RunnerOptions) -> BenchCommand,
) : CliktCommand(name = "perfetto") {
  override fun help(context: Context) = "Collect a raw Perfetto trace"

  protected val baseOpts by runnerOpts.base
  protected val runnerOpts by runnerOpts

  private val perfettoConfigFile by option(
    "--perfetto-config",
    help = "Path to a custom Perfetto config file (text proto format). If not specified, uses the default config."
  ).file(mustExist = true, canBeDir = false)

  override fun run() {
    baseOpts.setup()
    val configPath = perfettoConfigFile?.absolutePath
    val optsWithProfiler = object : RunnerValues by runnerOpts {
      override val profiler: String = "perfetto"
      override val perfettoConfigPath: String? = configPath
      override val iterations: Int? = runnerOpts.iterations ?: 1
      override val delegate: RunnerValues get() = runnerOpts
    }
    runBenchmark(baseOpts, optsWithProfiler)
  }

  open fun runBenchmark(baseVals: BaseValues, runnerVals: RunnerValues) {
    benchCommandFactory(runnerOpts).runBenchmark(baseVals, runnerVals)
  }
}

open class SimpleperfCommand(
  runnerOpts: RunnerOptions,
  private val benchCommandFactory: (RunnerOptions) -> BenchCommand,
) : CliktCommand(name = "simpleperf") {
  override fun help(context: Context) = "Run a benchmark with simpleperf profiling. Requires the instrumentation SDK."

  protected val baseOpts by runnerOpts.base
  protected val runnerOpts by runnerOpts

  private val callGraph by option(
    "--call-graph",
    help = "Enable call graph recording. Options: dwarf, fp (frame pointer). Mirrors simpleperf's --call-graph option."
  )

  override fun run() {
    baseOpts.setup()
    val callGraphValue = callGraph
    val optsWithProfiler = object : RunnerValues by runnerOpts {
      override val profiler: String = "simpleperf"
      override val simpleperfCallGraph: String? = callGraphValue
      override val iterations: Int? = runnerOpts.iterations ?: 1
      override val delegate: RunnerValues get() = runnerOpts
    }
    runBenchmark(baseOpts, optsWithProfiler)
  }

  open fun runBenchmark(baseVals: BaseValues, runnerVals: RunnerValues) {
    benchCommandFactory(runnerOpts).runBenchmark(baseVals, runnerVals)
  }
}

/**
 * @param runnerOptionsFactory Factory to create fresh RunnerOptions instances.
 *   A factory is needed because AbCommand creates multiple instances with different parsed args.
 *   The factory receives BaseConfig which includes francisRunDir and hostOutputDir.
 * @param benchCommandFactory Factory to create BenchCommand instances. Override to customize
 *   benchmark behavior (e.g., disable thermals before running).
 * @param simplePerfCommandFactory Factory to create SimpleperfCommand instances. Override to
 *   customize simpleperf behavior (e.g., disable SELinux during profiling). The factory receives
 *   RunnerOptions; use benchCommandFactory from the enclosing scope if needed.
 * @param compareCommandFactory Factory to create CompareCommand instances.
 * @param perfettoCommandFactory Factory to create PerfettoCommand instances.
 * @param viewCommandFactory Factory to create ViewCommand instances.
 */
fun runFrancis(
  rawArgs: Array<String>,
  name: String,
  help: String = "Francis - Android benchmark runner.",
  runnerOptionsFactory: (BaseConfig) -> RunnerOptions = { config -> RunnerOptions(config = config) },
  benchCommandFactory: (RunnerOptions) -> BenchCommand = { opts -> BenchCommand(opts) },
  simplePerfCommandFactory: (RunnerOptions) -> SimpleperfCommand = { opts -> SimpleperfCommand(opts, benchCommandFactory) },
  compareCommandFactory: () -> CompareCommand = { CompareCommand() },
  perfettoCommandFactory: (RunnerOptions) -> PerfettoCommand = { opts -> PerfettoCommand(opts, benchCommandFactory) },
  viewCommandFactory: (BaseOptions) -> ViewCommand = { opts -> ViewCommand(opts) },
) = pithyMain(rawArgs) {
  val baseConfig = BaseConfig()

  val abIdx = rawArgs.indexOf("ab")
  val abArgs = if (abIdx >= 0) {
    preprocessAbArgs(rawArgs.drop(abIdx + 1))
  } else {
    null
  }
  val cliktArgs = if (abArgs != null) {
    rawArgs.take(abIdx + 1) + abArgs.shared
  } else {
    rawArgs.toList()
  }

  val command = FrancisEntrypoint(name, help)
    .subcommands(
      benchCommandFactory(runnerOptionsFactory(baseConfig)),
      AbCommand(baseConfig, runnerOptionsFactory, benchCommandFactory, rawArgs.toList(), entrypointName = name),
      compareCommandFactory(),
      perfettoCommandFactory(runnerOptionsFactory(baseConfig)),
      simplePerfCommandFactory(runnerOptionsFactory(baseConfig)),
      viewCommandFactory(BaseOptions(baseConfig)),
    )
  try {
    command.parse(cliktArgs)
  } catch (e: UsageError) {
    command.echoFormattedHelp(e)
    System.err.println()
    System.err.println("For more information, try '--help'.")
    throw PithyException(e.statusCode, null)
  } catch (e: CliktError) {
    command.echoFormattedHelp(e)
    throw PithyException(e.statusCode, null)
  }
}
