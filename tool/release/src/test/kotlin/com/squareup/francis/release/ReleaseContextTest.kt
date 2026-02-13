package com.squareup.francis.release

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReleaseContextTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `releaseVersion uses active release when version file exists`() {
        val francisDir = tempFolder.newFolder("francis")
        francisDir.resolve("gradle.properties").writeText("francis.version=0.0.15-SNAPSHOT\n")

        val activeDir = francisDir.resolve("releases/active")
        activeDir.mkdirs()
        activeDir.resolve("version").writeText("0.0.14")

        val context = ReleaseContext(francisDir)

        assertThat(context.releaseVersion).isEqualTo("0.0.14")
        assertThat(context.artifactsDir.name).isEqualTo("active")
    }

    @Test
    fun `releaseVersion uses current version when no in-progress release exists`() {
        val francisDir = tempFolder.newFolder("francis")
        francisDir.resolve("gradle.properties").writeText("francis.version=0.0.14-SNAPSHOT\n")

        val context = ReleaseContext(francisDir)

        assertThat(context.releaseVersion).isEqualTo("0.0.14")
    }

    @Test
    fun `releaseVersion ignores completed releases without active directory`() {
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
    fun `persistReleaseVersion creates version file in active directory`() {
        val francisDir = tempFolder.newFolder("francis")
        francisDir.resolve("gradle.properties").writeText("francis.version=0.0.14-SNAPSHOT\n")

        val context = ReleaseContext(francisDir)
        context.persistReleaseVersion()

        val versionFile = francisDir.resolve("releases/active/version")
        assertThat(versionFile.exists()).isTrue()
        assertThat(versionFile.readText()).isEqualTo("0.0.14")
    }

    @Test
    fun `finalizeRelease renames active directory to version`() {
        val francisDir = tempFolder.newFolder("francis")
        francisDir.resolve("gradle.properties").writeText("francis.version=0.0.14-SNAPSHOT\n")

        val context = ReleaseContext(francisDir)
        context.persistReleaseVersion()

        context.finalizeRelease()

        assertThat(francisDir.resolve("releases/active").exists()).isFalse()
        assertThat(francisDir.resolve("releases/0.0.14/version").exists()).isTrue()
        assertThat(francisDir.resolve("releases/0.0.14/version").readText()).isEqualTo("0.0.14")
    }

    @Test
    fun `incrementSemver increments patch version`() {
        assertThat(ReleaseContext.incrementSemver("0.0.14")).isEqualTo("0.0.15-SNAPSHOT")
        assertThat(ReleaseContext.incrementSemver("1.2.3")).isEqualTo("1.2.4-SNAPSHOT")
        assertThat(ReleaseContext.incrementSemver("0.0.14-SNAPSHOT")).isEqualTo("0.0.15-SNAPSHOT")
    }
}
