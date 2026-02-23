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
}
