plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.application)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.clikt)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

application {
    mainClass.set("com.squareup.francis.release.MainKt")
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "com.squareup.francis.release.MainKt")
    }
    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
