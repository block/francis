package com.squareup.francis

import com.datumbox.framework.common.dataobjects.FlatDataCollection
import com.datumbox.framework.core.statistics.nonparametrics.onesample.ShapiroWilk
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.DecimalFormat
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

private const val RED = "\u001b[31m"
private const val GREEN = "\u001b[32m"
private const val BLUE = "\u001b[34m"
private const val RESET = "\u001b[0m"

@Serializable
data class BenchmarksData(
  val benchmarks: List<BenchmarkResult>,
)

@Serializable
data class BenchmarkResult(
  val name: String,
  val className: String,
  val metrics: Map<String, MetricData>,
  val repeatIterations: Int,
) {
  val testName: String
    get() = "${className}#${name}"
}

@Serializable
data class MetricData(
  val runs: List<Double>,
)

class Metric(
  val runs: List<Double>,
  val originalSampleSize: Int = runs.size,
) {
  val mean: Double by lazy { runs.average() }
  val median: Double by lazy { runs.p(50) }
  val variance: Double by lazy { runs.variance() }
  val sampleSize: Int get() = runs.size
  val standardDeviation: Double by lazy { sqrt(variance) }
  val coefficientOfVariation: Double by lazy { standardDeviation / mean }

  val checkEnoughIterations: Boolean by lazy { originalSampleSize >= 30 }

  val checkLatenciesPassNormalityTest: Boolean by lazy {
    val alphaLevel = 0.05
    val rejectNullHypothesis = ShapiroWilk.test(FlatDataCollection(runs), alphaLevel)
    !rejectNullHypothesis
  }

  val checkCoefficientOfVariationLowEnough: Boolean by lazy { coefficientOfVariation <= 0.06 }

  private fun List<Double>.variance(): Double {
    val mean = average()
    return sumOf { (it - mean).pow(2) } / size
  }

  private fun List<Double>.p(percentile: Int): Double {
    val idealIndex = percentile.coerceIn(0, 100) / 100.0 * (size - 1)
    val firstIndex = idealIndex.toInt()
    val secondIndex = firstIndex + 1
    val firstValue = this[firstIndex]
    val secondValue = getOrElse(secondIndex) { firstValue }
    return lerp(firstValue, secondValue, idealIndex - firstIndex)
  }

  private fun lerp(a: Double, b: Double, ratio: Double): Double = a * (1 - ratio) + b * ratio
}

fun MetricData.toMetric(): Metric = Metric(runs)

fun bootstrapMetric(metric: Metric, iterations: Int, sampleSize: Int): Metric {
  val bootstrappedRuns = mutableListOf<Double>()
  repeat(iterations) {
    val resample = List(sampleSize) { metric.runs[Random.nextInt(metric.runs.size)] }
    bootstrappedRuns.add(resample.average())
  }
  return Metric(bootstrappedRuns, originalSampleSize = metric.originalSampleSize)
}

data class MetricComparison(
  val metric1: Metric,
  val metric2: Metric,
) {
  val varianceRatio: Double by lazy { metric2.variance / metric1.variance }
  val checkVarianceLessThanDouble: Boolean by lazy { varianceRatio in 0.5..2.0 }

  val allChecksPass: Boolean by lazy {
    metric1.checkEnoughIterations &&
      metric1.checkCoefficientOfVariationLowEnough &&
      metric1.checkLatenciesPassNormalityTest &&
      metric2.checkEnoughIterations &&
      metric2.checkCoefficientOfVariationLowEnough &&
      metric2.checkLatenciesPassNormalityTest &&
      checkVarianceLessThanDouble
  }

  val pooledEstimateOfStandardDeviation: Double by lazy {
    val sizeMinusOne1 = metric1.sampleSize - 1
    val sizeMinusOne2 = metric2.sampleSize - 1
    sqrt(
      (sizeMinusOne1 * metric1.variance + sizeMinusOne2 * metric2.variance) /
        (sizeMinusOne1 + sizeMinusOne2)
    )
  }

  val standardError: Double by lazy {
    pooledEstimateOfStandardDeviation * sqrt((1.0 / metric1.sampleSize) + (1.0 / metric2.sampleSize))
  }

  fun computeConfidenceInterval(zScore: Double) = ConfidenceInterval(zScore, this)
}

class ConfidenceInterval(
  val zScore: Double,
  val metrics: MetricComparison,
) {
  val errorMargin: Double by lazy { zScore * metrics.standardError }
  val range: Double by lazy { errorMargin * 2 }
  val meanDifference: Double by lazy { metrics.metric2.mean - metrics.metric1.mean }
  val meanDifferenceRange: ClosedFloatingPointRange<Double> by lazy {
    (meanDifference - errorMargin).rangeTo(meanDifference + errorMargin)
  }
  val meanDifferencePercentRange: ClosedFloatingPointRange<Double> by lazy {
    (meanDifferenceRange.start / metrics.metric1.mean).rangeTo(meanDifferenceRange.endInclusive / metrics.metric1.mean)
  }
}

data class PairedBenchmarkComparison(
  val benchmarkData1: BenchmarksData,
  val benchmarkData2: BenchmarksData,
  val metricComparisonsByTest: Map<String, Map<String, MetricComparison>>,
)

fun parseBenchmarkJson(file: File): BenchmarksData {
  val json = Json { ignoreUnknownKeys = true }
  return json.decodeFromString(file.readText())
}

fun compare(benchmarkData1: BenchmarksData, benchmarkData2: BenchmarksData): PairedBenchmarkComparison {
  val tests1 = benchmarkData1.benchmarks.associateBy { it.testName }
  val tests2 = benchmarkData2.benchmarks.associateBy { it.testName }
  check(tests1.keys == tests2.keys) {
    "Expected exact same set of tests between ${tests1.keys} and ${tests2.keys}"
  }
  val testsWithPairedData = tests1.mapValues { (testName, benchmark1) ->
    val benchmark2 = tests2.getValue(testName)
    check(benchmark1.metrics.keys == benchmark2.metrics.keys) {
      "Expected exact same set of metrics for $testName between ${benchmark1.metrics.keys} and ${benchmark2.metrics.keys}"
    }
    benchmark1.metrics.mapValues { (metricName, metric1) ->
      val metric2 = benchmark2.metrics.getValue(metricName)
      MetricComparison(metric1.toMetric(), metric2.toMetric())
    }
  }
  return PairedBenchmarkComparison(benchmarkData1, benchmarkData2, testsWithPairedData)
}

fun printComparisonResults(comparison: PairedBenchmarkComparison, bootstrap: Boolean) {
  for ((testName, metrics) in comparison.metricComparisonsByTest.entries) {
    println("###########################################################################")
    println("Results for $testName")
    for ((metricName, metricComparison) in metrics.entries) {
      println("##################################################")
      println(metricName)
      println("#########################")
      println("DATA CHECKS")
      if (metricComparison.allChecksPass) {
        println("$GREEN✓ All checks passed, the comparison conclusion is meaningful.$RESET\n")
        if (bootstrap) {
          println("\nThe data checks passed, you don't need to pass the $RED--bootstrap$RESET flag.")
        }
      } else {
        println("$RED˟ Some checks did not pass, the comparison conclusion is NOT meaningful.$RESET\n")

        if (!metricComparison.metric1.checkLatenciesPassNormalityTest ||
          !metricComparison.metric2.checkLatenciesPassNormalityTest
        ) {
          println(
            """
            The distribution of latencies did not pass the Shapiro-Wilk normality test. Open the
            corresponding HTML report to learn more.
            """.trimIndent()
          )
        }

        if (!bootstrap) {
          println("\nYou could get the checks to pass by generating a normal distribution based on the real distribution with the $GREEN--bootstrap$RESET flag.\n")
        }

      }

      printMetricResults(metricComparison.metric1, "Benchmark 1")
      printMetricResults(metricComparison.metric2, "Benchmark 2")
      printResult(metricComparison)

      if (!bootstrap) continue

      println("#########################")
      println("\nComparing Benchmarks with Bootstrapping\n")
      println("#########################")

      val bootstrappedMetric1 = bootstrapMetric(metricComparison.metric1, 200, 25)
      val bootstrappedMetric2 = bootstrapMetric(metricComparison.metric2, 200, 25)
      val bootstrappedComparison = MetricComparison(bootstrappedMetric1, bootstrappedMetric2)

      if (bootstrappedComparison.allChecksPass) {
        println("$GREEN✓ All checks passed, the comparison conclusion is meaningful.$RESET\n")
      } else {
        println("$RED˟ Some checks did not pass, the comparison conclusion is NOT meaningful.$RESET\n")
        println("\n**NOTE: Each bootstrap iteration is a resample of the original data with replacement.")
        println("        If bootstrapped data checks fail, re-run the script to see if the results are consistent.\n")
      }
      printMetricResults(bootstrappedMetric1, "Benchmark 1")
      printMetricResults(bootstrappedMetric2, "Benchmark 2")
      println("#########################")
      printResult(bootstrappedComparison)
    }
  }
}

private fun printMetricResults(metric: Metric, metricName: String) {
  val twoDecimals = DecimalFormat("#.##")
  println(
    """
    Data checks for $metricName
    - ${metric.checkEnoughIterations.check()} is >30 iterations (${metric.originalSampleSize} iterations)
    - ${metric.checkCoefficientOfVariationLowEnough.check()} CV (${twoDecimals.format(metric.coefficientOfVariation * 100)}) <= 6%
    - ${metric.checkLatenciesPassNormalityTest.check()} Latencies pass normality test
    - ${metric.standardDeviation} Standard deviation
    #########################
    """.trimIndent()
  )
}

private fun printResult(comparison: MetricComparison) {
  val zScore = 1.96
  val confidenceInterval = comparison.computeConfidenceInterval(zScore)

  val meanDifferenceRange = confidenceInterval.meanDifferenceRange
  val meanDifferencePercentRange = confidenceInterval.meanDifferencePercentRange

  val twoDecimals = DecimalFormat("#.##")

  println("RESULT")
  println("Mean difference confidence interval at 95% confidence level:")
  when {
    0.0 in meanDifferenceRange -> {
      println(
        BLUE +
          "The change yielded no statistical significance (the mean difference confidence interval crosses 0): " +
          "from " +
          "${meanDifferenceRange.start.roundToInt()} ms (${twoDecimals.format(meanDifferencePercentRange.start * 100)}%) " +
          "to " +
          "${meanDifferenceRange.endInclusive.roundToInt()} ms (${twoDecimals.format(meanDifferencePercentRange.endInclusive * 100)}%)." +
          RESET
      )
    }

    meanDifferenceRange.endInclusive < 0.0 -> {
      println(
        GREEN +
          "The change yielded a mean improvement of " +
          "${meanDifferenceRange.endInclusive.roundToInt()} ms (${twoDecimals.format(meanDifferencePercentRange.endInclusive * 100)}%) " +
          "to " +
          "${meanDifferenceRange.start.roundToInt()} ms (${twoDecimals.format(meanDifferencePercentRange.start * 100)}%)." +
          RESET
      )
    }

    else -> {
      println(
        RED +
          "The change yielded a mean regression of " +
          "${meanDifferenceRange.start.roundToInt()} ms (${twoDecimals.format(meanDifferencePercentRange.start * 100)}%) " +
          "to " +
          "${meanDifferenceRange.endInclusive.roundToInt()} ms (${twoDecimals.format(meanDifferencePercentRange.endInclusive * 100)}%)." +
          RESET
      )
    }
  }
  println("#########################")
  println("MEDIANS")
  println("The median went from ${comparison.metric1.median.roundToInt()} ms to ${comparison.metric2.median.roundToInt()} ms.")
  println("DO NOT REPORT THE DIFFERENCE IN MEDIANS.")
  println("This data helps contextualize results but is not statistically meaningful.")
  println("#########################")
}

private fun Boolean.check() = if (this) "$GREEN✓$RESET" else "$RED˟$RESET"
