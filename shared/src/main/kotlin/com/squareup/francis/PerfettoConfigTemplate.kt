package com.squareup.francis

/**
 * Default Perfetto trace configuration template shared between host and instrumentation SDKs.
 *
 * Use [forPackage] or [forAllApps] to generate a config with the appropriate atrace_apps setting.
 */
object PerfettoConfigTemplate {
    private val DEFAULT_CONFIG_TEMPLATE = """
        buffers {
            size_kb: 65536
            fill_policy: RING_BUFFER
        }
        buffers {
            size_kb: 4096
            fill_policy: RING_BUFFER
        }
        data_sources {
            config {
                name: "linux.ftrace"
                target_buffer: 0
                ftrace_config {
                    ftrace_events: "sched/sched_switch"
                    ftrace_events: "sched/sched_wakeup"
                    ftrace_events: "sched/sched_waking"
                    ftrace_events: "sched/sched_blocked_reason"
                    ftrace_events: "power/cpu_frequency"
                    ftrace_events: "power/cpu_idle"
                    ftrace_events: "power/suspend_resume"
                    atrace_categories: "am"
                    atrace_categories: "dalvik"
                    atrace_categories: "gfx"
                    atrace_categories: "input"
                    atrace_categories: "pm"
                    atrace_categories: "res"
                    atrace_categories: "sched"
                    atrace_categories: "view"
                    atrace_categories: "wm"
                    {ATRACE_APPS}
                }
            }
        }
        data_sources {
            config {
                name: "linux.process_stats"
                target_buffer: 1
                process_stats_config {
                    scan_all_processes_on_start: true
                    proc_stats_poll_ms: 1000
                }
            }
        }
        data_sources {
            config {
                name: "android.packages_list"
                target_buffer: 1
            }
        }
        write_into_file: true
        file_write_period_ms: 2500
        flush_period_ms: 5000
    """.trimIndent()

    /** Generate config for a specific package. */
    fun forPackage(packageName: String): String {
        return DEFAULT_CONFIG_TEMPLATE.replace("{ATRACE_APPS}", "atrace_apps: \"$packageName\"")
    }

    /** Generate config for all apps (system-wide tracing). */
    fun forAllApps(): String {
        return DEFAULT_CONFIG_TEMPLATE.replace("{ATRACE_APPS}", "atrace_apps: \"*\"")
    }
}
