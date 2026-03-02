package com.squareup.francis

import com.squareup.francis.script.logging.nextAppRunDir
import com.squareup.francis.script.xdg.Xdg
import java.io.File

object FrancisXdg {
  const val APP_NAME = "francis"

  val francisData: File get() = File(Xdg.dataHome, APP_NAME)
  val francisConfig: File get() = File(Xdg.configHome, APP_NAME)
  val francisCache: File get() = File(Xdg.cacheHome, APP_NAME)
}

fun nextFrancisRunDir(): File = nextAppRunDir(FrancisXdg.APP_NAME)
