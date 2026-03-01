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
include("tool:release")
