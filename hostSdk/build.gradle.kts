import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("com.vanniktech.maven.publish")
    kotlin("kapt")
}

repositories {
    mavenCentral()
}

val francisVersion = project.findProperty("francis.version") as String

val generateVersion = tasks.register("generateVersion") {
    val outputDir = layout.buildDirectory.dir("generated/source/version")
    val versionFile = outputDir.map { it.file("com/squareup/francis/Version.kt") }

    // Declare version as an input so Gradle re-runs this task when the version changes.
    // Without this, the task would be considered up-to-date as long as the output file exists.
    inputs.property("version", francisVersion)
    outputs.file(versionFile)

    doLast {
        versionFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("""
                package com.squareup.francis

                internal const val FRANCIS_VERSION = "$francisVersion"
            """.trimIndent())
        }
    }
}

sourceSets {
    main {
        java {
            srcDir(generateVersion.map { it.outputs.files.singleFile.parentFile.parentFile.parentFile })
        }
    }
}

dependencies {
    api(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.clikt)
    implementation(libs.datumbox)
    implementation(libs.logcat)
    implementation(kotlin("stdlib"))
    
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

tasks.withType<KotlinCompile> {
    dependsOn(generateVersion)
    kotlinOptions {
        jvmTarget = "17"
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    configure(KotlinJvm(javadocJar = JavadocJar.Empty()))

    coordinates("com.squareup.francis", "host-sdk", francisVersion)

    pom {
        name.set("Francis Host SDK")
        description.set("SDK for Francis Android performance testing")
        url.set("https://github.com/block/francis")
        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("squareup")
                name.set("Square, Inc.")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/block/francis.git")
            developerConnection.set("scm:git:ssh://github.com/block/francis.git")
            url.set("https://github.com/block/francis")
        }
    }
}

tasks.register("printPublishingInfo") {
    doLast {
        println("Publishing com.squareup.francis:host-sdk:$francisVersion")
    }
}

tasks.withType<Test> {
    useJUnit {
        if (project.hasProperty("excludeDeviceTests")) {
            excludeCategories("com.squareup.francis.DeviceRequired")
        }
    }
}
