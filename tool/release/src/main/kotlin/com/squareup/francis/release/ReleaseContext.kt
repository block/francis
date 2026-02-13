package com.squareup.francis.release

import java.io.File

class CommandFailedException(
    cmd: List<String>,
    exitCode: Int
) : RuntimeException("Command failed with exit code $exitCode: ${cmd.joinToString(" ")}")

lateinit var ctx: ReleaseContext

class ReleaseContext(val francisDir: File) {
    val gradleProperties: File = francisDir.resolve("gradle.properties")
    val releasesDir: File = francisDir.resolve("releases")
    val activeDir: File = releasesDir.resolve("active")

    val currentVersion: String by lazy { readVersion() }
    val releaseVersion: String by lazy { readActiveReleaseVersion() ?: currentVersion.removeSuffix("-SNAPSHOT") }
    val postReleaseVersion: String by lazy { incrementSemver(releaseVersion) }
    val releaseTag: String by lazy { "v$releaseVersion" }
    val releaseBranch: String by lazy { "release/$releaseVersion" }
    val artifactsDir: File get() = activeDir
    val stepsDir: File get() = activeDir.resolve("steps")
    private val versionFile: File get() = activeDir.resolve("version")

    private fun readActiveReleaseVersion(): String? {
        val file = activeDir.resolve("version")
        return if (file.exists()) file.readText().trim() else null
    }

    fun persistReleaseVersion() {
        activeDir.mkdirs()
        versionFile.writeText(releaseVersion)
    }

    fun finalizeRelease() {
        val finalDir = releasesDir.resolve(releaseVersion)
        require(!finalDir.exists()) { "Release directory already exists: $finalDir" }
        check(activeDir.renameTo(finalDir)) { "Failed to rename $activeDir to $finalDir" }
    }

    fun readVersion(): String {
        val line = gradleProperties.readLines().find { it.startsWith("francis.version=") }
            ?: error("Could not find francis.version in gradle.properties")
        return line.substringAfter("=").trim()
    }

    fun writeVersion(version: String) {
        val lines = gradleProperties.readLines().map { line ->
            if (line.startsWith("francis.version=")) "francis.version=$version" else line
        }
        gradleProperties.writeText(lines.joinToString("\n") + "\n")
    }

    fun headSha(): String = runCommandOutput(listOf("git", "rev-parse", "HEAD")).trim()

    fun currentBranch(): String = runCommandOutput(listOf("git", "rev-parse", "--abbrev-ref", "HEAD")).trim()

    fun runCommand(cmd: List<String>): Boolean {
        println("+ ${cmd.joinToString(" ")}")
        val pb = ProcessBuilder(cmd).directory(francisDir).inheritIO()
        val exitCode = pb.start().waitFor()
        if (exitCode != 0) {
            val error = CommandFailedException(cmd, exitCode)
            System.err.println(error.message)
            System.err.println("Stack trace:")
            error.stackTrace.forEach { System.err.println("  at $it") }
            return false
        }
        return true
    }

    fun runCommandOutput(cmd: List<String>): String {
        println("+ ${cmd.joinToString(" ")}")
        val pb = ProcessBuilder(cmd)
            .directory(francisDir)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
        val process = pb.start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val error = CommandFailedException(cmd, exitCode)
            System.err.println(error.message)
            System.err.println("Stack trace:")
            error.stackTrace.forEach { System.err.println("  at $it") }
        }
        return output
    }

    companion object {
        fun incrementSemver(version: String): String {
            val clean = version.removeSuffix("-SNAPSHOT")
            val parts = clean.split(".").map { it.toInt() }
            return "${parts[0]}.${parts[1]}.${parts[2] + 1}-SNAPSHOT"
        }
    }
}
