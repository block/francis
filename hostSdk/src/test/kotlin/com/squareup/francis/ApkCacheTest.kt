package com.squareup.francis

import com.google.common.truth.Truth.assertThat
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder
import java.io.File

@Category(DeviceRequired::class)
class ApkCacheTest {
  companion object {
    @JvmField
    @ClassRule
    val tempFolder = TemporaryFolder()

    private lateinit var cache: ApkCache

    @JvmStatic
    @BeforeClass
    fun setUp() {
      cache = ApkCache(cacheDir = tempFolder.newFolder("apk-cache"))
    }
  }

  private val testPackage = "com.android.keychain"

  @Test
  fun getDevicePath_returnsApkPath() {
    val path = cache.getDevicePath(testPackage)

    assertThat(path).endsWith(".apk")
    assertThat(path).ignoringCase().contains("keychain")
  }

  @Test
  fun getDeviceSha256_returns64CharHex() {
    val sha = cache.getDeviceSha256(testPackage)

    assertThat(sha).hasLength(64)
    assertThat(sha).matches("[a-f0-9]+")
  }

  @Test
  fun getHostPath_pullsApkToCache() {
    val path = cache.getHostPath(testPackage)

    assertThat(File(path).exists()).isTrue()
    assertThat(path).endsWith(".apk")
  }

  @Test
  fun getHostPath_usesCachedFile_onSecondCall() {
    val path1 = cache.getHostPath(testPackage)
    val file1ModifiedTime = File(path1).lastModified()

    Thread.sleep(10)

    val path2 = cache.getHostPath(testPackage)

    assertThat(path2).isEqualTo(path1)
    assertThat(File(path2).lastModified()).isEqualTo(file1ModifiedTime)
  }
}
