package com.squareup.francis.release

import java.io.File

class CommandFailedException(cmd: List<String>, exitCode: Int) :
  RuntimeException("Command failed with exit code $exitCode: ${cmd.joinToString(" ")}")

lateinit var ctx: ReleaseContext

class ReleaseContext(val francisDir: File) {
  val gradleProperties: File = francisDir.resolve("gradle.properties")
  val releasesDir: File = francisDir.resolve("releases")
  val activeDir: File = releasesDir.resolve("active")
  private val versionFile: File
    get() = activeDir.resolve("version")

  val currentVersion: String by lazy { readGradlePropertiesVersion() }
  val persistedReleaseVersion: String? by lazy { readActiveReleaseVersion() }
  val persistedPostReleaseVersion: String by lazy {
    incrementSemver(
      requireNotNull(persistedReleaseVersion) {
        "No persisted release version found in $versionFile"
      }
    )
  }
  val releaseTag: String by lazy {
    "v${requireNotNull(persistedReleaseVersion) { "No persisted release version found in $versionFile" }}"
  }
  val releaseBranch: String by lazy {
    "release/${requireNotNull(persistedReleaseVersion) { "No persisted release version found in $versionFile" }}"
  }
  val artifactsDir: File
    get() = activeDir

  val stepsDir: File
    get() = activeDir.resolve("steps")

  fun deriveReleaseVersion(): String {
    require(currentVersion.endsWith("-SNAPSHOT")) {
      "Current version must end in -SNAPSHOT (was '$currentVersion')"
    }
    return currentVersion.removeSuffix("-SNAPSHOT")
  }

  fun derivePostReleaseVersion(): String = incrementSemver(deriveReleaseVersion())

  private fun readActiveReleaseVersion(): String? {
    return if (versionFile.exists()) versionFile.readText().trim() else null
  }

  fun persistReleaseVersion() {
    activeDir.mkdirs()
    versionFile.writeText(deriveReleaseVersion())
  }

  fun finalizeRelease() {
    val persistedReleaseVersion =
      requireNotNull(persistedReleaseVersion) {
        "No persisted release version found in $versionFile"
      }
    val finalDir = releasesDir.resolve(persistedReleaseVersion)
    require(!finalDir.exists()) { "Release directory already exists: $finalDir" }
    check(activeDir.renameTo(finalDir)) { "Failed to rename $activeDir to $finalDir" }
  }

  fun readGradlePropertiesVersion(): String {
    val line =
      gradleProperties.readLines().find { it.startsWith("francis.version=") }
        ?: error("Could not find francis.version in gradle.properties")
    return line.substringAfter("=").trim()
  }

  fun writeGradlePropertiesVersion(version: String) {
    val lines =
      gradleProperties.readLines().map { line ->
        if (line.startsWith("francis.version=")) "francis.version=$version" else line
      }
    gradleProperties.writeText(lines.joinToString("\n") + "\n")
  }

  fun headSha(): String = runCommandOutput(listOf("git", "rev-parse", "HEAD")).trim()

  fun currentBranch(): String =
    runCommandOutput(listOf("git", "rev-parse", "--abbrev-ref", "HEAD")).trim()

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
    val pb =
      ProcessBuilder(cmd).directory(francisDir).redirectError(ProcessBuilder.Redirect.INHERIT)
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
