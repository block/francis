rootProject.name = "francis"

pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
  }
}

include("demo")

include("hostSdk")

include("instrumentationSdk")

include("shared")

include("script")

project(":script").projectDir = file("script-module")

include("main")

include("demo-app")

include("demo-instrumentation")

include("demo-trailblaze-instrumentation")

// Allows testing the integration before Trailblaze publishes a complete Android dependency graph.
providers.gradleProperty("trailblazeCheckout").orNull?.let { includeBuild(it) }

include("tool:release")
