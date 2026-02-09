plugins {
    id("com.android.test")
    kotlin("android")
}

android {
    namespace = "com.squareup.francis.demoinstrumentation"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // must point to the app under test
        targetProjectPath = ":demo-app"

        // Enable the benchmark to run separately from the app process
        experimentalProperties["android.experimental.self-instrumenting"] = true

        if (project.hasProperty("devMode")) {
            // Suppress errors related to emulator
            testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,DEBUGGABLE"

            // Skip on-device compilation
            testInstrumentationRunnerArguments["compilation.enabled"] = "false"
        }
    }

    buildTypes {
        create("benchmark") {
            initWith(getByName("debug"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testOptions {
        managedDevices {
            devices {
                maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api31").apply {
                    device = "Pixel 6"
                    apiLevel = 31
                    systemImageSource = "aosp"
                }
            }
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
    implementation(project(":instrumentationSdk"))
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("androidx.benchmark:benchmark-junit4:1.4.1")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}
