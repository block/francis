package com.squareup.francis.script.logging

import com.squareup.francis.script.xdg.Xdg
import java.io.File

fun nextNumberedRunDir(runsDir: File): File {
  runsDir.mkdirs()
  val nextNum = (runsDir.list()?.mapNotNull { it.toIntOrNull() }?.maxOrNull() ?: -1) + 1
  val runDir = File(runsDir, "%06d".format(nextNum))
  runDir.mkdirs()
  return runDir
}

fun nextAppRunDir(appName: String): File = nextNumberedRunDir(File(Xdg.stateHome, "$appName/runs"))
