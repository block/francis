import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("com.vanniktech.maven.publish")
}

dependencies {
    implementation(libs.logcat)
    implementation(kotlin("stdlib"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

val francisVersion = project.findProperty("francis.version") as String

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    configure(KotlinJvm(javadocJar = JavadocJar.Empty()))

    coordinates("com.squareup.francis", "script", francisVersion)

    pom {
        name.set("Francis Script")
        description.set("Scripting and process execution primitives for Francis")
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

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}
