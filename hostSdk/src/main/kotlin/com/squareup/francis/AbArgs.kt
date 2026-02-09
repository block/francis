package com.squareup.francis

data class AbArgs(
  val shared: List<String>,
  val baselineOnly: List<String>,
  val treatmentOnly: List<String>,
) {
  fun baselineArgs(): List<String> = shared + baselineOnly
  fun treatmentArgs(): List<String> = shared + treatmentOnly
}

fun preprocessAbArgs(args: List<String>): AbArgs {
  val baselineMarkers = listOf("--baseline-options", "--baseline-opts", "--baseline")
  val treatmentMarkers = listOf("--treatment-options", "--treatment-opts", "--treatment")

  val baselineMatches = args.filter { it in baselineMarkers }
  val treatmentMatches = args.filter { it in treatmentMarkers }

  if (baselineMatches.size > 1) {
    error("Cannot specify baseline options more than once: ${baselineMatches.joinToString(", ")}")
  }
  if (treatmentMatches.size > 1) {
    error("Cannot specify treatment options more than once: ${treatmentMatches.joinToString(", ")}")
  }

  val baselineIdx = args.indexOfFirst { it in baselineMarkers }
  val treatmentIdx = args.indexOfFirst { it in treatmentMarkers }

  return when {
    baselineIdx == -1 && treatmentIdx == -1 -> {
      AbArgs(shared = args, baselineOnly = emptyList(), treatmentOnly = emptyList())
    }
    baselineIdx == -1 -> {
      val shared = args.subList(0, treatmentIdx)
      val treatmentOnly = args.subList(treatmentIdx + 1, args.size)
      AbArgs(shared = shared, baselineOnly = emptyList(), treatmentOnly = treatmentOnly)
    }
    treatmentIdx == -1 -> {
      val shared = args.subList(0, baselineIdx)
      val baselineOnly = args.subList(baselineIdx + 1, args.size)
      AbArgs(shared = shared, baselineOnly = baselineOnly, treatmentOnly = emptyList())
    }
    baselineIdx < treatmentIdx -> {
      val shared = args.subList(0, baselineIdx)
      val baselineOnly = args.subList(baselineIdx + 1, treatmentIdx)
      val treatmentOnly = args.subList(treatmentIdx + 1, args.size)
      AbArgs(shared = shared, baselineOnly = baselineOnly, treatmentOnly = treatmentOnly)
    }
    else -> {
      val shared = args.subList(0, treatmentIdx)
      val treatmentOnly = args.subList(treatmentIdx + 1, baselineIdx)
      val baselineOnly = args.subList(baselineIdx + 1, args.size)
      AbArgs(shared = shared, baselineOnly = baselineOnly, treatmentOnly = treatmentOnly)
    }
  }
}
