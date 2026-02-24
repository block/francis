package com.squareup.francis.release

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File
import kotlin.system.exitProcess

class ReleaseCommand : CliktCommand(name = "release") {
    private val step by option("--step", help = "Run a specific step")
    private val runAll by option("--run-all", help = "Run all steps").flag()
    private val list by option("--list", help = "List available steps").flag()

    override fun run() {
        if (list) {
            println("Available steps:")
            Steps.entries.forEach { println("  ${it.stepName}") }
            return
        }

        val francisDir = findFrancisDir()

        if (!francisDir.resolve("gradle.properties").exists()) {
            System.err.println("Error: Could not find gradle.properties in $francisDir")
            exitProcess(1)
        }

        when {
            runAll -> runAllSteps(francisDir)
            step != null -> runSingleStep(francisDir, step!!)
            else -> {
                System.err.println("Error: Must specify --step <name> or --run-all")
                System.err.println("Use --list to see available steps")
                exitProcess(1)
            }
        }
    }

    private fun runAllSteps(francisDir: File) {
        val context = ReleaseContext(francisDir)
        val releaseScript = francisDir.resolve("scripts/release.sh")
        
        for (step in Steps.entries) {
            println()
            println("────────────────────────────────────────────────────────────────")
            val cmd = listOf(releaseScript.absolutePath, "--step", step.stepName)
            println("$ ${cmd.joinToString(" ")}")
            println("────────────────────────────────────────────────────────────────")
            
            if (!context.runCommand(cmd)) {
                exitProcess(1)
            }
        }
    }

    private fun runSingleStep(francisDir: File, stepName: String) {
        val step = Steps.findByName(stepName)
        if (step == null) {
            System.err.println("Unknown step: $stepName")
            System.err.println("Available steps: ${Steps.entries.map { it.stepName }}")
            exitProcess(1)
        }

        ctx = ReleaseContext(francisDir)
        step.execute()

        if (step == Steps.entries.last()) {
            ctx.finalizeRelease()
        }
    }

    private fun findFrancisDir(): File {
        var dir = File(".").absoluteFile
        while (dir.parentFile != null) {
            if (dir.resolve("gradle.properties").exists() &&
                dir.resolve("tool/release").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        return File(".").absoluteFile
    }
}

fun main(args: Array<String>) = ReleaseCommand().main(args)
