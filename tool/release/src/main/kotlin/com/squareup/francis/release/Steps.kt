package com.squareup.francis.release

import java.io.File
import kotlin.system.exitProcess

enum class Steps(val stepName: String) {
    PROMPT("prompt") {
        override fun run() {
            val releaseVersion = ctx.deriveReleaseVersion()
            val derivedPostReleaseVersion = ctx.derivePostReleaseVersion()
            val releaseBranch = "release/$releaseVersion"
            val releaseTag = "v$releaseVersion"

            if (ctx.activeDir.exists()) {
                error("""
                    |A release is already in progress (releases/active exists).
                    |
                    |To continue the existing release, run:
                    |  scripts/release.sh --run-all
                    |
                    |To abandon and start fresh, run:
                    |  rm -r releases/active
                    |
                    |You may also need to clean up git artifacts (if they were created):
                    |  git checkout main && git branch -D $releaseBranch && git push origin --delete $releaseBranch
                    |  git tag -d $releaseTag && git push origin --delete $releaseTag
                """.trimMargin())
            }

            println()
            println("═══════════════════════════════════════════════════════════════")
            println("                    Francis Release Process                     ")
            println("═══════════════════════════════════════════════════════════════")
            println()
            println("  Current version:      ${ctx.currentVersion}")
            println("  Release version:      $releaseVersion")
            println("  Post-release version: $derivedPostReleaseVersion")
            println()
            println("  This will:")
            println("    • Create release branch: $releaseBranch")
            println("    • Tag the release as: $releaseTag")
            println("    • Publish to Maven Central")
            println("    • Create GitHub release")
            println("    • Update Homebrew formula")
            println("    • Merge to main and bump version")
            println()
            println("To abandon this release later, run:")
            println("  rm -r releases/active")
            println("  git checkout main && git branch -D $releaseBranch && git push origin --delete $releaseBranch")
            println("  git tag -d $releaseTag && git push origin --delete $releaseTag")
            println()

            print("Do you want to proceed with this release? (yes/no): ")
            System.out.flush()
            val response = readLine()?.trim()?.lowercase()
            if (response != "yes") {
                println("Release cancelled.")
                exitProcess(1)
            }

            ctx.persistReleaseVersion()
        }
    },

    CREATE_BRANCH("create-branch") {
        override fun run() {
            ensureCleanGitRepo()
            val persistedReleaseVersion = requireNotNull(ctx.persistedReleaseVersion) {
                "No persisted release version found. Run the prompt step first."
            }

            val branch = ctx.currentBranch()
            require(branch == "main") { "Must run from main branch (currently on '$branch')" }
            require(ctx.currentVersion.endsWith("-SNAPSHOT")) {
                "Current version must end in -SNAPSHOT (was '${ctx.currentVersion}')"
            }
            require(persistedReleaseVersion == ctx.deriveReleaseVersion()) {
                "Persisted release version '$persistedReleaseVersion' does not match current version '${ctx.currentVersion}'"
            }

            println("Creating release branch: ${ctx.releaseBranch}")
            check(ctx.runCommand(listOf("git", "checkout", "-b", ctx.releaseBranch)))

            println("Updating version to $persistedReleaseVersion")
            ctx.writeGradlePropertiesVersion(persistedReleaseVersion)

            check(ctx.runCommand(listOf("git", "add", "gradle.properties")))
            check(ctx.runCommand(listOf("git", "commit", "-m", "Prepare $persistedReleaseVersion release")))
            check(ctx.runCommand(listOf("git", "push", "--set-upstream", "origin", ctx.releaseBranch)))
        }
    },

    TAG_RELEASE("tag-release") {
        override fun run() {
            val persistedReleaseVersion = requireNotNull(ctx.persistedReleaseVersion) {
                "No persisted release version found in releases/active/version"
            }
            println("Tagging release as ${ctx.releaseTag}")
            check(ctx.runCommand(listOf("git", "tag", "-a", ctx.releaseTag, "-m", "Release $persistedReleaseVersion")))
            check(ctx.runCommand(listOf("git", "push", "origin", ctx.releaseTag)))
        }
    },

    WAIT_RELEASE("wait-release") {
        override fun run() {
            val persistedReleaseVersion = requireNotNull(ctx.persistedReleaseVersion) {
                "No persisted release version found in releases/active/version"
            }
            println("Waiting for release workflow to complete...")

            waitForWorkflow("release", tag = ctx.releaseTag, commit = ctx.headSha())

            println()
            println("GitHub release and Maven Central artifacts published:")
            println("  GitHub Release:        https://github.com/block/francis/releases/tag/${ctx.releaseTag}")
            println("  Maven Central (host):  https://central.sonatype.com/artifact/com.squareup.francis/host-sdk/$persistedReleaseVersion")
            println("  Maven Central (inst):  https://central.sonatype.com/artifact/com.squareup.francis/instrumentation-sdk/$persistedReleaseVersion")
            println()
            println("Note: Maven Central artifacts may take up to 30 minutes to become available.")
        }
    },

    MERGE_MAIN("merge-main") {
        override fun run() {
            println("Merging ${ctx.releaseBranch} into main...")
            check(ctx.runCommand(listOf("git", "checkout", "main")))
            check(ctx.runCommand(listOf("git", "merge", "--ff-only", ctx.releaseBranch)))
        }
    },

    BUMP_SNAPSHOT("bump-snapshot") {
        override fun run() {
            println("Bumping version to ${ctx.persistedPostReleaseVersion}")
            ctx.writeGradlePropertiesVersion(ctx.persistedPostReleaseVersion)

            check(ctx.runCommand(listOf("git", "add", "gradle.properties")))
            check(ctx.runCommand(listOf("git", "commit", "-m", "Start ${ctx.persistedPostReleaseVersion} development")))
            check(ctx.runCommand(listOf("git", "push", "origin", "main")))
        }
    },

    TRIGGER_FORMULA_BUMP("trigger-formula-bump") {
        override fun run() {
            val persistedReleaseVersion = requireNotNull(ctx.persistedReleaseVersion) {
                "No persisted release version found in releases/active/version"
            }
            val releaseArtifactUrl = "https://github.com/block/francis/releases/download/${ctx.releaseTag}/francis-release.tar.gz"
            println("Triggering Homebrew tap update for ${ctx.releaseTag}...")
            check(ctx.runCommand(listOf(
                "gh", "workflow", "run", "bump-formula.yaml",
                "--repo", "block/homebrew-tap",
                "--field", "repo=block/francis",
                "--field", "formula=francis",
                "--field", "tag=${ctx.releaseTag}",
                "--field", "artifact_url=$releaseArtifactUrl"
            )))

            waitForWorkflowInRepo("bump-formula.yaml", "block/homebrew-tap", timeoutMinutes = 10)

            println()
            println("═══════════════════════════════════════════════════════════════")
            println("          Release $persistedReleaseVersion completed successfully!          ")
            println("═══════════════════════════════════════════════════════════════")
            println()
            println("All release artifacts:")
            println("  GitHub Release:        https://github.com/block/francis/releases/tag/${ctx.releaseTag}")
            println("  Maven Central (host):  https://central.sonatype.com/artifact/com.squareup.francis/host-sdk/$persistedReleaseVersion")
            println("  Maven Central (inst):  https://central.sonatype.com/artifact/com.squareup.francis/instrumentation-sdk/$persistedReleaseVersion")
            println("  Homebrew:              https://github.com/block/homebrew-tap/blob/main/Formula/francis.rb")
            println()
        }
    };

    abstract fun run()

    val markerFile: File by lazy {
        val prefix = "%02d".format(ordinal + 1)
        ctx.stepsDir.resolve("$prefix-$stepName")
    }

    fun execute() {
        // Check if this step has already been completed
        if (markerFile.exists()) {
            println("Skipping already completed step: $stepName")
            return
        }

        println("Running step: $stepName")
        run()

        // Mark this step as completed
        ctx.stepsDir.mkdirs()
        markerFile.writeText("done\n")
        println("✓ Step complete: $stepName")
    }

    companion object {
        fun findByName(name: String): Steps? = entries.find { it.stepName == name }
    }
}

// Helper functions

private fun ensureCleanGitRepo() {
    val status = ctx.runCommandOutput(listOf("git", "status", "--porcelain")).trim()
    if (status.isNotEmpty()) {
        System.err.println(status)
        error("Git repository has uncommitted changes")
    }
}

private fun waitForWorkflow(workflow: String, tag: String, commit: String, timeoutMinutes: Int = 30) {
    // Wait a moment for workflow to be registered
    Thread.sleep(5_000)

    // Find the run ID for this tag and commit
    var runId: String? = null
    repeat(10) {
        val result = ctx.runCommandOutput(listOf(
            "gh", "run", "list",
            "--workflow=$workflow.yaml",
            "--branch=$tag",
            "--commit=$commit",
            "--repo=block/francis",
            "--json", "databaseId",
            "--jq", ".[0].databaseId"
        )).trim()
        if (result.isNotEmpty() && result != "null") {
            runId = result
            return@repeat
        }
        println("  Waiting for $workflow workflow to start...")
        Thread.sleep(10_000)
    }

    requireNotNull(runId) { "Could not find workflow run for $workflow on tag $tag commit $commit" }

    // Use 'gh run watch' to stream status (avoids rate limiting from repeated API calls)
    println("Watching workflow run $runId...")
    val success = ctx.runCommand(listOf(
        "gh", "run", "watch", runId!!,
        "--repo=block/francis",
        "--exit-status"
    ))

    if (success) {
        println("✓ Workflow '$workflow' completed successfully!")
    } else {
        error("Workflow '$workflow' failed!")
    }
}

private fun waitForWorkflowInRepo(workflow: String, repo: String, timeoutMinutes: Int = 10) {
    Thread.sleep(5_000) // Give workflow time to start

    // Find the most recent run ID for this workflow
    var runId: String? = null
    repeat(10) {
        val result = ctx.runCommandOutput(listOf(
            "gh", "run", "list",
            "--workflow=$workflow",
            "--repo=$repo",
            "--json", "databaseId",
            "--jq", ".[0].databaseId"
        )).trim()
        if (result.isNotEmpty() && result != "null") {
            runId = result
            return@repeat
        }
        println("  Waiting for $workflow workflow to start in $repo...")
        Thread.sleep(10_000)
    }

    requireNotNull(runId) { "Could not find workflow run for $workflow in $repo" }

    // Use 'gh run watch' to stream status (avoids rate limiting from repeated API calls)
    println("Watching workflow run $runId in $repo...")
    val success = ctx.runCommand(listOf(
        "gh", "run", "watch", runId!!,
        "--repo=$repo",
        "--exit-status"
    ))

    if (success) {
        println("✓ Workflow '$workflow' in '$repo' completed successfully!")
    } else {
        error("Workflow '$workflow' in '$repo' failed!")
    }
}
