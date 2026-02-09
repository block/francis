package com.squareup.francis

import com.squareup.francis.log.log
import com.squareup.francis.log.logFile
import com.squareup.francis.log.setRawArgs
import com.squareup.francis.log.stdErr
import logcat.LogPriority.ERROR
import logcat.LogcatLogger
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess

val isGraal: Boolean by lazy {
  System.getProperty("org.graalvm.nativeimage.imagecode") != null
}

val releaseDir: String by lazy {
  val pathToSelf = if (isGraal) {
    // GraalVM-generated binary
    ProcessHandle.current().info().command().get()
  } else {
    // This is how we find the release dir when run normally (via a jar)
    val uri = BaseOptions::class.java.protectionDomain.codeSource.location.toURI()
    File(uri).toPath().toAbsolutePath().toString()
  }

  Paths.get(pathToSelf).toRealPath().parent.parent.toAbsolutePath().toString()
}

fun pithyMain(rawArgs: Array<String>, block: () -> Unit) {
  setRawArgs(rawArgs)
  val exitCode = try {
    block()
    0
  } catch (e: PithyException) {
    // If we have a pithy message to display to the user, we'll display just that message
    // (unless debug logging is enabled) and then exit with status code 1.
    log { e.stackTraceToString() }

    val message = e.message
    if (message != null) {
      stdErr.println(message)
    } else {
      log { "(no pithyMsg)" }
    }
    logFile?.let {
      log(ERROR) { "Terminated with ${e.exitCode} - see ${it.canonicalPath} for full details"}
    }
    e.exitCode
  } catch (e: Exception) {
    // We don't have a pithy message
    if (!LogcatLogger.isInstalled) {
      System.err.println("Crashed prior to logging setup completion")
      System.err.println(e.stackTraceToString())
    } else {
      log(ERROR) { e.stackTraceToString() }
    }
    1
  }

  exitProcess(exitCode)
}
