buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.vanniktech.maven.publish.plugin)
    }
}

plugins {
    // Plugins need to be declared here to avoid warnings like:
    //   The Kotlin Gradle plugin was loaded multiple times in different
    //   subprojects, which is not supported and may break the build.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("com.android.application") version "8.5.0" apply false
    kotlin("android") version "1.9.23" apply false
}

repositories {
    google()
    mavenCentral()
}

tasks.register<Copy>("releaseArtifacts") {
    // Include benchmark-variant from demo-app/demo-instrumentation
    dependsOn(":demo-app:assembleBenchmark")
    dependsOn(":demo-instrumentation:assembleBenchmark")
    dependsOn(":main:jar")
    dependsOn(":demo:jar")
    dependsOn(":hostSdk:jar")

    val releaseDir = layout.buildDirectory.dir("release")
    into(releaseDir)

    // Copy JARs to jar/ subdirectory
    into("jar") {
        from(project(":main").tasks.named("jar")) {
            rename { "francis.jar" }
        }
        from(project(":demo").tasks.named("jar")) {
            rename { "francis-demo.jar" }
        }
        from(project(":hostSdk").tasks.named("jar")) {
            rename { "francis-host-sdk.jar" }
        }
    }

    // Copy SDK jar to sdk/ subdirectory for Maven publishing
    into("sdk") {
        from(project(":hostSdk").tasks.named("jar")) {
            rename { "francis-host-sdk.jar" }
        }
    }

    from(project(":demo-app").layout.buildDirectory.dir("outputs/apk/benchmark")) {
        include("*.apk")
        rename { _ -> "demo-app.apk" }
    }

    from(project(":demo-instrumentation").layout.buildDirectory.dir("outputs/apk/benchmark")) {
        include("*.apk")
        rename { _ -> "demo-instrumentation.apk" }
    }

    // Copy wrapper scripts from prebuilt/
    into("bin/jvm") {
        from("prebuilt/bin/jvm")
        fileMode = 0b111101101 // 0755 in octal
    }
}
