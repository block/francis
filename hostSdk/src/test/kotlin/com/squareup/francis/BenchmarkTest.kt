package com.squareup.francis

import com.google.common.truth.Truth.assertThat
import logcat.LogPriority
import com.squareup.francis.process.subproc

import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder

@Category(DeviceRequired::class)
class BenchmarkTest {
  companion object {
    @JvmField
    @ClassRule
    val tempFolder = TemporaryFolder()

    private lateinit var apkCache: ApkCache

    @JvmStatic
    @BeforeClass
    fun setUp() {
      apkCache = ApkCache(cacheDir = tempFolder.newFolder("apk-cache"))
    }
  }

  private val testPackage = "com.android.keychain"

  @Test
  fun apkCache_getHostPath_returnsValidPath() {
    val apkPath = apkCache.getHostPath(testPackage)

    assertThat(apkPath).endsWith(".apk")
  }

  @Test
  fun apkCache_deviceSha256MatchesLocalSha256() {
    val apkPath = apkCache.getHostPath(testPackage)
    val localSha256 = subproc.stdout("sha256sum", apkPath) { logPriority = LogPriority.DEBUG }
      .split("\\s+".toRegex())
      .first()

    val deviceSha256 = apkCache.getDeviceSha256(testPackage)
    assertThat(deviceSha256).isEqualTo(localSha256)
  }
}
