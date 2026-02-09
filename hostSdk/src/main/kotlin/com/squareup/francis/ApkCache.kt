package com.squareup.francis

import logcat.LogPriority
import java.io.File

class ApkCache(
  private val cacheDir: File = File("/tmp/francis/apk-cache")
) {
  init {
    cacheDir.mkdirs()
  }

  fun getHostPath(packageName: String): String {
    val sha = getDeviceSha256(packageName)
      ?: throw PithyException(1, "Package $packageName is not installed on device")
    val cachedApk = File(cacheDir, "$sha.apk")

    if (!cachedApk.exists()) {
      val devicePath = getDevicePath(packageName)!!
      adb.cmdRun("pull", devicePath, cachedApk.absolutePath)
    }

    return cachedApk.absolutePath
  }

  fun getDevicePath(packageName: String): String? {
    return runCatching {
      adb.shellStdout("pm", "path", packageName) { logPriority = LogPriority.DEBUG }
        .removePrefix("package:")
        .trim()
    }.getOrNull()
  }

  fun getDeviceSha256(packageName: String): String? {
    val devicePath = getDevicePath(packageName) ?: return null
    val output = adb.shellStdout("sha256sum", devicePath) { logPriority = LogPriority.DEBUG }
    return output.split("\\s+".toRegex()).first()
  }

  companion object {
    private val default = ApkCache()

    fun getHostPath(packageName: String): String = default.getHostPath(packageName)
    fun getDevicePath(packageName: String): String? = default.getDevicePath(packageName)
    fun getDeviceSha256(packageName: String): String? = default.getDeviceSha256(packageName)
  }
}
