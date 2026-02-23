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
include("main")
include("demo-app")
include("demo-instrumentation")
include("tool:release")
