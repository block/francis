package com.squareup.francis

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.squareup.francis.logging.log
import com.sun.net.httpserver.HttpServer
import logcat.LogPriority.ERROR
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ViewCommand(
  baseOptions: BaseOptions = BaseOptions(),
) : CliktCommand(name = "view") {
  override fun help(context: Context) = """
    Open a trace file in the appropriate viewer.

    For .perfetto-trace files: Opens in ui.perfetto.dev using postMessage.
    For .simpleperf.data files: Converts to gecko format and opens in profiler.firefox.com.
  """.trimIndent()

  private val baseOpts by baseOptions

  private val traceFile: File by argument(help = "Path to the trace file (.perfetto-trace or .simpleperf.data)")
    .file(mustExist = true, canBeDir = false, mustBeReadable = true)

  private val port: Int by option("-p", "--port", help = "Port for the local HTTP server")
    .int()
    .default(9001)

  override fun run() {
    baseOpts.setup()
    openTrace(traceFile, port)
  }

  companion object {
    fun openTrace(traceFile: File, port: Int = 9001) {
      when {
        traceFile.name.endsWith(".perfetto-trace") -> openTraceInPerfetto(traceFile, port)
        traceFile.name.endsWith(".simpleperf.data") -> openTraceInFirefoxProfiler(traceFile, port)
        else -> {
          log(ERROR) { "Unknown file type: ${traceFile.name}. Expected .perfetto-trace or .simpleperf.data" }
        }
      }
    }

    fun openTraceInPerfetto(traceFile: File, port: Int = 9001) {
      val traceBytes = traceFile.readBytes()
      val fileName = traceFile.name

      val html = generatePerfettoHtml(fileName)
      val shutdownLatch = CountDownLatch(1)

      val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
      val actualPort = server.address.port

      server.createContext("/") { exchange ->
        log { "${exchange.requestMethod} ${exchange.requestURI}" }
        val response = html.toByteArray()
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, response.size.toLong())
        exchange.responseBody.use { it.write(response) }
      }

      server.createContext("/trace") { exchange ->
        log { "${exchange.requestMethod} ${exchange.requestURI}" }
        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
        exchange.sendResponseHeaders(200, traceBytes.size.toLong())
        exchange.responseBody.use { it.write(traceBytes) }
      }

      server.createContext("/shutdown") { exchange ->
        log { "${exchange.requestMethod} ${exchange.requestURI}" }
        exchange.sendResponseHeaders(204, -1)
        exchange.close()
        shutdownLatch.countDown()
      }

      server.executor = null
      server.start()

      val localUrl = "http://127.0.0.1:$actualPort/"

      log { "Serving loader page at $localUrl" }
      log { "Click the button in the browser to open the trace in Perfetto UI." }

      openInBrowser(localUrl)

      shutdownLatch.await()
      log { "Trace sent. Shutting down server..." }
      server.stop(0)
    }

    fun openTraceInFirefoxProfiler(simpleperfFile: File, port: Int = 9001) {
      val geckoProfile = convertToGeckoProfile(simpleperfFile)
      if (geckoProfile == null) {
        log(ERROR) { "Failed to convert simpleperf data to gecko profile" }
        return
      }

      val profileBytes = geckoProfile.readBytes()
      val fileName = geckoProfile.name

      val fetchedLatch = CountDownLatch(1)

      val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
      val actualPort = server.address.port

      server.createContext("/$fileName") { exchange ->
        log { "${exchange.requestMethod} ${exchange.requestURI}" }

        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.add("Cache-Control", "no-cache")
        exchange.responseHeaders.add("Content-Type", "application/json")

        exchange.sendResponseHeaders(200, profileBytes.size.toLong())
        exchange.responseBody.use { it.write(profileBytes) }
        fetchedLatch.countDown()
      }

      server.executor = null
      server.start()

      val profileUrl = "http://127.0.0.1:$actualPort/$fileName"
      val encodedUrl = URLEncoder.encode(profileUrl, "UTF-8")
      val firefoxProfilerUrl = "https://profiler.firefox.com/from-url/$encodedUrl"

      log { "Serving profile at $profileUrl" }
      log { "Opening Firefox Profiler..." }

      openInBrowser(firefoxProfilerUrl)

      val fetched = fetchedLatch.await(2, TimeUnit.MINUTES)
      if (fetched) {
        log { "Profile fetched. Shutting down server..." }
      } else {
        log { "Timeout waiting for profile fetch. Shutting down server..." }
      }
      server.stop(0)
    }

    private fun convertToGeckoProfile(simpleperfFile: File): File? {
      val simpleperfDir = findSimpleperfDir()
      if (simpleperfDir == null) {
        log(ERROR) { "simpleperf tools not found in Android NDK. Install NDK 28+ to enable Firefox Profiler viewing." }
        return null
      }

      val geckoProfileGenerator = File(simpleperfDir, "gecko_profile_generator.py").absolutePath
      val binaryCacheBuilder = File(simpleperfDir, "binary_cache_builder.py").absolutePath

      val binaryCacheDir = File(simpleperfFile.parentFile, "binary_cache")
      if (!binaryCacheDir.exists()) {
        log { "Building binary cache for symbols..." }
        try {
          val cacheProcess = ProcessBuilder(
            binaryCacheBuilder, "-i", simpleperfFile.absolutePath
          )
            .directory(simpleperfFile.parentFile)
            .redirectErrorStream(true)
            .start()

          cacheProcess.inputStream.bufferedReader().forEachLine { line ->
            log { line }
          }

          val cacheExitCode = cacheProcess.waitFor()
          if (cacheExitCode != 0) {
            log(ERROR) { "binary_cache_builder.py failed with exit code $cacheExitCode" }
          } else {
            deduplicateBinaryCache(binaryCacheDir)
          }
        } catch (e: Exception) {
          log(ERROR) { "Failed to build binary cache: ${e.message}" }
        }
      }

      val outputFile = File(simpleperfFile.parent, simpleperfFile.nameWithoutExtension + ".gecko-profile.json")
      log { "Converting simpleperf data to gecko profile format..." }

      try {
        val command = mutableListOf(geckoProfileGenerator, "-i", simpleperfFile.absolutePath)
        if (binaryCacheDir.isDirectory) {
          command.addAll(listOf("--symfs", binaryCacheDir.absolutePath))
        }

        val process = ProcessBuilder(command)
          .directory(simpleperfFile.parentFile)
          .redirectError(ProcessBuilder.Redirect.INHERIT)
          .start()

        outputFile.outputStream().use { out ->
          process.inputStream.copyTo(out)
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
          log(ERROR) { "gecko_profile_generator.py failed with exit code $exitCode" }
          outputFile.delete()
          return null
        }

        log { "Gecko profile written to: ${outputFile.absolutePath}" }
        return outputFile
      } catch (e: Exception) {
        log(ERROR) { "Failed to convert to gecko profile: ${e.message}" }
        return null
      }
    }

    private fun deduplicateBinaryCache(binaryCacheDir: File) {
      val globalCache = File(Xdg.francisCache, "symbols")
      globalCache.mkdirs()

      var deduped = 0
      var dedupedBytes = 0L
      var total = 0

      binaryCacheDir.walkTopDown()
        .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
        .forEach { file ->
          total++
          try {
            val hash = file.inputStream().use { input ->
              val digest = MessageDigest.getInstance("SHA-256")
              val buffer = ByteArray(8192)
              var read: Int
              while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
              }
              digest.digest().joinToString("") { "%02x".format(it) }
            }

            val cached = File(globalCache, hash)
            if (!cached.exists()) {
              file.copyTo(cached)
            } else {
              deduped++
              dedupedBytes += file.length()
            }
            file.delete()
            Files.createSymbolicLink(file.toPath(), cached.toPath())
          } catch (e: Exception) {
            log(ERROR) { "Failed to deduplicate ${file.name}: ${e.message}" }
          }
        }

      if (deduped > 0) {
        val savedMb = dedupedBytes / (1024 * 1024)
        log { "Deduplicated $deduped of $total binaries, saved ${savedMb}MB using global symbol cache" }
      }
    }

    private fun findSimpleperfDir(): File? {
      val androidHome = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT") ?: return null
      val ndkDir = File(androidHome, "ndk")
      if (!ndkDir.isDirectory) return null

      return ndkDir.listFiles()
        ?.filter { it.isDirectory }
        ?.sortedDescending()
        ?.map { File(it, "simpleperf") }
        ?.firstOrNull { File(it, "gecko_profile_generator.py").isFile }
    }

    private fun openInBrowser(url: String) {
      val os = System.getProperty("os.name").lowercase()
      val command = when {
        os.contains("mac") -> arrayOf("open", url)
        os.contains("linux") -> arrayOf("xdg-open", url)
        else -> {
          log { "Open manually: $url" }
          return
        }
      }
      ProcessBuilder(*command).start()
    }

    private fun generatePerfettoHtml(fileName: String): String {
    return """
<!DOCTYPE html>
<html>
<head>
  <title>Open in Perfetto: $fileName</title>
  <style>
    body { 
      font-family: system-ui, sans-serif; 
      padding: 40px; 
      background: #1a1a2e; 
      color: #eee;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 80vh;
    }
    h1 { margin-bottom: 10px; }
    .filename { color: #888; margin-bottom: 30px; font-family: monospace; }
    button {
      background: #4a90d9;
      color: white;
      border: none;
      padding: 16px 32px;
      font-size: 18px;
      border-radius: 8px;
      cursor: pointer;
    }
    button:hover { background: #3a7bc8; }
    button:disabled { background: #555; cursor: default; }
    .status { margin-top: 20px; font-size: 16px; }
    .error { color: #ff6b6b; }
  </style>
</head>
<body>
  <h1>Perfetto Trace Viewer</h1>
  <div class="filename">$fileName</div>
  <button id="openBtn" disabled>Loading trace...</button>
  <div class="status" id="status"></div>
  <script>
    const FILE_NAME = "$fileName";
    let traceBuffer = null;

    function updateStatus(msg, isError) {
      const el = document.getElementById('status');
      el.textContent = msg;
      if (isError) el.classList.add('error');
    }

    async function loadTrace() {
      try {
        const response = await fetch('/trace');
        traceBuffer = await response.arrayBuffer();

        // Shutdown the server so `francis view` exits
        fetch('/shutdown');

        openTrace();
      } catch (e) {
        updateStatus('Failed to load trace: ' + e.message, true);
      }
    }

    function openTrace() {
      if (!traceBuffer) return;
      
      document.getElementById('openBtn').disabled = true;
      document.getElementById('openBtn').textContent = 'Opening...';
      
      const perfettoWindow = window.open('https://ui.perfetto.dev');
      if (!perfettoWindow) {
        updateStatus('Click the button to open Perfetto UI. (Tip: allow popups to skip this step)');
        document.getElementById('openBtn').disabled = false;
        document.getElementById('openBtn').textContent = 'Open in Perfetto UI';
        return;
      }

      updateStatus('Waiting for Perfetto UI...');

      const sendPing = () => {
        perfettoWindow.postMessage('PING', 'https://ui.perfetto.dev');
      };

      const pingInterval = setInterval(sendPing, 100);

      window.addEventListener('message', function handler(event) {
        if (event.origin !== 'https://ui.perfetto.dev') return;
        if (event.data !== 'PONG') return;

        clearInterval(pingInterval);
        window.removeEventListener('message', handler);

        perfettoWindow.postMessage({
          perfetto: {
            buffer: traceBuffer,
            title: FILE_NAME,
            fileName: FILE_NAME,
          }
        }, 'https://ui.perfetto.dev', [traceBuffer]);

        window.close();
      });

      sendPing();
    }

    document.getElementById('openBtn').onclick = openTrace;
    loadTrace();
  </script>
</body>
</html>
    """.trimIndent()
    }
  }
}
