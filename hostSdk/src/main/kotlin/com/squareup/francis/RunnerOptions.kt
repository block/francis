package com.squareup.francis

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.nullableFlag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.squareup.francis.logging.log

interface RunnerValues {
  val base: BaseValues
  val appApkOrNull: String?
  val appApk: String get() = appApkOrNull ?: throw PithyException(1, "App APK path not provided. Use --app to specify.")
  val instrumentationApkOrNull: String?
  val instrumentationApk: String get() = instrumentationApkOrNull ?: throw PithyException(1, "Instrumentation APK path not provided. Use --instrumentation to specify.")
  val testSymbolOrNull: String?
  val testSymbol: String get() = testSymbolOrNull ?: throw PithyException(1, "Test symbol not provided. Use --symbol to specify.")
  val runnerClass: String
  val suppressErrors: Boolean
  val aot: Boolean
  val dryRun: Boolean
  val instrumentationArgs: Map<String, String>
  val hostOutputDir: String
  val iterations: Int?
  val profiler: String?
  val simpleperfCallGraph: String?
  val perfettoConfigPath: String?

  /** Returns the underlying delegate if this is a wrapper, or this if not. */
  val delegate: RunnerValues get() = this
}

open class RunnerOptions(
  val config: BaseConfig = BaseConfig(),
  override val base: BaseOptions = BaseOptions(config),
): OptionGroup(), RunnerValues {
  override val hostOutputDir: String get() = config.hostOutputDir
  private val appApkOption by option("-A", "--app", help = "Path to the APK to instrument, or package name if not ending in .apk/.aab.")
  override val appApkOrNull: String? by lazy {
    val app = appApkOption ?: return@lazy null
    if (app.endsWith(".apk") || app.endsWith(".aab")) {
      log { "Interpreting --app as APK path: $app" }
      app
    } else {
      log { "Interpreting --app as package name: $app" }
      ApkCache.getHostPath(app)
    }
  }

  private val instrumentationApkOption by option("-I", "--inst", "--instrumentation", help = "Path to the APK to use as instrumentation, or package name if not ending in .apk/.aab.")
  override val instrumentationApkOrNull: String? by lazy {
    val instrumentation = instrumentationApkOption ?: return@lazy null
    if (instrumentation.endsWith(".apk") || instrumentation.endsWith(".aab")) {
      log { "Interpreting --instrumentation as APK path: $instrumentation" }
      instrumentation
    } else {
      log { "Interpreting --instrumentation as package name: $instrumentation" }
      ApkCache.getHostPath(instrumentation)
    }
  }

  private val testSymbolOption by option("-s", "--symbol", help = "Instrumentation class/method to run (e.g., com.example.MyTest or com.example.MyTest#testMethod).")
  override val testSymbolOrNull: String? by lazy { testSymbolOption }

  private val runnerClassOption by option("--runner-class", help = "Fully qualified name of the test runner class.")
  override val runnerClass: String by lazy {
    runnerClassOption ?: "androidx.test.runner.AndroidJUnitRunner"
  }

  private val suppressErrorsOption by option("--suppress-errors", help = "Suppress errors when running tests.")
    .nullableFlag("--no-suppress-errors")
  override val suppressErrors: Boolean by lazy { suppressErrorsOption ?: base.devMode }

  private val aotOption by option(
    "--aot",
    help = """
      Enable benchmark-controlled compilation (reinstall and compile per CompilationMode).
      Use --no-aot to skip compilation and benchmark the app as-is.
      See https://developer.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args#compilation-enabled
    """.trimIndent()
  ).nullableFlag("--no-aot")
  override val aot: Boolean by lazy { aotOption ?: !base.devMode }

  override val dryRun by option(
    "--dry-run",
    help = "Verify instrumentation works correctly without collecting performance data. Runs a single iteration with tracing and compilation disabled."
  ).flag()

  private val instrumentationArgsOption by option("--inst-arg", "--instrumentation-arg", help = "Arguments to pass to the instrumentation.")
    .multiple()
  override val instrumentationArgs: Map<String, String> by lazy {
    instrumentationArgsOption.associate {
      val (key, value) = it.split("=", limit = 2)
      key to value
    }
  }

  private val iterationsOption by option("--iterations", "-n", help = "Number of iterations to run for each benchmark. Requires the instrumentation SDK.")
    .int()
  override val iterations: Int? by lazy { iterationsOption }

  override val profiler: String? = null
  override val simpleperfCallGraph: String? = null
  override val perfettoConfigPath: String? = null
}
