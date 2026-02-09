package com.squareup.francis

import java.io.File

/**
 * XDG Base Directory Specification paths for Francis.
 *
 * See: https://specifications.freedesktop.org/basedir-spec/latest/
 */
object Xdg {
  private val home: String get() = System.getProperty("user.home")

  private val dataHome: File
    get() = File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotEmpty() } ?: "$home/.local/share")

  private val configHome: File
    get() = File(System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotEmpty() } ?: "$home/.config")

  private val cacheHome: File
    get() = File(System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotEmpty() } ?: "$home/.cache")

  val francisData: File get() = File(dataHome, "francis")
  val francisConfig: File get() = File(configHome, "francis")
  val francisCache: File get() = File(cacheHome, "francis")
}
