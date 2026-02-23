package com.squareup.francis

/**
 * Utility for determining simpleperf event arguments.
 *
 * Some devices don't support hardware cpu-cycles events (e.g., emulators),
 * so we fall back to cpu-clock.
 */
object SimpleperfUtils {
    /**
     * Check if the output of `simpleperf list hw` indicates cpu-cycles support.
     */
    fun supportsCpuCycles(simpleperfListOutput: String): Boolean {
        return simpleperfListOutput.lines().any { it.trim() == "cpu-cycles" }
    }

    /**
     * Build the simpleperf record command as a list of arguments.
     *
     * @param outputPath Path to write the perf.data file
     * @param supportsCpuCycles Whether the device supports cpu-cycles events (use [supportsCpuCycles] to check)
     * @param callGraph Call graph mode (e.g., "fp", "dwarf"), or null to disable
     * @param targetPackage Package name to profile, or null for system-wide (-a)
     * @param useRoot Whether to prefix the command with `su 0` for root access (required for system-wide profiling)
     * @return List of command arguments starting with "simpleperf" (or "su 0 simpleperf" if useRoot is true)
     */
    fun buildRecordCommand(
        outputPath: String,
        supportsCpuCycles: Boolean,
        callGraph: String? = null,
        targetPackage: String? = null,
        useRoot: Boolean = false,
    ): List<String> = buildList {
        if (useRoot) {
            add("su")
            add("0")
        }
        add("simpleperf")
        add("record")

        if (callGraph != null) {
            add("--call-graph")
            add(callGraph)
        }

        if (!supportsCpuCycles) {
            add("-e")
            add("cpu-clock")
        }

        if (targetPackage != null) {
            add("--app")
            add(targetPackage)
        } else {
            add("-a")
        }

        add("-o")
        add(outputPath)
    }
}
