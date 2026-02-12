package com.squareup.francis.release

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReleaseContextTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `releaseVersion uses in-progress release when gradle properties has been bumped`() {
        val francisDir = tempFolder.newFolder("francis")
        francisDir.resolve("gradle.properties").writeText("francis.version=0.0.15-SNAPSHOT\n")

        val releasesDir = francisDir.resolve("releases")
        val release014 = releasesDir.resolve("0.0.14/steps")
        release014.mkdirs()
        release014.resolve("01-prompt").writeText("done\n")

        val context = ReleaseContext(francisDir)

        assertThat(context.releaseVersion).isEqualTo("0.0.14")
        assertThat(context.artifactsDir.name).isEqualTo("0.0.14")
    }

    @Test
    fun `releaseVersion uses current version when no in-progress release exists`() {
        val francisDir = tempFolder.newFolder("francis")
        francisDir.resolve("gradle.properties").writeText("francis.version=0.0.14-SNAPSHOT\n")

        val context = ReleaseContext(francisDir)

        assertThat(context.releaseVersion).isEqualTo("0.0.14")
    }

    @Test
    fun `releaseVersion ignores completed releases`() {
        val francisDir = tempFolder.newFolder("francis")
        francisDir.resolve("gradle.properties").writeText("francis.version=0.0.15-SNAPSHOT\n")

        val releasesDir = francisDir.resolve("releases")
        val release014 = releasesDir.resolve("0.0.14/steps")
        release014.mkdirs()
        release014.resolve("07-trigger-formula-bump").writeText("done\n")

        val context = ReleaseContext(francisDir)

        assertThat(context.releaseVersion).isEqualTo("0.0.15")
    }

    @Test
    fun `persistReleaseVersion creates version file`() {
        val francisDir = tempFolder.newFolder("francis")
        francisDir.resolve("gradle.properties").writeText("francis.version=0.0.14-SNAPSHOT\n")

        val context = ReleaseContext(francisDir)
        context.persistReleaseVersion()

        val versionFile = francisDir.resolve("releases/0.0.14/version")
        assertThat(versionFile.exists()).isTrue()
        assertThat(versionFile.readText()).isEqualTo("0.0.14")
    }

    @Test
    fun `incrementSemver increments patch version`() {
        assertThat(ReleaseContext.incrementSemver("0.0.14")).isEqualTo("0.0.15-SNAPSHOT")
        assertThat(ReleaseContext.incrementSemver("1.2.3")).isEqualTo("1.2.4-SNAPSHOT")
        assertThat(ReleaseContext.incrementSemver("0.0.14-SNAPSHOT")).isEqualTo("0.0.15-SNAPSHOT")
    }
}
