import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("com.vanniktech.maven.publish")
}

dependencies {
    implementation(kotlin("stdlib"))
}

val francisVersion = project.findProperty("francis.version") as String

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    configure(KotlinJvm(javadocJar = JavadocJar.Empty()))

    coordinates("com.squareup.francis", "shared", francisVersion)

    pom {
        name.set("Francis Shared")
        description.set("Shared utilities for Francis Android performance testing")
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
        println("Publishing com.squareup.francis:shared:$francisVersion")
    }
}
