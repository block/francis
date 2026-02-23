import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.squareup.francis.instrumentationsdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    buildTypes {
        create("benchmark") {
            initWith(getByName("release"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}

val francisVersion = project.findProperty("francis.version") as String

tasks.register("printPublishingInfo") {
    doLast {
        println("Publishing com.squareup.francis:instrumentation-sdk:$francisVersion")
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    configure(AndroidSingleVariantLibrary())

    coordinates("com.squareup.francis", "instrumentation-sdk", francisVersion)

    pom {
        name.set("Francis Instrumentation SDK")
        description.set("SDK for Francis Android performance testing - instrumentation side")
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
