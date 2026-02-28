package com.squareup.francis.process

import com.squareup.francis.logging.log
import logcat.LogPriority
import logcat.LogPriority.DEBUG
import logcat.LogPriority.WARN
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val teePumpExecutor = Executors.newCachedThreadPool { r ->
  Thread(r, "TeeProcess-pump").apply { isDaemon = true }
}

sealed class OutputTarget {
  object Pipe : OutputTarget()
  object Inherit : OutputTarget()
  data class ToFile(val file: File, val append: Boolean = false) : OutputTarget()
  data class ToStream(val stream: OutputStream, val autoClose: Boolean = true) : OutputTarget()
}

interface PidAware {
  fun setPid(pid: Long)
}

data class OutputRedirectSpec(val targets: List<OutputTarget>) {
  operator fun plus(other: OutputRedirectSpec): OutputRedirectSpec {
    val merged = targets.toMutableList()
    for (target in other.targets) {
      val isDuplicate = when (target) {
        is OutputTarget.Pipe -> merged.any { it is OutputTarget.Pipe }
        is OutputTarget.Inherit -> merged.any { it is OutputTarget.Inherit }
        else -> false
      }
      if (!isDuplicate) merged.add(target)
    }
    return OutputRedirectSpec(merged)
  }

  companion object {
    val DISCARD = OutputRedirectSpec(emptyList())
    val PIPE = OutputRedirectSpec(listOf(OutputTarget.Pipe))
    val INHERIT = OutputRedirectSpec(listOf(OutputTarget.Inherit))
  }
}

sealed class InputSource {
  object Pipe : InputSource()
  object Inherit : InputSource()
  object Null : InputSource()
  data class FromFile(val file: File) : InputSource()
  data class FromStream(val stream: InputStream, val autoClose: Boolean = true) : InputSource()
}

data class InputRedirectSpec(
  val source: InputSource,
  val teeOutputs: List<OutputTarget.ToStream> = emptyList()
) {
  companion object {
    val NULL = InputRedirectSpec(InputSource.Null)
    val PIPE = InputRedirectSpec(InputSource.Pipe)
    val INHERIT = InputRedirectSpec(InputSource.Inherit)
  }
}

class TeeProcess(
  private val delegate: Process,
  private val stdoutPipe: InputStream?,
  private val stderrPipe: InputStream?,
  private val stdinWrapper: OutputStream?,
  private val command: List<String>,
  private val pumpFutures: List<CompletableFuture<Void>> = emptyList(),
  private val logPriority: LogPriority? = null
) {
  val stdinStream: OutputStream get() = stdinWrapper ?: delegate.outputStream
  val stdoutStream: InputStream get() = stdoutPipe ?: error("stdout is not available")
  val stderrStream: InputStream get() = stderrPipe ?: error("stderr is not available")
  val stdinWriter: BufferedWriter by lazy { stdinStream.bufferedWriter() }
  val stdoutReader: BufferedReader by lazy { stdoutStream.bufferedReader() }
  val stderrReader: BufferedReader by lazy { stderrStream.bufferedReader() }

  val pid: Long get() = delegate.pid()

  // ProcessBuilder has an exitValue API with confusing semantics - it will throw if you call it on
  // a process that hasn't yet finished. We provide a different API - exitCode - which waits for
  // the process to complete and then returns its exitCode.
  // You can still use isAlive if you want to check if the process is alive
  val exitCode: Int by lazy {
    // This is the one place where we wait for our pumps to finish and log the exit (that's why it's
    // important that this property is lazy)
    // TODO: arguably we should have a separate thread waiting for the process to exit (the current
    //   implementation won't show the process exiting if exitCode is never called)
    while (!delegate.waitFor(10, TimeUnit.SECONDS)) {
      log(WARN) { "(${delegate.pid()}) still waiting for process to exit..." }
    }
    val code = delegate.exitValue()
    log(DEBUG) { "(${delegate.pid()}) process exited, waiting for pumps..." }
    awaitPumps()
    logPriority?.let { log(it) { "(${delegate.pid()}) exited with code $code" } }
    code
  }

  val isAlive: Boolean get() = delegate.isAlive

  fun waitFor(): Int {
    return exitCode
  }

  fun checkExitCode(allowedExitCodes: List<Int>? = listOf(0)): Int {
    // We always wait for the process, even if allowedExitCodes is null
    val exitCode = waitFor()

    // We only check the exitCode if allowedExitCodes is non-null
    allowedExitCodes?.let {
      if (exitCode !in it) {
        throw FailedExecException(
          exitCode,
          command,
          if (stdoutPipe != null) stdoutReader.readText() else null,
          if (stderrPipe != null) stderrReader.readText() else null,
        )
      }
    }

    return exitCode
  }

  fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
    val didComplete = delegate.waitFor(timeout, unit)
    if (didComplete) {
      awaitPumps()
      logPriority?.let { log(it) { "(${delegate.pid()}) exited with code ${delegate.exitValue()}" } }
    }
    return didComplete
  }

  fun stdoutText(chomp: Boolean = true, allowedExitCodes: List<Int>? = listOf(0)): String {
    checkExitCode(allowedExitCodes)
    val text = stdoutReader.readText()
    if (chomp) {
      return text.removeSuffix("\n")
    } else {
      return text
    }
  }

  fun awaitPumps() = pumpFutures.forEach { it.join() }
  fun exitValue(): Int = delegate.exitValue()
  fun destroy() = delegate.destroy()
  fun destroyForcibly(): Process = delegate.destroyForcibly()
}

class TeeProcessBuilder(command: List<String>) {
  private val pb = ProcessBuilder(command)
  var stdoutRedirect: OutputRedirectSpec = OutputRedirectSpec.PIPE
  var stderrRedirect: OutputRedirectSpec = OutputRedirectSpec.PIPE
  var stdinRedirect: InputRedirectSpec = InputRedirectSpec.NULL

  var command: List<String>
    get() = pb.command().orEmpty()
    set(value) { pb.command(value) }

  var commandRepr: String? = null

  var directory: File?
    get() = pb.directory()
    set(value) { pb.directory(value) }

  var environment: Map<String, String> get() = pb.environment()
    set(value) {
      val env = pb.environment()
      env.clear()
      env.putAll(value)
    }

  // Overlay that gets applied on start
  var environmentOverlay: Map<String, String> = emptyMap()

  var logPriority: LogPriority? = null

  constructor(vararg command: String) : this(command.toList())

  fun copy(): TeeProcessBuilder = TeeProcessBuilder(command).also { copy ->
    copy.stdoutRedirect = stdoutRedirect
    copy.stderrRedirect = stderrRedirect
    copy.stdinRedirect = stdinRedirect
    copy.commandRepr = commandRepr
    copy.directory = directory
    copy.environment = environment.toMap()
    copy.environmentOverlay = environmentOverlay.toMap()
    copy.logPriority = logPriority
  }

  fun start(): TeeProcess {
    pb.environment().putAll(environmentOverlay)
    validateRedirects()

    val pumpFutures = mutableListOf<CompletableFuture<Void>>()

    // Configure underlying ProcessBuilder redirects
    configureStdin()
    configureStdout()
    configureStderr()

    val process = pb.start()
    initializePidAwareStreams(process.pid())
    logPriority?.let { level ->
      val repr = commandRepr ?: "$ ${shellEscape(command)}"
      log(level) { "(${process.pid()}) $repr" }
    }

    // Set up stdout pumping
    var stdoutPipe = setupOutputPumping(
      process.inputStream,
      stdoutRedirect,
      pumpFutures,
      "stdout"
    )

    // Set up stderr pumping
    var stderrPipe = setupOutputPumping(
      process.errorStream,
      stderrRedirect,
      pumpFutures,
      "stderr"
    )

    // Set up stdin pumping
    var stdinWrapper = setupInputPumping(
      process.outputStream,
      stdinRedirect,
      pumpFutures
    )

    return TeeProcess(
      process,
      stdoutPipe?.toBlockingInputStream(),
      stderrPipe?.toBlockingInputStream(),
      stdinWrapper,
      command,
      pumpFutures,
      logPriority
    )
  }

  fun stdoutText(
    chomp: Boolean = true,
    allowedExitCodes: List<Int>? = listOf(0)
  ): String {
    stdoutRedirect += OutputRedirectSpec.PIPE
    stderrRedirect += OutputRedirectSpec.PIPE
    val proc = start()
    return proc.stdoutText(chomp, allowedExitCodes)
  }

  private fun validateRedirects() {
    // INHERIT must be the sole target - can't tee from/to inherited streams
    if (stdoutRedirect.targets.any { it is OutputTarget.Inherit } && stdoutRedirect.targets.size > 1) {
      throw IllegalArgumentException("stdout INHERIT cannot be combined with other targets")
    }
    if (stderrRedirect.targets.any { it is OutputTarget.Inherit } && stderrRedirect.targets.size > 1) {
      throw IllegalArgumentException("stderr INHERIT cannot be combined with other targets")
    }
    if (stdinRedirect.source is InputSource.Inherit && stdinRedirect.teeOutputs.isNotEmpty()) {
      throw IllegalArgumentException("stdin INHERIT cannot have tee outputs")
    }
  }

  private fun initializePidAwareStreams(pid: Long) {
    for (target in stdoutRedirect.targets) {
      if (target is OutputTarget.ToStream) {
        (target.stream as? PidAware)?.setPid(pid)
      }
    }
    for (target in stderrRedirect.targets) {
      if (target is OutputTarget.ToStream) {
        (target.stream as? PidAware)?.setPid(pid)
      }
    }
    for (teeOutput in stdinRedirect.teeOutputs) {
      (teeOutput.stream as? PidAware)?.setPid(pid)
    }
  }

  private fun configureStdin() {
    when (val source = stdinRedirect.source) {
      is InputSource.Null -> pb.redirectInput(File("/dev/null"))
      is InputSource.Inherit -> pb.redirectInput(ProcessBuilder.Redirect.INHERIT)
      is InputSource.Pipe -> pb.redirectInput(ProcessBuilder.Redirect.PIPE)
      is InputSource.FromFile -> {
        if (stdinRedirect.teeOutputs.isEmpty()) {
          pb.redirectInput(source.file)
        } else {
          pb.redirectInput(ProcessBuilder.Redirect.PIPE)
        }
      }
      is InputSource.FromStream -> pb.redirectInput(ProcessBuilder.Redirect.PIPE)
    }
  }

  private fun configureStdout() {
    val targets = stdoutRedirect.targets
    when {
      targets.isEmpty() -> pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
      targets.size == 1 && targets[0] is OutputTarget.Inherit -> {
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT)
      }
      targets.size == 1 && targets[0] is OutputTarget.ToFile -> {
        val toFile = targets[0] as OutputTarget.ToFile
        pb.redirectOutput(
          if (toFile.append) ProcessBuilder.Redirect.appendTo(toFile.file)
          else ProcessBuilder.Redirect.to(toFile.file)
        )
      }
      else -> pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
    }
  }

  private fun configureStderr() {
    val targets = stderrRedirect.targets
    when {
      targets.isEmpty() -> pb.redirectError(ProcessBuilder.Redirect.DISCARD)
      targets.size == 1 && targets[0] is OutputTarget.Inherit -> {
        pb.redirectError(ProcessBuilder.Redirect.INHERIT)
      }
      targets.size == 1 && targets[0] is OutputTarget.ToFile -> {
        val toFile = targets[0] as OutputTarget.ToFile
        pb.redirectError(
          if (toFile.append) ProcessBuilder.Redirect.appendTo(toFile.file)
          else ProcessBuilder.Redirect.to(toFile.file)
        )
      }
      else -> pb.redirectError(ProcessBuilder.Redirect.PIPE)
    }
  }

  private fun setupOutputPumping(
    processStream: InputStream,
    spec: OutputRedirectSpec,
    pumpFutures: MutableList<CompletableFuture<Void>>,
    streamType: String
  ): StreamingByteBuffer? {
    val targets = spec.targets
    
    // No pumping needed for these cases (handled by ProcessBuilder directly)
    if (targets.isEmpty()) return null
    if (targets.size == 1) {
      when (targets[0]) {
        is OutputTarget.Inherit -> return null
        is OutputTarget.ToFile -> return null
        else -> {}
      }
    }

    // Build list of output streams to tee to
    val outputStreams = mutableListOf<OutputStream>()
    val streamsToClose = mutableListOf<OutputStream>()
    var buffer: StreamingByteBuffer? = null

    for (target in targets) {
      when (target) {
        is OutputTarget.Pipe -> {
          buffer = StreamingByteBuffer()
          outputStreams += buffer
          streamsToClose += buffer
        }
        is OutputTarget.ToFile -> {
          val fos = if (target.append) FileOutputStream(target.file, true) else FileOutputStream(target.file)
          outputStreams += fos
          streamsToClose += fos
        }
        is OutputTarget.ToStream -> {
          outputStreams += target.stream
          if (target.autoClose) streamsToClose += target.stream
        }
        else -> error("Unexpected target type: $target")
      }
    }

    if (outputStreams.isNotEmpty()) {
      pumpFutures += pumpAsync(processStream, outputStreams, streamsToClose)
    }

    return buffer
  }

  private fun setupInputPumping(
    processStdin: OutputStream,
    spec: InputRedirectSpec,
    pumpFutures: MutableList<CompletableFuture<Void>>
  ): OutputStream? {
    val source = spec.source
    val teeOutputs = spec.teeOutputs

    // Build list of all outputs (process stdin + tee outputs)
    val allOutputs = mutableListOf<OutputStream>(processStdin)
    val streamsToClose = mutableListOf<Closeable>(processStdin)
    
    for (tee in teeOutputs) {
      allOutputs += tee.stream
      if (tee.autoClose) streamsToClose += tee.stream
    }

    return when (source) {
      is InputSource.Null, is InputSource.Inherit -> null
      is InputSource.Pipe -> {
        if (teeOutputs.isEmpty()) {
          null  // Use delegate.outputStream directly
        } else {
          TeeOutputStream(allOutputs, streamsToClose)
        }
      }
      is InputSource.FromFile -> {
        if (teeOutputs.isEmpty()) {
          null  // ProcessBuilder handles file redirect directly
        } else {
          val fis = FileInputStream(source.file)
          pumpFutures += pumpAsync(fis, allOutputs, streamsToClose + fis)
          OutputStream.nullOutputStream()
        }
      }
      is InputSource.FromStream -> {
        pumpFutures += pumpAsync(
          source.stream,
          allOutputs,
          streamsToClose + if (source.autoClose) listOf(source.stream) else emptyList()
        )
        OutputStream.nullOutputStream()
      }
    }
  }

  private fun pumpAsync(
    source: InputStream,
    outputs: List<OutputStream>,
    toClose: List<Closeable>
  ): CompletableFuture<Void> {
    return CompletableFuture.runAsync({
      try {
        val buffer = ByteArray(8192)
        while (true) {
          val n = source.read(buffer)
          if (n == -1) break
          for (out in outputs) {
            out.write(buffer, 0, n)
          }
        }
        for (out in outputs) {
          out.flush()
        }
      } finally {
        for (c in toClose) {
          try {
            c.close()
          } catch (_: Exception) {}
        }
      }
    }, teePumpExecutor)
  }

  fun checkExitCode(allowedExitCodes: List<Int>? = listOf(0)) {
    start().checkExitCode(allowedExitCodes)
  }

  private class TeeOutputStream(
    private val streams: List<OutputStream>,
    private val toClose: List<Closeable>
  ) : OutputStream() {
    override fun write(b: Int) {
      for (s in streams) s.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      for (s in streams) s.write(b, off, len)
    }

    override fun flush() {
      for (s in streams) s.flush()
    }

    override fun close() {
      for (s in toClose) {
        try {
          s.close()
        } catch (_: Exception) {}
      }
    }
  }
}

// Similar to Okio's Buffer - if we took a dep on okio we could just use that
class StreamingByteBuffer : OutputStream() {
  private val lock = Object()
  private var buf = ByteArray(8192)
  private var writePos = 0
  private var closed = false

  override fun write(b: ByteArray, off: Int, len: Int) = synchronized(lock) {
    ensureCapacity(writePos + len)
    System.arraycopy(b, off, buf, writePos, len)
    writePos += len
    lock.notifyAll()
  }

  override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

  private fun ensureCapacity(needed: Int) {
    if (needed > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, needed))
  }

  override fun close() = synchronized(lock) { closed = true; lock.notifyAll() }

  fun toBlockingInputStream(): InputStream = object : InputStream() {
    private var readPos = 0
    @Volatile private var readerClosed = false

    override fun read(b: ByteArray, off: Int, len: Int): Int = synchronized(lock) {
      while (!readerClosed && readPos >= writePos && !closed) lock.wait()
      if (readerClosed || readPos >= writePos) return -1
      val n = minOf(len, writePos - readPos)
      System.arraycopy(buf, readPos, b, off, n)
      readPos += n
      n
    }

    override fun read(): Int {
      val b = ByteArray(1)
      return if (read(b, 0, 1) == -1) -1 else b[0].toInt() and 0xFF
    }

    override fun close() = synchronized(lock) {
      readerClosed = true
      lock.notifyAll()
    }
  }
}
