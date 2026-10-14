plugins {
  id("com.android.test")
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "com.squareup.francis.demotrailblaze"
  compileSdk = 36

  defaultConfig {
    minSdk = 28
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    targetProjectPath = ":demo-app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
  }

  buildTypes {
    create("benchmark") {
      initWith(getByName("debug"))
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin { jvmToolchain(17) }

  packaging {
    resources.excludes +=
      setOf(
        "META-INF/INDEX.LIST",
        "META-INF/AL2.0",
        "META-INF/LGPL2.1",
        "META-INF/LICENSE.md",
        "META-INF/LICENSE-notice.md",
        "META-INF/io.netty.versions.properties",
      )
  }
}

dependencies {
  implementation(project(":instrumentationSdk"))
  implementation(libs.trailblaze.android)
  implementation(libs.koog.prompt.executor.clients)
  implementation("androidx.test.ext:junit:1.3.0")
  implementation("androidx.test:runner:1.7.0")
  implementation("androidx.test:rules:1.7.0")
  implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}
