package com.squareup.francis

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProcessUtilsTest {
  @Test
  fun parsePackageNameFromBadging_returnsPackageName() {
    assertThat(parsePackageNameFromBadging(BADGING_OUTPUT)).isEqualTo("com.example.benchmark.test")
  }

  @Test
  fun parseTargetPackageFromBadging_returnsTargetPackage() {
    assertThat(parseTargetPackageFromBadging(BADGING_OUTPUT)).isEqualTo("com.example.app")
  }

  @Test
  fun resolveAppPackageOrNull_prefersExplicitAppApk() {
    val appPackage = resolveAppPackageOrNull(
      appApkOrNull = "/tmp/app.apk",
      instrumentationApkOrNull = "/tmp/test.apk",
      packageNameResolver = { "com.example.explicit" },
      targetPackageResolver = { "com.example.target" },
    )

    assertThat(appPackage).isEqualTo("com.example.explicit")
  }

  @Test
  fun resolveAppPackageOrNull_usesInstrumentationTargetPackageWhenAppMissing() {
    val appPackage = resolveAppPackageOrNull(
      appApkOrNull = null,
      instrumentationApkOrNull = "/tmp/test.apk",
      packageNameResolver = { error("packageNameResolver should not be called") },
      targetPackageResolver = { "com.example.target" },
    )

    assertThat(appPackage).isEqualTo("com.example.target")
  }

  @Test
  fun resolveAppPackageOrNull_returnsNullWhenNoAppOrInstrumentationProvided() {
    val appPackage = resolveAppPackageOrNull(
      appApkOrNull = null,
      instrumentationApkOrNull = null,
      packageNameResolver = { error("packageNameResolver should not be called") },
      targetPackageResolver = { error("targetPackageResolver should not be called") },
    )

    assertThat(appPackage).isNull()
  }

  companion object {
    private val BADGING_OUTPUT = """
      package: name='com.example.benchmark.test' versionCode='42' versionName='1.0'
      sdkVersion:'28'
      targetSdkVersion:'34'
      instrumentation: name='androidx.test.runner.AndroidJUnitRunner' targetPackage='com.example.app' functionalTest='false' handleProfiling='false'
      application: label='Example benchmark tests'
    """.trimIndent()
  }
}
