package com.squareup.francis.release

import java.io.File
import kotlin.system.exitProcess

enum class Steps(val stepName: String) {
    PROMPT("prompt") {
        override fun run() {
            println()
            println("═══════════════════════════════════════════════════════════════")
            println("                    Francis Release Process                     ")
            println("═══════════════════════════════════════════════════════════════")
            println()
            println("  Current version:      ${ctx.currentVersion}")
            println("  Release version:      ${ctx.releaseVersion}")
            println("  Post-release version: ${ctx.postReleaseVersion}")
            println()
            println("  This will:")
            println("    • Create release branch: ${ctx.releaseBranch}")
            println("    • Tag the release as: ${ctx.releaseTag}")
            println("    • Publish to Maven Central")
            println("    • Create GitHub release")
            println("    • Update Homebrew formula")
            println("    • Merge to main and bump version")
            println()
            println("To abandon this release later, run:")
            println("  rm -r releases/${ctx.releaseVersion}")
            println("  git checkout main && git branch -D ${ctx.releaseBranch} && git push origin --delete ${ctx.releaseBranch}")
            println("  git tag -d ${ctx.releaseTag} && git push origin --delete ${ctx.releaseTag}")
            println()

            print("Do you want to proceed with this release? (yes/no): ")
            System.out.flush()
            val response = readLine()?.trim()?.lowercase()
            if (response != "yes") {
                println("Release cancelled.")
                exitProcess(0)
            }

            ctx.artifactsDir.mkdirs()
        }
    },

    CREATE_BRANCH("create-branch") {
        override fun run() {
            ensureCleanGitRepo()

            val branch = ctx.currentBranch()
            require(branch == "main") { "Must run from main branch (currently on '$branch')" }
            require(ctx.currentVersion.endsWith("-SNAPSHOT")) {
                "Current version must end in -SNAPSHOT (was '${ctx.currentVersion}')"
            }

            println("Creating release branch: ${ctx.releaseBranch}")
            check(ctx.runCommand(listOf("git", "checkout", "-b", ctx.releaseBranch)))

            println("Updating version to ${ctx.releaseVersion}")
            ctx.writeVersion(ctx.releaseVersion)

            check(ctx.runCommand(listOf("git", "add", "gradle.properties")))
            check(ctx.runCommand(listOf("git", "commit", "-m", "Prepare ${ctx.releaseVersion} release")))
            check(ctx.runCommand(listOf("git", "push", "--set-upstream", "origin", ctx.releaseBranch)))
        }
    },

    TAG_RELEASE("tag-release") {
        override fun run() {
            println("Tagging release as ${ctx.releaseTag}")
            check(ctx.runCommand(listOf("git", "tag", "-a", ctx.releaseTag, "-m", "Release ${ctx.releaseVersion}")))
            check(ctx.runCommand(listOf("git", "push", "origin", ctx.releaseTag)))
        }
    },

    WAIT_RELEASE("wait-release") {
        override fun run() {
            println("Waiting for release workflow to complete...")

            waitForWorkflow("release", tag = ctx.releaseTag, commit = ctx.headSha())

            println()
            println("GitHub release and Maven Central artifacts published:")
            println("  GitHub Release:        https://github.com/block/francis/releases/tag/${ctx.releaseTag}")
            println("  Maven Central (host):  https://central.sonatype.com/artifact/com.squareup.francis/host-sdk/${ctx.releaseVersion}")
            println("  Maven Central (inst):  https://central.sonatype.com/artifact/com.squareup.francis/instrumentation-sdk/${ctx.releaseVersion}")
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
            println("Bumping version to ${ctx.postReleaseVersion}")
            ctx.writeVersion(ctx.postReleaseVersion)

            check(ctx.runCommand(listOf("git", "add", "gradle.properties")))
            check(ctx.runCommand(listOf("git", "commit", "-m", "Start ${ctx.postReleaseVersion} development")))
            check(ctx.runCommand(listOf("git", "push", "origin", "main")))
        }
    },

    TRIGGER_FORMULA_BUMP("trigger-formula-bump") {
        override fun run() {
            println("Triggering Homebrew tap update for ${ctx.releaseTag}...")
            check(ctx.runCommand(listOf(
                "gh", "workflow", "run", "update-francis.yaml",
                "--repo", "block/homebrew-tap",
                "--field", "tag=${ctx.releaseTag}"
            )))

            waitForWorkflowInRepo("update-francis.yaml", "block/homebrew-tap", timeoutMinutes = 10)

            println()
            println("═══════════════════════════════════════════════════════════════")
            println("          Release ${ctx.releaseVersion} completed successfully!          ")
            println("═══════════════════════════════════════════════════════════════")
            println()
            println("All release artifacts:")
            println("  GitHub Release:        https://github.com/block/francis/releases/tag/${ctx.releaseTag}")
            println("  Maven Central (host):  https://central.sonatype.com/artifact/com.squareup.francis/host-sdk/${ctx.releaseVersion}")
            println("  Maven Central (inst):  https://central.sonatype.com/artifact/com.squareup.francis/instrumentation-sdk/${ctx.releaseVersion}")
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


